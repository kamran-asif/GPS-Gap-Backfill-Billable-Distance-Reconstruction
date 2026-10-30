# System Architecture & Technical Design Document
## MoveInSync Billing-Grade GPS Trip Distance Rebuilding Engine

---


## Table of Contents
1. [Pre-Implementation Architectural Decisions (Data Model, Components, APIs, Failure Modes)](#1-pre-implementation-architectural-decisions)
2. [Executive Summary & Core Design Principles](#2-executive-summary--core-design-principles)
3. [End-to-End System Architecture & Hexagonal Boundary](#3-end-to-end-system-architecture--hexagonal-boundary)
4. [Deep Dive: The 4-Stage Distance Rebuilding Algorithm](#4-deep-dive-the-4-stage-distance-rebuilding-algorithm)
5. [Resilience, Spatial Caching & Rate-Limiting Architecture](#5-resilience-spatial-caching--rate-limiting-architecture)
6. [Sequence Diagram: The Hardest Production Flow](#6-sequence-diagram-the-hardest-production-flow)
7. [Database Schema & Multi-Tenant Entity-Relationship Design](#7-database-schema--multi-tenant-entity-relationship-design)
8. [Failure-Mode, Mitigation & Recovery Matrix](#8-failure-mode-mitigation--recovery-matrix)
9. [Algorithmic Complexity & 15-Million-Ping Benchmark Analysis](#9-algorithmic-complexity--15-million-ping-benchmark-analysis)
10. [Deliberate Engineering Trade-offs](#10-deliberate-engineering-trade-offs)
11. [Security, Multi-Tenancy & Zero-PII Architecture](#11-security-multi-tenancy--zero-pii-architecture)
12. [Observability, Prometheus Metrics & Distributed Tracing](#12-observability-prometheus-metrics--distributed-tracing)
13. [8-Layer Scalability & Growth Path (15M to 150M+ Pings)](#13-8-layer-scalability--growth-path-15m-to-150m-pings)

---

## 1. Pre-Implementation Architectural Decisions

As mandated by enterprise design governance, all core data models, components, API contracts, and failure modes were formalized **prior to coding**:

```
+-------------------------------------------------------------------------------------------------------------------+
| PRE-IMPLEMENTATION DESIGN DECISION MATRIX                                                                         |
+---------------------+-------------------------------------------------------+-------------------------------------+
| Dimension           | Architectural Formulation                             | Concrete System Specification       |
+---------------------+-------------------------------------------------------+-------------------------------------+
| 1. DATA MODEL       | - Immutable Domain Value Objects                      | `Coordinate`, `GpsPing`,            |
|                     | - Versioned Entity Append-Only Ledger                 | `AnchorPoint`, `SegmentResult`,     |
|                     | - Signed Delta Financial Adjustments                  | `TripEvidence`, `TripEntity`,       |
|                     | - Encapsulated Raw Evidence Blob Snapshot             | `TripSegmentEntity`,                |
|                     |                                                       | `TripAdjustmentEntity`              |
+---------------------+-------------------------------------------------------+-------------------------------------+
| 2. MAIN COMPONENTS  | - Stage I: Stream Cleaning (Dedupe, Order, Noise)     | `StreamCleaner`                     |
|                     | - Stage II: Gap Patching (Business Milestones)        | `AnchorPatchEngine`                 |
|                     | - Stage III: Segment Evaluation & Cheap Fallback      | `SegmentCalculatorEngine`           |
|                     | - Routing Layer: Snapped Grid Cache (~11m Caffeine)   | `RoutingCache`                      |
|                     | - Resilience: 10-Sample Ring Buffer Circuit Breaker   | `RoutingCircuitBreaker`             |
|                     | - Scaling: Token-Bucket Rate Limiter & Batch Chunks   | `RoutingRateLimiter`,               |
|                     |                                                       | `ParallelTripProcessor`             |
+---------------------+-------------------------------------------------------+-------------------------------------+
| 3. APIS             | - Ingest & Calculate Initial Distance (v1)            | `POST /api/v1/trips/calculate`      |
|                     | - Parallel Multi-Worker Batch Calculation             | `POST /api/v1/trips/batch-calculate`|
|                     | - Idempotent Recompute with Signed Delta (Credit/Debit| `POST /api/v1/trips/{id}/recompute` |
|                     | - Fetch Explainable Trip Audit Trail by Version       | `GET  /api/v1/trips/{id}?version=V` |
|                     | - Fetch Signed Billing Adjustments Ledger             | `GET  /api/v1/trips/{id}/adjustments|
|                     | - Auto-Heal Degraded Outage Trips                     | `POST /api/v1/admin/reconcile-degrad|
|                     | - Inspect & Trip/Reset Circuit Breaker                | `GET/POST /api/v1/admin/circuit-brea|
+---------------------+-------------------------------------------------------+-------------------------------------+
| 4. FAILURE MODES    | - Teleport Jumps (>120 km/h burst rejection)          | Evaluated against `lastAccepted`    |
|                     | - Low GPS Accuracy (>50m dilution dropped)            | Dropped during Stream Cleaning      |
|                     | - 22-min Underpass/Tunnel Blackout                    | Anchored via Pickups/Drops/Office   |
|                     | - 3 AM Maps Provider Outage (503 / Timeout)           | 1.4x Haversine Fallback (DEGRADED)  |
|                     | - Driver Late Pings Uploaded After Invoice Closed     | Signed Delta (+X.XX km CREDIT_NOTE) |
|                     | - Cross-Tenant Data Leakage                           | TenantFilter + TenantContext barrier|
+---------------------+-------------------------------------------------------+-------------------------------------+
```

---

## 2. Executive Summary & Core Design Principles

Vendors providing enterprise commuter cabs to MoveInSync are compensated strictly per kilometer (`$/km`). Raw trip distances originate from hardware GPS trackers and driver smartphones operating in hostile urban environments (underpasses, high-rise urban canyons, underground basement parking, battery savers, and delayed batch uploads).

- **Naive straight-line summation under-pays vendors** when GPS drops out for 20+ minutes during employee pickups or drops inside tunnels and basements.
- **Naive routing API calls for every GPS pair over-pay vendors** on multipath teleport bursts (`8,000 km/h`) and bankrupt third-party Maps API budgets.
- **Silent invoice mutations** when late pings arrive hours after billing cause accounting audits to fail and erode vendor trust.

### Architectural Pillars
1. **100% Explainable Kilometers**: Every single segment `(start -> end)` carries its exact `DistanceRule` enum, aerial vs. final kilometer figures, implied speed, and a human-readable audit string suitable for resolving vendor billing disputes.
2. **Deterministic & Versioned Rule Execution (`v1.2.0`)**: Given the same `TripEvidence` and `EngineConfig`, two runs on any thread or node are mathematically guaranteed to agree to `0.00 km`.
3. **Immutable Append-Only Billing Ledger**: Closed invoices are never mutated in place. Post-billing late pings or degraded-route recoveries generate a new trip version (`v1 -> v2`) and a signed `RecomputeDelta` (`CREDIT_NOTE`, `DEBIT_NOTE`, or `NO_CHANGE`).
4. **Hexagonal (Ports & Adapters) Pure-Java Core**: The entire `domain`, `engine`, `routing`, and `scaling` packages have **zero Spring or JPA dependencies**, enabling sub-millisecond unit testing, zero GC bloat, and effortless portability across REST, batch CLI, or Kafka workers.

---

## 2. End-to-End System Architecture & Hexagonal Boundary

```mermaid
flowchart TD
    Client["Vendor Mobile App / Billing Portal / Admin Console"] -->|HTTPS + X-Tenant-ID + X-User-Role| Gateway["Spring Security + TenantFilter + MDC Trace"]
    Gateway --> TripCtrl["TripController & AdminController"]
    TripCtrl --> TripSvc["TripDistanceService & TripBatchService"]

    subgraph PureJavaCore ["Pure Java Core Engine (Zero Framework Dependencies)"]
        direction TB
        Orchestrator["TripDistanceRebuildingEngine<br/>(Deterministic v1.2.0 Orchestrator)"]
        Stage1["Stage I: StreamCleaner<br/>Hash Dedupe, TimSort, Acc <= 50m, Speed <= 120 km/h"]
        Stage2["Stage II: AnchorPatchEngine<br/>Employee Actual/Home/Planned Fallbacks, No-Show, Office 400m"]
        Stage3["Stage III: SegmentCalculatorEngine<br/>Short Gap < 70s, Parked < 5 km/h, Routed, Anomalous Rejections"]
        Stage4["Stage IV: RecomputeDelta<br/>Append-Only Versioning & Signed Credit/Debit Note"]

        Orchestrator --> Stage1 --> Stage2 --> Stage3 --> Stage4
    end

    subgraph RoutingResilience ["Routing & Resilience Layer"]
        direction TB
        RoutingSvc["RoutingService"]
        GridCache["RoutingCache (Caffeine LRU)<br/>4-Decimal Spatial Grid (~11.1m), 50k Max, 24h TTL"]
        RateLimiter["RoutingRateLimiter<br/>Non-Blocking Token Bucket"]
        CircuitBreaker["RoutingCircuitBreaker<br/>Ring Buffer (10 calls, 50% threshold, 3s reset)"]
        Provider["RoutingProvider Interface<br/>(SimulatedRoutingProvider / Maps API)"]

        RoutingSvc --> GridCache
        GridCache -->|Cache Miss| RateLimiter
        RateLimiter -->|Permit Acquired| CircuitBreaker
        CircuitBreaker -->|CLOSED or HALF_OPEN| Provider
    end

    subgraph PersistenceAndAudit ["Persistence & Observability Layer"]
        direction TB
        TripRepo[("PostgreSQL / H2<br/>trips & trip_segments")]
        AdjRepo[("PostgreSQL / H2<br/>trip_adjustments (Signed Ledger)")]
        Prom[("Micrometer / Prometheus<br/>/actuator/prometheus")]
    end

    TripSvc --> Orchestrator
    Stage3 --> RoutingSvc
    TripSvc --> TripRepo
    TripSvc --> AdjRepo
    TripSvc --> Prom
```

---

## 3. Deep Dive: The 4-Stage Distance Rebuilding Algorithm

### 3.1 Geodesic Foundation (`Coordinate.java`)
All aerial distances are computed on the WGS84 spherical earth model ($R = 6371.0088\text{ km}$) using the numerically stable Haversine formulation:

$$a = \sin^2\left(\frac{\Delta \phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta \lambda}{2}\right)$$

$$d_{\text{km}} = 2 R \cdot \text{atan2}\left(\sqrt{a}, \sqrt{1 - a}\right)$$

For spatial cache indexing, coordinates are snapped to a configurable decimal grid ($p = 4$ decimal places, $0.0001^\circ \approx 11.1\text{ meters}$ at the equator) using `BigDecimal` `HALF_UP` rounding:

$$\text{Cell}(\phi, \lambda) = \text{round}_4(\phi) \mathbin{\Vert} \text{","} \mathbin{\Vert} \text{round}_4(\lambda)$$

---

### 3.2 Stage I: Stream Cleaning (`StreamCleaner.java`)

```mermaid
flowchart LR
    Raw["Raw GPS Stream<br/>(Unsorted, Duplicates, Noise)"] --> NullCheck["1. Null & Coordinate<br/>Bounds Filter"]
    NullCheck --> Dedupe["2. Timestamp Deduplication<br/>Keep min(accuracyMeters)"]
    Dedupe --> Sort["3. Chronological TimSort<br/>Strictly Ascending Instant"]
    Sort --> Acc["4. Accuracy Filter<br/>Drop accuracy > 50.0m"]
    Acc --> Teleport["5. Sequential Teleport Filter<br/>Drop v > 120.0 km/h vs lastAccepted"]
    Teleport --> Clean["Cleaned GPS Stream +<br/>Audit Drop Counters"]
```

- **Crucial Invariant in Step 5**: Implied speed is always evaluated against `lastAccepted` (the most recent *valid* ping), **never** against a rejected teleport ping. If a device emits a single bad ping 9 km away (`8,000 km/h`) and then resumes normal pings 30 seconds later on the real road, comparing against `lastAccepted` rejects only the outlier burst and cleanly reconnects the valid road trajectory.

---

### 3.3 Stage II: Gap Patching with Business Anchors (`AnchorPatchEngine.java`)

When a cab enters an underground basement or underpass, GPS fixes vanish for 15–25 minutes while employee pickups or drops still occur. `AnchorPatchEngine` weaves business milestones into the cleaned GPS stream:

```mermaid
flowchart TD
    Milestone["TripMilestone Event"] --> TypeCheck{"Milestone Type?"}
    TypeCheck -->|TRIP_START| StartAnchor["Add TRIP_START Anchor<br/>(if valid coordinate)"]
    TypeCheck -->|NO_SHOW| FlagCheck{"includeNoShowAnchors<br/>enabled?"}
    FlagCheck -->|Yes| NoShowResolve["Resolve: Actual -> Planned Stop -> Profile Home"]
    FlagCheck -->|No| Skip["Ignore No-Show"]
    TypeCheck -->|PICKUP / DROP<br/>Status == COMPLETED| HasActual{"Has Valid Actual<br/>Coordinate?"}
    HasActual -->|Yes| ActualAnchor["Use Actual Coordinate<br/>(EMPLOYEE_ACTUAL)"]
    HasActual -->|No| RiderCheck{"RiderType?"}
    RiderCheck -->|AD_HOC| AdhocChain["1. Profile Home + Sign-In Time<br/>2. Planned Stop Fallback"]
    RiderCheck -->|PLANNED| PlannedChain["1. Planned Stop + Planned Time<br/>2. Profile Home Fallback"]
```

#### The Office 400m Rule
At trip completion, drivers occasionally end the trip early on the highway or late at a distant parking depot. To prevent billing manipulation:
- Let $d_{\text{office}} = \text{HaversineMeters}(\text{tripEndActual}, \text{officePlanned})$.
- If $\text{tripEndActual} \ne \text{null}$ **and** $d_{\text{office}} \le 400.0\text{ m}$: anchor `tripEndActual` (`OFFICE_ACTUAL_WITHIN_400M`).
- Otherwise: anchor `officePlanned` at `tripEndTime` (`OFFICE_PLANNED_FALLBACK`).

#### Coincident Point Deduplication
After merging all GPS fixes and business anchors and sorting by timestamp (`AnchorPoint.compareTo`), if two points have $\Delta t = 0\text{ s}$ and are separated by $< 5.0\text{ m}$, the engine retains the authoritative business anchor (`EMPLOYEE_PICKUP`, `EMPLOYEE_DROP`, `OFFICE_END`) over the raw `GPS_PING`.

---

### 3.4 Stage III: Segment Distance Calculation (`SegmentCalculatorEngine.java`)

For each consecutive pair $(P_i, P_{i+1})$ in the unified anchor list:

1. **Cheap Aerial Short-Circuit (`SHORT_GAP_AERIAL`)**:
   - If $\Delta t < 70\text{ s}$ and $v_{\text{aerial}} \le 120\text{ km/h}$, road curvature over `< 70s` is negligible. Bill $d_{\text{aerial}}$ directly without calling the external routing API.
2. **Parked / Idling Detection (`PARKED`)**:
   - If $\Delta t \ge 70\text{ s}$ and $v_{\text{aerial}} < 5.0\text{ km/h}$, the cab was waiting at a traffic signal or pickup gate. Bill $d_{\text{aerial}}$ directly without wasting a routing call on GPS jitter.
3. **Real Gap Road Routing (`ROUTED`)**:
   - When $\Delta t \ge 70\text{ s}$ and $v_{\text{aerial}} \ge 5.0\text{ km/h}$, query `RoutingService.getDrivingRoute(start, end)`.
   - **Sanity Guards on Provider Output**:
     - If $d_{\text{routed}} > 100.0\text{ km}$ for a single gap $\implies$ `REJECTED_MAX_DISTANCE` (fallback to $d_{\text{aerial}}$).
     - If implied routed speed $v_{\text{routed}} > 120\text{ km/h}$ **and** $1.4 \times v_{\text{aerial}} > 120\text{ km/h} \implies$ `REJECTED_SPEED` (fallback to $d_{\text{aerial}}$).
4. **Degraded Fallback on Provider Failure (`1.4x Haversine`)**:
   - If the rate limiter throttles (`DEGRADED_RATE_LIMITED`), the circuit breaker is open (`DEGRADED_CIRCUIT_OPEN`), or the provider fails (`DEGRADED_PROVIDER_ERROR`), apply the empirical urban road-tortuosity factor:
     $$d_{\text{fallback}} = 1.4 \times d_{\text{aerial}}$$
   - Mark the overall trip status as `TripStatus.DEGRADED` so background reconciliation knows to re-route the trip once the provider recovers.

---

### 3.5 Stage IV: Deterministic Rounding & Signed Billing Deltas (`RecomputeDelta.java`)

Floating-point addition (`double += ...`) suffers from IEEE 754 representation drift across long segments. To guarantee financial accuracy:
- Every individual segment's `finalDistanceKm` is converted to `BigDecimal` scaled to **3 decimal places** (`RoundingMode.HALF_UP`, 1-meter resolution) before adding to `runningTotal`.
- The trip's `totalDistanceKm` is scaled to **2 decimal places** (`RoundingMode.HALF_UP`, 10-meter billing resolution).
- When recomputing version $v_k \to v_{k+1}$:
  $$\Delta d = \text{BigDecimal}(d_{\text{new}}).\text{setScale}(2) - \text{BigDecimal}(d_{\text{prev}}).\text{setScale}(2)$$

---

## 4. Resilience, Spatial Caching & Rate-Limiting Architecture

### 4.1 Spatial Grid Cell Cache (`RoutingCache.java`)
Raw vehicle GPS coordinates have 6–7 decimal places (`~1 cm` variation), which yields a **0% cache hit rate** if used directly as cache keys.
- By snapping start and end coordinates to 4 decimal places (`0.0001°` $\approx 11.1\text{ m}$), any two cabs traveling between the same residential gate and the same office campus entrance hit the exact same spatial cell key:
  `12.9250,77.6100->12.8800,77.5800`
- Backed by **Caffeine** (`maximumSize = 50,000`, `expireAfterWrite = 24h`), achieving **90%–99.9% cache hit rates** on commuter corridors.

### 4.2 Sliding Ring-Buffer Circuit Breaker (`RoutingCircuitBreaker.java`)

```mermaid
stateDiagram-v2
    [*] --> CLOSED
    CLOSED --> OPEN : Failure rate >= 50% over 10-call ring buffer\n(or Admin manual /trip)
    OPEN --> HALF_OPEN : resetTimeoutMillis (3000ms) elapsed
    HALF_OPEN --> CLOSED : 3 consecutive trial calls succeed
    HALF_OPEN --> OPEN : Any trial call fails
```

- **Thread-Safe Design**: Fast-path reads in `CLOSED` state are lock-free (`volatile State state`). State transitions and ring-buffer updates are guarded by a fair `ReentrantLock` and `AtomicInteger` counters.

### 4.3 Client-Side Token-Bucket Rate Limiter (`RoutingRateLimiter.java`)
When horizontally scaling worker instances, $W$ workers can overwhelm an external provider's QPS quota even when the provider is healthy.
- `RoutingRateLimiter` implements a continuous nano-time refill token bucket (`permitsPerSecond`, `burstCapacity`).
- Accepts an injectable `LongSupplier nanoClock` test seam so unit tests can verify sub-millisecond token depletion and refill deterministically without `Thread.sleep` flakiness.

---

## 5. Sequence Diagram: The Hardest Production Flow
### Underpass Gap Patching, Circuit Breaker Outage, and Post-Billing Signed Recomputation

```mermaid
sequenceDiagram
    autonumber
    actor Vendor as Cab / Billing Client
    participant API as TripController
    participant Svc as TripDistanceService
    participant Clean as StreamCleaner
    participant Anchor as AnchorPatchEngine
    participant Calc as SegmentCalculatorEngine
    participant Route as RoutingService
    participant DB as PostgreSQL / H2

    Note over Vendor,DB: Phase 1: Initial Ingestion During Provider Outage
    Vendor->>API: POST /api/v1/trips/calculate (Pings, Milestones, Office)
    API->>Svc: calculateAndSave(evidence)
    Svc->>Clean: clean(rawPings)
    Clean-->>Svc: Cleaned pings
    Svc->>Anchor: patchAndMerge(cleanedPings, evidence)
    Anchor-->>Svc: Unified timeline with anchors
    Svc->>Calc: calculate(unifiedPoints)

    loop Every consecutive segment
        alt Short Gap or Parked
            Calc->>Calc: Apply Haversine directly
        else Real Gap
            Calc->>Route: getDrivingRoute(start, end)
            alt Cache Hit
                Route-->>Calc: RouteResult CACHE_HIT
            else Circuit Breaker OPEN
                Route-->>Calc: RouteResult CIRCUIT_OPEN
                Calc->>Calc: Apply 1.4x Haversine Fallback
            end
        end
    end

    Calc-->>Svc: TripDistanceResult v1 (DEGRADED)
    Svc->>DB: INSERT trips v1 and trip_segments
    Svc-->>API: 200 OK (Invoice v1 Billed)

    Note over Vendor,DB: Phase 2: Provider Recovers & Late Pings Arrive
    Vendor->>API: POST /api/v1/trips/{tripId}/recompute (Late Pings)
    API->>Svc: recomputeTrip(tripId, updatedEvidence, reason)
    Svc->>DB: SELECT latest trip version v1
    Svc->>Calc: Recompute with late pings (Version 2)
    Calc->>Route: getDrivingRoute(start, end)
    Route-->>Calc: RouteResult SUCCESS (Live road route)
    Calc-->>Svc: TripDistanceResult v2 (COMPLETED)

    Svc->>Svc: Compute signed delta (+1.80 km CREDIT_NOTE)
    Svc->>DB: INSERT trips v2 (v1 untouched)
    Svc->>DB: INSERT trip_adjustments (+1.80 km CREDIT_NOTE)
    Svc-->>API: 200 OK (Signed Delta +1.80 km)
```

---

## 6. Database Schema & Multi-Tenant Entity-Relationship Design

```mermaid
erDiagram
    TRIPS ||--o{ TRIP_SEGMENTS : "contains (1:N cascade)"
    TRIPS ||--o{ TRIP_ADJUSTMENTS : "audits (v_prev -> v_new)"

    TRIPS {
        bigint id PK
        varchar trip_id "Indexed with tenant_id"
        varchar tenant_id "Hard Multi-Tenant Partition Key"
        int version "Unique per (trip_id, tenant_id, version)"
        double total_distance_km "Billed distance (2 decimal scale)"
        varchar status "COMPLETED | DEGRADED | FAILED"
        varchar rule_version "e.g. v1.2.0"
        int raw_pings_count
        int cleaned_pings_count
        int teleport_dropped_count
        int low_accuracy_dropped_count
        int anchors_inserted_count
        int provider_calls_attempted
        int provider_cache_hits
        int provider_cache_misses
        int provider_calls_failed
        timestamp calculated_at
    }

    TRIP_SEGMENTS {
        bigint id PK
        bigint trip_entity_id FK
        int segment_index "1-based order"
        double start_lat
        double start_lon
        timestamp start_time
        varchar start_type "GPS_PING | EMPLOYEE_DROP | ..."
        varchar start_desc "Audit source description"
        double end_lat
        double end_lon
        timestamp end_time
        varchar end_type
        varchar end_desc
        bigint delta_seconds
        double implied_aerial_speed_kmh
        double aerial_distance_km
        double final_distance_km "Billed segment distance"
        varchar rule "SHORT_GAP_AERIAL | PARKED | ROUTED | ..."
        varchar explanation "Human-readable dispute explanation"
        boolean is_routed
    }

    TRIP_ADJUSTMENTS {
        bigint id PK
        varchar trip_id "Indexed with tenant_id"
        varchar tenant_id "Hard Multi-Tenant Partition Key"
        int previous_version
        int new_version
        double previous_distance_km
        double new_distance_km
        double delta_km "Signed numeric delta"
        varchar formatted_delta "+2.98 km | -8.47 km | 0.00 km"
        varchar adjustment_type "CREDIT_NOTE | DEBIT_NOTE | NO_CHANGE"
        varchar reason "Audit reason for recomputation"
        timestamp created_at
    }
```

### Indexing Strategy
1. `idx_trip_tenant_id (trip_id, tenant_id)` on `trips`: Ensures sub-millisecond point lookups and prevents cross-tenant table scans.
2. `idx_trip_version (trip_id, tenant_id, version)` (`UNIQUE`) on `trips`: Enforces strict version uniqueness at the database constraint level, preventing race-condition duplicate versions under concurrent recomputations.
3. `idx_adjustment_trip_tenant (trip_id, tenant_id)` on `trip_adjustments`: Accelerates invoice adjustment ledger queries.

---

## 7. Failure-Mode, Mitigation & Recovery Matrix

| # | Failure Scenario | Root Cause | Engine Detection & Mitigation | Billing & Audit Impact | Recovery Mechanism |
| :-: | :--- | :--- | :--- | :--- | :--- |
| **1** | **GPS Teleport Burst (`8,000 km/h`)** | Urban canyon multipath or cell-tower handoff glitch | `StreamCleaner` compares implied speed against `lastAccepted` valid ping; drops fixes with $v > 120\text{ km/h}$. | Zero distance inflation; `teleportDroppedCount` recorded on trip header. | Subsequent valid fixes reconnect cleanly to `lastAccepted`. |
| **2** | **High-Radius GPS Noise (`> 50m`)** | Weak satellite lock inside covered driveway | `StreamCleaner` drops any ping with `accuracyMeters > 50.0` or `< 0`. | No noisy zig-zag segments billed; `lowAccuracyDroppedCount` incremented. | Next fix with `accuracy <= 50m` resumes timeline. |
| **3** | **22-Min Underpass / Basement Dropout** | Complete loss of satellite line-of-sight during drops | `AnchorPatchEngine` injects employee pickup/drop anchors inside the blackout and routes the sub-gaps. | Vendor paid accurate road distance (`30.6 km`) instead of chord (`18.4 km`). | Automatic fallback chain: Actual → Home (`AD_HOC`) / Planned (`PLANNED`). |
| **4** | **Employee No-Show at Pickup** | Cab drove to pickup point; rider cancelled/absent | `AnchorPatchEngine` anchors `NO_SHOW` coordinate when `includeNoShowAnchors=true`. | Vendor fairly compensated for deadhead detour to no-show stop. | Configurable per tenant via `EngineConfig.includeNoShowAnchors`. |
| **5** | **Driver Ends Trip 2 km Away from Office** | Driver forgets to close trip at campus gate | Office `400m` rule detects $d_{\text{office}} > 400\text{ m}$ and substitutes planned office coordinate (`OFFICE_PLANNED_FALLBACK`). | Prevents over-billing for post-trip personal driving. | Logged and labeled in segment `endDescription`. |
| **6** | **External Maps API Outage (`503` / Timeout)** | Third-party provider downtime or network partition | `RoutingCircuitBreaker` trips to `OPEN` at 50% failure rate; applies `1.4x Haversine` fallback (`DEGRADED_CIRCUIT_OPEN`). | Trip completes immediately with status `DEGRADED`; fleet checkout never blocks. | After `3000ms`, CB probes `HALF_OPEN` → `CLOSED`; trip recomputed. |
| **7** | **Outbound Routing Quota Spike** | Multi-worker burst exceeds provider QPS | `RoutingRateLimiter` rejects excess calls non-blockingly; segment uses `1.4x` fallback (`DEGRADED_RATE_LIMITED`). | Protects provider SLA and prevents cascading thread-pool starvation. | Token bucket refills continuously via nano-clock; `DEGRADED` trips reconciled. |
| **8** | **Corrupt Provider Route (`> 100 km`)** | Map snap error onto wrong highway direction | `SegmentCalculatorEngine` rejects routes $> 100\text{ km}$ (`REJECTED_MAX_DISTANCE`) or $> 120\text{ km/h}$ (`REJECTED_SPEED`). | Falls back to safe Haversine aerial distance for that segment. | Anomaly flagged in segment rule breakdown for map data review. |
| **9** | **Late Pings Synced After Invoice Closed** | Driver phone regains cellular data hours later | `/recompute` runs idempotent calculation on $v_{k+1}$ and persists `RecomputeDelta`. | Original invoice $v_k$ untouched; emits signed `CREDIT_NOTE` or `DEBIT_NOTE`. | Full audit trail in `trip_adjustments` table. |
| **10** | **Cross-Tenant Query Attempt** | Misconfigured client header or malicious probe | `TenantFilter` + repository `AndTenantId` clauses isolate every query to `TenantContext.getTenantId()`. | Returns `404` / empty list; zero cross-tenant data leakage. | MDC logs `Tenant` and `Trace` IDs for security auditing. |

---

## 8. Algorithmic Complexity & 15-Million-Ping Benchmark Analysis

### 8.1 Stage-by-Stage Time & Space Complexity
Let $N$ be the number of raw GPS pings in a trip, $K$ be the number of business milestones ($K \ll N$, typically $K \le 10$), and $M$ be the number of unified points ($M \le N + K + 2$):

| Pipeline Stage | Time Complexity | Space Complexity | Justification |
| :--- | :---: | :---: | :--- |
| **1. Timestamp Deduplication** | $\mathcal{O}(N)$ | $\mathcal{O}(N)$ | Single pass into pre-sized `HashMap<Instant, GpsPing>(N)`. |
| **2. Chronological Sorting** | $\mathcal{O}(N \log N)$ | $\mathcal{O}(N)$ | Java TimSort; degrades to $\mathcal{O}(N)$ when pings arrive in near-chronological order. |
| **3. Accuracy & Teleport Scan** | $\mathcal{O}(N)$ | $\mathcal{O}(N)$ | Single linear pass comparing each fix with `lastAccepted` via $\mathcal{O}(1)$ Haversine math. |
| **4. Anchor Patching & Coalescing** | $\mathcal{O}(M \log M)$ | $\mathcal{O}(M)$ | Indexes $K$ stops in $\mathcal{O}(K)$, merges with cleaned pings, sorts & deduplicates in one pass. |
| **5. Segment Evaluation & Routing** | $\mathcal{O}(M)$ | $\mathcal{O}(M)$ | $M-1$ iterations; each does $\mathcal{O}(1)$ Haversine math and $\mathcal{O}(1)$ Caffeine spatial cache lookup. |
| **Total Per Trip** | **$\mathcal{O}(N \log N)$** | **$\mathcal{O}(N)$** | Optimal lower bound for comparison-sorted telemetry streams. |

### 8.2 Global Memory Bounds
- **Per-Trip Buffer**: Strictly garbage-collected upon request completion (`~200 KB` for a 1,500-ping trip).
- **Spatial Routing Cache**: Bounded by Caffeine `maximumSize = 50,000` entries ($\approx 15\text{ MB}$ total heap footprint).

### 8.3 Empirical 15-Million-Ping Benchmark Results
Validated in `ScaleBenchmarkTest` (**10,000 trips × 1,500 pings = 15,000,000 pings**):
- **Total Wall-Clock Time**: `19.65 seconds`
- **Sustained Throughput**: `508.83 trips/sec` (`763,242 pings/sec`)
- **Per-Trip Latency**: `p50 = 1.782 ms`, `p95 = 2.835 ms`, `p99 = 4.415 ms`
- **Net Heap Growth**: `2.27 MB` (proving zero memory leaks under sustained load)

---

## 9. Deliberate Engineering Trade-offs

### 1. Availability vs. Immediate Consistency During Provider Outages (PACELC: PC/EC)
- **Dilemma**: When Google Maps / OSRM is unreachable at 3 AM, should the engine fail the trip calculation (`HTTP 503`) or compute a fallback distance?
- **Decision**: Choose **Availability** with explicit degradation (`TripStatus.DEGRADED`, `1.4x Haversine`), followed by **Eventual Consistency** via signed-delta recomputation once the circuit breaker closes.
- **Rationale**: Blocking thousands of shift-change cabs from closing trips halts fleet operations. Marking the trip `DEGRADED` makes the estimate transparent and auditable.

### 2. Append-Only Versioning vs. In-Place Row Updates
- **Dilemma**: In-place `UPDATE trips SET total_distance_km = ...` requires less storage than inserting a new version row + segments + adjustment record.
- **Decision**: Strictly **append-only versioning** (`v1`, `v2`, `v3`) paired with `trip_adjustments`.
- **Rationale**: Disk storage is cheap; un-auditable mutations on closed financial invoices are a compliance failure.

### 3. 4-Decimal Spatial Grid Snapping (`~11.1m`) vs. Exact Coordinate Caching
- **Dilemma**: Snapping coordinates to `0.0001°` introduces up to `~7.8m` RMS endpoint quantization, whereas exact coordinates (`0.000001°`) have zero quantization error.
- **Decision**: Snap cache keys to **4 decimal places (`~11.1m`)**.
- **Rationale**: Raw GPS hardware already has `5m–15m` horizontal error. Snapping to `~11.1m` boosts cache hit rates from `~0%` to `>95%` across repeated corporate pickup/drop corridors while keeping distance variance well below the `0.01 km` (`10m`) billing precision.

---

## 10. Security, Multi-Tenancy & Zero-PII Architecture

1. **Hard Multi-Tenant Isolation (`TenantFilter` + `TenantContext`)**:
   - Every incoming HTTP request must supply `X-Tenant-ID`.
   - `TenantFilter` binds the tenant ID to a `ThreadLocal` (`TenantContext`) and SLF4J `MDC` (`[Tenant:...]`), and clears both in a `finally` block to prevent thread-pool contamination.
   - In multi-threaded batch execution (`TripBatchService`), the parent thread's `TenantContext` is explicitly captured and bound inside each worker thread.
2. **Role-Based Access Control (`SecurityConfig`)**:
   - Method-level `@PreAuthorize` guards separate read-only employee queries (`ROLE_EMPLOYEE`) from billing calculations (`ROLE_OPERATOR`) and circuit-breaker/cache administration (`ROLE_ADMIN`).
3. **Zero PII Leakage (`PiiMasker`)**:
   - All exception messages and untrusted strings pass through `PiiMasker.mask(...)`, which scrubs email addresses (`[EMAIL_REDACTED]`), 10-digit phone numbers (`[PHONE_REDACTED]`), and residential address patterns (`[ADDRESS_REDACTED]`) before logging or returning HTTP error payloads.

---

## 11. Observability, Prometheus Metrics & Distributed Tracing

`EngineMetrics` registers custom Micrometer instruments exported at **`GET /actuator/prometheus`**:
- `moveinsync.trips.calculated.total` *(Counter)*: Total trips processed.
- `moveinsync.trips.degraded.total` *(Counter)*: Trips completed in `DEGRADED` mode (primary SLA alert trigger).
- `moveinsync.trips.recomputed.total` *(Counter)*: Post-billing recomputations executed.
- `moveinsync.pings.teleport.dropped.total` *(Counter)*: Teleport burst fixes rejected.
- `moveinsync.pings.low_accuracy.dropped.total` *(Counter)*: Low-accuracy fixes rejected.
- `moveinsync.routing.cache.hits.total` / `misses.total` *(Counters)*: Real-time spatial cache efficiency.
- `moveinsync.trip.calculation.duration` *(Timer / Histogram)*: Latency distribution across percentiles.

Every log line is stamped with `[Tenant:%X{tenant_id}] [Trace:%X{trace_id}]` for instant correlation in ELK / Datadog / Grafana Loki.

---

## 12. 8-Layer Scalability & Growth Path (15M to 150M+ Pings)

This section details how the engine scales from **10,000 trips (1.5 crore / 15M pings)** to **100,000+ trips (15 crore / 150M+ pings)** without rewriting the core domain engine.

### 12.1 Workload Growth Projection
| Dimension | Baseline (Verified in Benchmark) | 10x Enterprise Scale |
| :--- | :--- | :--- |
| **Trips Processed** | `10,000` trips | `100,000` trips |
| **Pings per Trip** | `1,500` pings | `1,500` pings |
| **Total GPS Pings** | `15,000,000` (1.5 Crore) | `150,000,000` (15 Crore) |

### 12.2 The 8 Scaling Layers (Implemented vs. Planned)

| Layer | Architectural Pattern | Codebase Implementation Status |
| :--- | :--- | :--- |
| **Level 1: Stateless Horizontal Tier** | Multiple stateless Spring Boot instances behind an L7 Load Balancer (ALB / NGINX). | **Implemented**: Core engine (`TripDistanceRebuildingEngine`) holds zero session state. |
| **Level 2: Bounded Batch Processing** | Process massive backlogs in fixed-size pages (`BatchTripProcessor`) with per-batch retry to prevent `OutOfMemoryError`. | **Implemented**: `BatchTripProcessor<T>` enforces hard batch bounds, retries transient failures, and emits `BatchReport`. |
| **Level 3: Parallel Trip Workers** | Concurrent execution across independent trips while keeping each single trip's pings strictly timestamp-ordered. | **Implemented**: `ParallelTripProcessor` + `TripBatchService` (`POST /api/v1/trips/batch-calculate?workers=N`). |
| **Level 4: Database Indexing & Partitioning** | Composite `(trip_id, tenant_id, version)` indexes, monthly range partitioning on `trips`/`trip_segments`, and PostGIS geography types. | **Indexing Implemented** in `TripEntity` & `TripAdjustmentEntity`; monthly range partitioning & PostGIS ready for PG 16. |
| **Level 5: Distributed Spatial Cache (Redis)** | Shared L2 Redis cache behind L1 Caffeine cache so multi-instance fleet workers share spatial grid routes. | **L1 Caffeine Implemented** (`RoutingCache`); `RoutingProvider` interface allows drop-in Redis decorator. |
| **Level 6: Event-Driven Kafka Ingestion** | Partition `gps-pings` topic by `key = tripId` so all pings for a single trip land on the same partition in order. | **Planned** when continuous streaming ingestion replaces batch trip-end requests. |
| **Level 7: Outbound Provider Protection** | Client-side token-bucket rate limiter + sliding-window circuit breaker + spatial cache to prevent exhausting external Maps QPS. | **Fully Implemented**: `RoutingRateLimiter` + `RoutingCircuitBreaker` + `RoutingCache`. |
| **Level 8: Container & K8s Autoscaling** | Multi-stage Docker image + Kubernetes HPA scaling on CPU, memory, and Kafka consumer lag. | **Docker & Compose Implemented** (`Dockerfile`, `docker-compose.yml`); K8s HPA ready. |

### 12.3 Target High-Scale Distributed Topology

```mermaid
flowchart TD
    Devices["Cab GPS Trackers & Driver Apps"] --> LB["AWS ALB / NGINX Load Balancer"]
    LB --> Ingest["Stateless Spring Boot Ingestion Fleet"]
    Ingest --> Kafka{{"Kafka Topic: trip-evidence<br/>(Partition Key = tripId)"}}
    
    Kafka --> W1["Worker Pod 1<br/>ParallelTripProcessor"]
    Kafka --> W2["Worker Pod 2<br/>ParallelTripProcessor"]
    Kafka --> W3["Worker Pod N<br/>ParallelTripProcessor"]

    W1 & W2 & W3 --> L1Cache["L1 Caffeine + L2 Redis<br/>Spatial Grid Cache (~11.1m)"]
    L1Cache -->|Cache Miss| RL["RoutingRateLimiter +<br/>RoutingCircuitBreaker"]
    RL --> MapsAPI["External Maps Routing API"]

    W1 & W2 & W3 --> PGPrimary[("PostgreSQL 16 + PostGIS Primary<br/>(Partitioned trips, segments, adjustments)")]
    PGPrimary -.->|Async Streaming Replication| PGReplica[("PostgreSQL Read Replicas<br/>(Analytics & Vendor Dashboards)")]
```
