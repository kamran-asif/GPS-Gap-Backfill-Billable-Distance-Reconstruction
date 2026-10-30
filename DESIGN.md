# System Architecture & Technical Design Document
## MoveInSync Billing-Grade GPS Trip Distance Rebuilding Engine

---

## 1. Pre-Implementation Architectural Decisions

As mandated by design governance, the core data model, components, APIs, and failure modes were finalized **prior to coding**:

| Dimension | Architectural Choice | System Component / Implementation |
| :--- | :--- | :--- |
| **1. Data Model** | Immutable value objects & versioned append-only audit ledger with signed adjustments | `Coordinate`, `GpsPing`, `AnchorPoint`, `SegmentResult`, `TripEvidence`, `TripEntity`, `TripSegmentEntity`, `TripAdjustmentEntity` |
| **2. Main Components** | Pure-Java 4-stage pipeline decoupled from Spring/JPA framework | `StreamCleaner` (Stage I), `AnchorPatchEngine` (Stage II), `SegmentCalculatorEngine` (Stage III), `RoutingCache` (~11m Caffeine grid), `RoutingCircuitBreaker`, `RoutingRateLimiter` |
| **3. APIs** | REST API with multi-tenant RBAC and idempotency protection | `POST /calculate` (v1 distance), `POST /recompute` (signed delta), `POST /batch-calculate` (parallel), `GET /{id}` (audit trail), `GET /adjustments` (credit/debit ledger), `POST /admin/reconcile-degraded` |
| **4. Failure Modes** | Fault-tolerant graceful degradation and deterministic recovery | Teleport speed filter (`>120 km/h` vs `lastAccepted`), Accuracy drop (`>50m`), Underpass milestone anchoring, Maps outage `1.4x` fallback (`DEGRADED`), Post-billing signed credit/debit notes |

---

## 2. End-to-End System Architecture

```mermaid
flowchart TD
    Client["Cab App / Billing Client"] -->|HTTPS + Tenant & RBAC| Gateway["Spring Security + TenantFilter"]
    Gateway --> TripCtrl["TripController & AdminController"]
    TripCtrl --> TripSvc["TripDistanceService"]

    subgraph PureJavaCore ["Pure Java Core Engine (Zero Framework Dependencies)"]
        direction TB
        Stage1["Stage I: StreamCleaner<br/>Deduplicate, TimSort, Acc <= 50m, Speed <= 120 km/h"]
        Stage2["Stage II: AnchorPatchEngine<br/>Milestone Anchors, No-Show, Office 400m"]
        Stage3["Stage III: SegmentCalculatorEngine<br/>Short Gap < 70s, Parked < 5 km/h, Routed"]
        Stage4["Stage IV: RecomputeDelta<br/>Append-Only Versioning & Signed Credit/Debit Note"]
        Stage1 --> Stage2 --> Stage3 --> Stage4
    end

    subgraph RoutingResilience ["Routing & Resilience Layer"]
        RoutingSvc["RoutingService"]
        GridCache["RoutingCache (Caffeine LRU, ~11m grid)"]
        RateLimiter["RoutingRateLimiter (Token Bucket)"]
        CircuitBreaker["RoutingCircuitBreaker (10-sample ring, 50% threshold)"]
        Provider["RoutingProvider (Simulated / Maps API)"]
        RoutingSvc --> GridCache --> RateLimiter --> CircuitBreaker --> Provider
    end

    subgraph Persistence ["Persistence & Audit"]
        TripRepo[("PostgreSQL / H2<br/>trips & trip_segments")]
        AdjRepo[("PostgreSQL / H2<br/>trip_adjustments (Signed Ledger)")]
    end

    TripSvc --> PureJavaCore
    Stage3 --> RoutingSvc
    TripSvc --> TripRepo
    TripSvc --> AdjRepo
```

---

## 3. The 4-Stage Distance Rebuilding Pipeline

```
Raw Pings ──> [ Stage I: StreamCleaner ] ──> [ Stage II: AnchorPatch ] ──> [ Stage III: SegmentCalc ] ──> [ Stage IV: RecomputeDelta ]
                 - Dedupe by timestamp         - Injected pickups/drops      - Short gap < 70s: Aerial        - Immutable append-only
                 - Chronological TimSort       - Fallback: Home vs Planned   - Parked < 5 km/h: Aerial        - Signed Delta:
                 - Drop accuracy > 50m         - Office 400m Rule            - Real gap: Snapped Grid Cache     +X.XX km CREDIT_NOTE
                 - Drop speed > 120 km/h       - No-Show anchor flag         - Outage: 1.4x Fallback (DEGRADED) -X.XX km DEBIT_NOTE
```

1. **Stage I: Stream Cleaning (`StreamCleaner`)**: Drops malformed fixes; deduplicates coincident timestamps picking the lowest accuracy radius; sorts chronologically; drops accuracy $> 50$m; rejects teleport jumps ($> 120$ km/h) evaluated strictly against `lastAccepted` valid fix.
2. **Stage II: Gap Patching (`AnchorPatchEngine`)**: Injects business milestones into GPS blackout zones (underpasses/basements). Fallback precedence: `AD_HOC` $\to$ Profile Home + Sign-in Time; `PLANNED` $\to$ Planned Stop. Enforces **Office 400m Rule**: actual end point used only if $\le 400$m from office, else planned office location is anchored.
3. **Stage III: Segment Distance (`SegmentCalculatorEngine`)**: Evaluates cheap rules first (`SHORT_GAP_AERIAL` for $\Delta t < 70$s, `PARKED` for $\Delta t \ge 70$s at $< 5$ km/h). Real gaps query `RoutingService`. If provider is down/circuit open, applies $1.4\times\text{Haversine}$ and marks trip `DEGRADED`.
4. **Stage IV: Deterministic Rounding & Signed Deltas (`RecomputeDelta`)**: Segments summed in `BigDecimal` (`HALF_UP`, 3 decimals); total scaled to 2 decimals (`0.01 km`). Recomputation generates signed delta adjustments (`CREDIT_NOTE` / `DEBIT_NOTE`).

---

## 4. Sequence Diagram: Outage Fallback & Post-Billing Recompute

```mermaid
sequenceDiagram
    autonumber
    actor Vendor as Cab / Billing Client
    participant API as TripController
    participant Svc as TripDistanceService
    participant Engine as RebuildingEngine
    participant Route as RoutingService
    participant DB as PostgreSQL / H2

    Note over Vendor,DB: Phase 1: Ingestion During Maps Provider Outage
    Vendor->>API: POST /api/v1/trips/calculate (Pings, Milestones, Office)
    API->>Svc: calculateAndSave(evidence)
    Svc->>Engine: clean + patch + calculate
    Engine->>Route: getDrivingRoute(start, end)
    Route-->>Engine: Circuit Breaker OPEN -> Fallback 1.4x Aerial
    Engine-->>Svc: TripDistanceResult v1 = 28.80 km (DEGRADED)
    Svc->>DB: INSERT trips v1 and trip_segments
    Svc-->>API: 200 OK (v1 Invoice Billed)

    Note over Vendor,DB: Phase 2: Provider Recovers & Late Pings Arrive
    Vendor->>API: POST /api/v1/trips/{tripId}/recompute (Late Pings)
    API->>Svc: recomputeTrip(tripId, updatedEvidence, reason)
    Svc->>DB: SELECT latest trip version v1
    Svc->>Engine: Recompute with merged pings (v2)
    Engine->>Route: getDrivingRoute(start, end)
    Route-->>Engine: Provider Recovered -> Live Road Distance
    Engine-->>Svc: TripDistanceResult v2 = 30.60 km (COMPLETED)
    Svc->>Svc: Delta: 30.60 - 28.80 = +1.80 km (CREDIT_NOTE)
    Svc->>DB: INSERT trips v2 (v1 untouched)
    Svc->>DB: INSERT trip_adjustments (+1.80 km CREDIT_NOTE)
    Svc-->>API: 200 OK (Signed Delta +1.80 km)
```

---

## 5. Database Schema (Multi-Tenant Audit Ledger)

```mermaid
erDiagram
    TRIPS ||--o{ TRIP_SEGMENTS : "1:N (cascade)"
    TRIPS ||--o{ TRIP_ADJUSTMENTS : "v_prev -> v_new"

    TRIPS {
        bigint id PK
        varchar trip_id "Index (trip_id, tenant_id)"
        varchar tenant_id "Multi-tenant partition key"
        int version "Unique per (trip_id, tenant_id, version)"
        double total_distance_km "2 decimal billing scale"
        varchar status "COMPLETED | DEGRADED | RECOMPUTED"
        varchar rule_version "e.g. v1.2.0"
        varchar idempotency_key "Unique invoice key"
        text evidence_snapshot "Base64 raw input snapshot"
        timestamp calculated_at
    }

    TRIP_SEGMENTS {
        bigint id PK
        bigint trip_record_id FK
        int segment_index "1-based order"
        double start_lat
        double start_lon
        double end_lat
        double end_lon
        double final_distance_km
        varchar rule_applied "ROUTED | SHORT_GAP_AERIAL | ..."
        varchar explanation "Dispute explanation string"
        boolean routed
    }

    TRIP_ADJUSTMENTS {
        bigint id PK
        varchar trip_id "Index (trip_id, tenant_id)"
        varchar tenant_id
        int previous_version
        int new_version
        double previous_distance_km
        double new_distance_km
        double delta_km "Signed delta"
        varchar formatted_delta "+1.80 km | -8.47 km"
        varchar adjustment_type "CREDIT_NOTE | DEBIT_NOTE | NO_CHANGE"
        varchar reason
        timestamp created_at
    }
```

---

## 6. Failure-Mode Analysis Matrix

| Failure Scenario | Root Cause | System Mitigation | Business / Billing Outcome |
| :--- | :--- | :--- | :--- |
| **GPS Teleport Jump** | Urban canyon multipath ($> 8,000$ km/h burst) | Speed evaluated against `lastAccepted` valid fix; candidate dropped. | Vendor not over-paid; noisy burst rejected cleanly. |
| **GPS Dilution Noise** | Satellite scatter in covered driveway ($> 50$m radius) | Dropped in Stage I accuracy filter (`accuracyMeters > 50m`). | Prevents phantom zig-zag mileage inflation. |
| **22-min Underpass Gap** | Satellite blackout during passenger drops | Stage II injects milestone anchors; Stage III routes gap. | Vendor paid true road distance ($30.6$ km vs naive $18.4$ km). |
| **Employee No-Show** | Cab arrived at pickup; rider cancelled/absent | Feature flag `includeNoShowAnchors` anchors planned stop. | Vendor fairly paid for deadhead pickup detour. |
| **Late Trip-End (> 400m)** | Driver ends trip 3 km away at gas station | Office 400m rule snaps end point to planned office location. | Prevents over-billing for post-trip personal driving. |
| **Maps Provider Outage** | 3 AM provider $503$ or rate limit exhausted | Circuit breaker opens; applies $1.4\times$ Haversine; marks `DEGRADED`. | Zero fleet downtime; auto-healed via `/reconcile-degraded`. |
| **Post-Billing Late Pings** | Phone syncs delayed pings hours after invoice closed | Append-only recompute produces signed `CREDIT_NOTE` / `DEBIT_NOTE`. | Original invoice never mutated; full accounting audit trail. |
| **Cross-Tenant Leakage** | Buggy or malicious client query | `TenantFilter` + `TenantContext` binds all queries to tenant ID. | Hard data barrier; returns 404 across tenant boundaries. |

---

## 7. Performance & Empirical Benchmark

Validated on AMD Ryzen 5 5500H (Windows 11, OpenJDK 64-Bit, 15M pings):

- **Workload**: 10,000 trips $\times$ 1,500 pings = **15,000,000 pings**
- **Throughput**: **508.83 trips/sec** (**763,242 pings/sec**)
- **Latencies**: $p50 = 1.78\text{ ms}$, $p95 = 2.84\text{ ms}$, $p99 = 4.41\text{ ms}$
- **Net Heap Overhead**: **2.27 MB** (bounded linear memory $\mathcal{O}(N)$)
- **Time Complexity**: $\mathcal{O}(N \log N)$ comparison-sorted stream lower bound; spatial routing cache lookup is $\mathcal{O}(1)$.

---

## 8. Scalability & Production Hardening

1. **Narrow Transactions (`TransactionTemplate`)**: Heavy CPU math and routing I/O execute outside database transactions; HikariCP connection held only for $\sim 2$ ms during entity write.
2. **Raw Evidence Snapshots**: Stored as compressed Base64 snapshots on `trips`; incremental `/recompute` needs only new late pings.
3. **Idempotency Protection**: `X-Idempotency-Key` header prevents duplicate billing on network retries.
4. **Outbound Protection Shield**: 4-decimal spatial grid cache ($\sim 11.1$m resolution, 99% hit rate) + token-bucket rate limiter + sliding ring-buffer circuit breaker protect third-party routing quotas.
5. **Horizontal Growth**: Stateless core engine allows multi-node deployment behind a load balancer; Kafka keying by `tripId` guarantees ordered single-partition processing per trip.
