# MoveInSync Trip Distance Rebuilding Engine
### Enterprise Billing-Grade GPS Distance Rebuilding, Explainable Audit Trail & Signed Adjustment Ledger

[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg)](https://www.docker.com/)
[![Prometheus](https://img.shields.io/badge/Prometheus-Metrics-E6522C.svg)](https://prometheus.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## Table of Contents
1. [Core Requirements Quick-Reference](#1-core-requirements-quick-reference-how-to-run-assumptions-benchmarks-trade-offs)
2. [Problem Statement & Business Motivation](#2-problem-statement--business-motivation)
3. [Core Engineering Guarantees](#3-core-engineering-guarantees)
4. [Repository & Package Architecture](#4-repository--package-architecture)
5. [The 4-Stage Distance Rebuilding Pipeline](#5-the-4-stage-distance-rebuilding-pipeline)
6. [Billing Model, Versioning & Signed Adjustments](#6-billing-model-versioning--signed-adjustments)
7. [Quickstart & How to Run](#7-quickstart--how-to-run)
8. [Complete REST API Reference & Live Commands](#8-complete-rest-api-reference--live-commands)
9. [Manual Database & Billing Verification (H2 Console)](#9-manual-database--billing-verification-h2-console)
10. [Benchmark & Scale Performance (15 Million Pings)](#10-benchmark--scale-performance-15-million-pings)
11. [Design Assumptions](#11-design-assumptions)
12. [Architectural Trade-offs](#12-architectural-trade-offs)
13. [Automated Verification & Test Matrix](#13-automated-verification--test-matrix)
14. [Configuration Reference](#14-configuration-reference)

---

## 1. Core Requirements Quick-Reference (How to Run, Assumptions, Benchmarks, Trade-offs)

For rapid evaluation against case-study criteria:

| Requirement | Implementation Summary | Quick Link |
| :--- | :--- | :--- |
| **How to Run** | Local (`mvn spring-boot:run` on port 8080) with pre-seeded billing data in H2 Console, or containerized (`docker compose up --build` with PostgreSQL 16). | [Section 7: Quickstart](#7-quickstart--how-to-run) |
| **Assumptions** | Urban cab speed cap ($120$ km/h), GPS noise ceiling ($50$m), short-gap cutoff ($70$s), office perimeter ($400$m), WGS84 geodesic curvature, external Maps API QPS limits. | [Section 11: Assumptions](#11-design-assumptions) |
| **Benchmark Results** | **15,000,000 pings (10,000 trips)** in **19.65s** $\to$ **763,242 pings/sec**, p50: $1.78$ms, p99: $4.41$ms, heap delta: **2.27 MB**. | [Section 10: Benchmark](#10-benchmark--scale-performance-15-million-pings) |
| **Trade-offs** | Availability over immediate consistency ($1.4\times$ fallback vs blocking fleet); append-only versioning vs in-place row mutation; spatial grid snapping (~11m) vs exact coordinates. | [Section 12: Trade-offs](#12-architectural-trade-offs) |

---

## 2. Problem Statement & Business Motivation

Vendors who supply corporate commuter cabs to MoveInSync are compensated strictly per kilometer (`$/km`). Raw trip distances originate from in-cab GPS hardware trackers and driver smartphones operating in hostile urban environments. Naive distance calculation fails in four critical ways:

1. **Underpasses, Tunnels & Basement Parking (Vendor Under-Payment)**
   - GPS fixes drop out for 15–25 minutes while employees are picked up or dropped off inside underground basements or beneath multi-level flyovers.
   - A naive straight-line (Haversine) sum across a 22-minute blackout draws a chord across curved road networks and intermediate stops, under-paying the vendor (e.g., paying `18.4 km` for a `30.6 km` road trip) and triggering vendor disputes.
2. **Multipath Reflections & Teleport Bursts (Vendor Over-Payment & API Bankruptcy)**
   - Urban canyons and cell-tower trilateration glitches emit brief bursts of GPS fixes 9 km away with implied speeds exceeding `8,000 km/h`, or high-radius accuracy noise (`> 50 m`).
   - Feeding uncleaned streams to a third-party routing API over-pays the vendor by tens of kilometers and exhausts enterprise Maps API budgets.
3. **Third-Party Routing Provider Outages at 3 AM (Pipeline Stalls)**
   - External routing APIs suffer rate-limiting spikes (`HTTP 429`), transient `503` outages, or network partitions. Shift-change fleet billing at 3 AM cannot block or crash when an external dependency degrades.
4. **Post-Billing Delayed Pings (Audit & Accounting Violations)**
   - Driver phones frequently enter dead zones at the end of a trip and upload cached GPS pings hours *after* the trip invoice has been closed and billed.
   - Silently overwriting a closed invoice row destroys accounting reconciliation against bank disbursements.

### The Solution
The **MoveInSync Trip Distance Rebuilding Engine** reconstructs a deterministic, audit-grade distance for every trip where:
- **Every kilometer is 100% explainable** with a machine-readable `DistanceRule` and a human-readable vendor dispute explanation on every segment.
- **Every calculation is deterministic and versioned (`v1.2.0`)**: identical evidence under the same rule version is mathematically guaranteed to produce identical kilometers down to `0.01 km`.
- **Invoices are immutable (append-only)**: post-billing late pings or provider recovery recomputations create a new version (`v1 -> v2`) and emit a **signed billing adjustment (`+2.98 km CREDIT_NOTE` or `-8.47 km DEBIT_NOTE`)**.

---

## 2. Core Engineering Guarantees

| Capability | Engineering Implementation | Key Classes |
| :--- | :--- | :--- |
| **Stage I: Stream Cleaning** | Deduplicates identical timestamps (retaining the fix with lowest accuracy error), sorts chronologically via TimSort, drops fixes with `accuracy > 50m`, and rejects teleport bursts with implied speed `> 120 km/h`. | `StreamCleaner`, `StreamCleanerResult`, `GpsPing` |
| **Stage II: Gap Patching (Anchors)** | Injects business milestones into GPS blackouts. Uses actual pickup/drop coordinates if valid; otherwise falls back to **Profile Home Location** (for `AD_HOC` riders) or **Planned Stop** (for `PLANNED` riders). | `AnchorPatchEngine`, `AnchorPoint`, `AnchorType` |
| **No-Show & Office 400m Rules** | Anchors `NO_SHOW` stops when `includeNoShowAnchors=true`. Uses actual trip-end coordinate **only if within `400 m`** of the planned office; otherwise anchors the planned office coordinate. | `AnchorPatchEngine`, `OfficeLocation` |
| **Stage III: Explainable Segments** | Evaluates cheap rules first (`SHORT_GAP_AERIAL` for `< 70s`, `PARKED` for `>= 70s` at `< 5 km/h`), routes real gaps via `RoutingService`, and rejects anomalous routes (`> 100 km` or speed `> 120 km/h`). | `SegmentCalculatorEngine`, `SegmentResult`, `DistanceRule` |
| **Spatial Grid Caching** | Snaps coordinates to 4-decimal spatial grid cells (`~11.1 m` resolution) backed by a bounded 50,000-entry Caffeine LRU cache (`24h` TTL), cutting external API costs by up to 99%. | `RoutingCache`, `Coordinate` |
| **Sliding-Window Circuit Breaker** | Lock-guarded ring buffer (`window=10`, `failureRateThreshold=50%`, `resetTimeout=3000ms`, `halfOpenTrials=3`). Trips to `OPEN` on outage, applies `1.4x Haversine` fallback, and marks the trip `DEGRADED`. | `RoutingCircuitBreaker`, `RoutingService` |
| **Client-Side Token-Bucket Limiter** | Non-blocking token-bucket rate limiter (`RoutingRateLimiter`) protecting external provider quotas under multi-worker horizontal scale. | `RoutingRateLimiter` |
| **Stage IV: Signed Billing Deltas** | Append-only trip versioning (`v1`, `v2`, `v3`) and immutable `trip_adjustments` ledger emitting `CREDIT_NOTE`, `DEBIT_NOTE`, or `NO_CHANGE` using `BigDecimal` `HALF_UP` rounding. | `RecomputeDelta`, `TripAdjustmentEntity` |
| **Batch & Parallel Scaling** | Bounded memory chunking (`BatchTripProcessor`) with per-batch retry, and deterministic order-preserving multi-threaded execution (`ParallelTripProcessor`). | `BatchTripProcessor`, `ParallelTripProcessor` |
| **Multi-Tenant RBAC & Zero PII** | Hard tenant barrier (`X-Tenant-ID` via `TenantFilter` + `TenantContext`), role-based access (`ROLE_EMPLOYEE`, `ROLE_OPERATOR`, `ROLE_ADMIN`), and regex PII scrubbing (`PiiMasker`). | `TenantFilter`, `SecurityConfig`, `PiiMasker` |

---

## 3. Repository & Package Architecture

The core domain and distance-rebuilding engine are written in **pure, framework-free Java** (`domain`, `engine`, `routing`, `scaling`) with zero Spring or JPA annotations, making the core algorithm unit-testable in milliseconds and portable across synchronous REST, batch CLI, or Kafka stream workers.

```text
MoveInSync Case Study/
├── pom.xml                                                 # Maven build (Java 21+, Spring Boot 3.3.4, Caffeine, H2, PostgreSQL, Micrometer)
├── Dockerfile                                              # Multi-stage production container build (Eclipse Temurin 21 JRE)
├── docker-compose.yml                                      # One-command stack: App + PostgreSQL 16 with healthchecks
├── MoveInSync_Trip_Distance_Engine.postman_collection.json # Ready-to-import Postman collection (8 requests)
├── README.md                                               # Operational & developer guide
├── DESIGN.md                                               # Deep-dive system architecture & scaling design document
└── src/
    ├── main/
    │   ├── resources/
    │   │   └── application.yml                             # Server, H2/PostgreSQL datasource, Actuator, Prometheus, Rate-limit config
    │   └── java/com/moveinsync/distance/
    │       ├── TripDistanceEngineApplication.java          # Spring Boot entry point
    │       ├── config/
    │       │   └── EngineAppConfig.java                    # Wires pure-Java engine, cache, circuit breaker & rate limiter beans
    │       ├── controller/
    │       │   ├── TripController.java                     # REST API: /calculate, /batch-calculate, /recompute, /{tripId}, /adjustments
    │       │   ├── AdminController.java                    # Admin API: Circuit breaker status, manual trip/reset, cache invalidation
    │       │   └── GlobalExceptionHandler.java             # Standardized error responses with PiiMasker sanitization
    │       ├── domain/                                     # Immutable pure-Java value objects
    │       │   ├── Coordinate.java                         # WGS84 coordinate, Haversine math, 4-decimal spatial grid cell snapping
    │       │   ├── GpsPing.java                            # Raw telemetry point (Coordinate, Instant, accuracyMeters)
    │       │   ├── AnchorPoint.java                        # Unified timeline point (GPS fix or injected business milestone)
    │       │   ├── AnchorType.java                         # GPS_PING, TRIP_START, EMPLOYEE_PICKUP, EMPLOYEE_DROP, EMPLOYEE_NO_SHOW, OFFICE_END
    │       │   ├── DistanceRule.java                       # SHORT_GAP_AERIAL, PARKED, ROUTED, DEGRADED_*, REJECTED_*
    │       │   ├── SegmentResult.java                      # Single segment audit record (start, end, deltaSec, aerialKm, finalKm, rule, explanation)
    │       │   ├── TripEvidence.java                       # Complete input bundle (pings, milestones, plannedStops, office, profiles, config)
    │       │   ├── TripDistanceResult.java                 # Complete immutable output of a single calculation version
    │       │   ├── RecomputeDelta.java                     # Signed delta calculation (+X.XX km CREDIT_NOTE / -X.XX km DEBIT_NOTE / NO_CHANGE)
    │       │   ├── ProviderCallStats.java                  # Routing API hits, misses, failures, hit rate %, and USD cost tracker
    │       │   ├── EngineConfig.java                       # Versioned thresholds (v1.2.0, 50m, 120km/h, 70s, 5km/h, 400m, 100km, 1.4x)
    │       │   └── [TripMilestone, PlannedStop, EmployeeProfile, OfficeLocation, MilestoneType, MilestoneStatus, RiderType, TripStatus]
    │       ├── dto/                                        # Request/Response JSON data transfer objects
    │       ├── engine/                                     # 4-Stage Deterministic Distance Rebuilding Core
    │       │   ├── StreamCleaner.java                      # Stage I: Deduplication, sorting, accuracy & teleport filtering
    │       │   ├── StreamCleanerResult.java                # Cleaned pings + telemetry drop counters
    │       │   ├── AnchorPatchEngine.java                  # Stage II: Business milestone patching, fallback chain & 400m office rule
    │       │   ├── SegmentCalculatorEngine.java            # Stage III: Segment rule evaluation, routing invocation & BigDecimal summation
    │       │   └── TripDistanceRebuildingEngine.java       # Stage IV: Orchestrator & idempotent signed-delta recomputation
    │       ├── routing/                                    # Fault-tolerant external routing layer
    │       │   ├── RoutingProvider.java                    # Strategy interface for road-network routing providers
    │       │   ├── SimulatedRoutingProvider.java           # Deterministic road tortuosity (1.28x) simulator with failure injection
    │       │   ├── RoutingCache.java                       # Caffeine spatial cell cache (4-decimal grid key, 50k max, 24h TTL)
    │       │   ├── RoutingCircuitBreaker.java              # Thread-safe sliding ring-buffer circuit breaker (CLOSED/OPEN/HALF_OPEN)
    │       │   ├── RoutingRateLimiter.java                 # Token-bucket outbound call limiter with injectable virtual nano-clock
    │       │   ├── RoutingService.java                     # Integrates Cache -> RateLimiter -> CircuitBreaker -> Provider
    │       │   └── [RouteResult, RouteStatus]
    │       ├── scaling/                                    # High-volume throughput & memory primitives
    │       │   ├── BatchTripProcessor.java                 # Bounded-memory chunk fetcher & retry coordinator
    │       │   └── ParallelTripProcessor.java              # Order-preserving bounded thread-pool trip executor
    │       ├── entity/                                     # JPA Persistence Entities
    │       │   ├── TripEntity.java                         # `trips` table (versioned trip headers & telemetry counts)
    │       │   ├── TripSegmentEntity.java                  # `trip_segments` table (per-segment explainable audit trail)
    │       │   └── TripAdjustmentEntity.java               # `trip_adjustments` table (append-only signed billing ledger)
    │       ├── repository/                                 # Tenant-scoped Spring Data JPA Repositories
    │       │   ├── TripRepository.java
    │       │   └── TripAdjustmentRepository.java
    │       ├── service/                                    # Transactional application services
    │       │   ├── TripDistanceService.java                # Persistence, tenant enforcement & versioned recomputation
    │       │   └── TripBatchService.java                   # Multi-worker batch orchestration with TenantContext propagation
    │       ├── security/                                   # Multi-tenancy, RBAC & Privacy
    │       │   ├── TenantFilter.java                       # Extracts X-Tenant-ID, X-User-Role, X-Trace-ID & populates MDC
    │       │   ├── TenantContext.java                      # ThreadLocal tenant isolation holder
    │       │   ├── SecurityConfig.java                     # Method & endpoint RBAC policies
    │       │   └── PiiMasker.java                          # Regex sanitizer for emails, phone numbers, and street addresses
    │       └── monitoring/
    │           └── EngineMetrics.java                      # Micrometer counters & timers exported to /actuator/prometheus
    └── test/                                               # 14 Comprehensive Unit, Concurrency, E2E & 15M-Ping Scale Tests
```

---

## 4. The 4-Stage Distance Rebuilding Pipeline

### Stage I: Stream Cleaning (`StreamCleaner`)
Raw GPS pings from hardware/phones are cleaned in four deterministic steps:
1. **Null & Malformed Guard**: Drops pings missing coordinates or timestamps; validates WGS84 bounds (`lat ∈ [-90, 90]`, `lon ∈ [-180, 180]`).
2. **Timestamp Deduplication (`O(N)`)**: Groups pings by exact `Instant`. If multiple fixes share a timestamp, retains the fix with the **smallest `accuracyMeters` radius** (highest precision).
3. **Chronological Ordering (`O(N log N)`)**: Sorts pings strictly ascending by timestamp using TimSort (which runs in `O(N)` on nearly-ordered streams).
4. **Sequential Accuracy & Teleport Filter (`O(N)`)**:
   - **Low Accuracy Rejection**: Drops any ping where `accuracyMeters > 50.0 m` (or `< 0`).
   - **Teleport Jump Rejection**: Computes implied aerial speed from the *last accepted valid ping* (`lastAccepted`), not from a previously rejected noisy ping:
     $$\text{Speed}_{\text{km/h}} = \frac{\text{HaversineKm}(\text{lastAccepted}, \text{candidate})}{\Delta t_{\text{seconds}}} \times 3600$$
     If $\text{Speed}_{\text{km/h}} > 120.0\text{ km/h}$, the candidate ping is dropped as a teleport burst and `teleportDroppedCount` is incremented.

### Stage II: Gap Patching with Business Anchors (`AnchorPatchEngine`)
Cleaned GPS pings are merged with authoritative business events (`TripMilestone`, `PlannedStop`, `EmployeeProfile`, `OfficeLocation`):

1. **Employee Pickup & Drop Anchors (`MilestoneStatus.COMPLETED`)**:
   - **Primary**: Uses the milestone's actual GPS coordinate and timestamp if valid.
   - **Fallback Chain (when GPS was blacked out during pickup/drop)**:
     - If `RiderType == AD_HOC`:
       1. Employee Profile **Home Location** + **Sign-In Time** (`EMPLOYEE_HOME_FALLBACK_ADHOC`)
       2. **Planned Stop** coordinate + planned time (`PLANNED_STOP_FALLBACK`)
     - If `RiderType == PLANNED`:
       1. **Planned Stop** coordinate + planned time (`PLANNED_STOP_FALLBACK`)
       2. Employee Profile **Home Location** + **Sign-In Time** (`EMPLOYEE_HOME_FALLBACK`)
2. **No-Show Anchors (`MilestoneType.NO_SHOW` or `MilestoneStatus.NO_SHOW`)**:
   - Controlled by feature flag `includeNoShowAnchors` (default `true`), because vendors must still be compensated when a cab drives to a pickup point and the employee fails to show up.
   - Resolves actual coordinate → Planned Stop fallback → Profile Home fallback.
3. **Office 400m Rule (`OfficeLocation`)**:
   - Computes Haversine distance in meters between `tripEndActualCoordinate` and the planned `OfficeLocation`:
     - If $\text{distance} \le 400.0\text{ m}$: uses actual trip-end coordinate (`OFFICE_ACTUAL_WITHIN_400M`).
     - If $\text{distance} > 400.0\text{ m}$ (or missing): anchors the planned office coordinate at `tripEndTime` (`OFFICE_PLANNED_FALLBACK`).
4. **Unified Timeline Coalescing**:
   - Sorts all GPS and business anchors chronologically. If a GPS ping and a business anchor occur at the exact same timestamp (`Δt = 0s`) within `< 5.0 m`, the authoritative business anchor replaces the raw GPS ping.

### Stage III: Per-Segment Distance Evaluation (`SegmentCalculatorEngine`)
For every consecutive pair of points $(P_i, P_{i+1})$ in the unified timeline, with $\Delta t = t_{i+1} - t_i$, aerial distance $d_{\text{aerial}}$, and implied aerial speed $v_{\text{aerial}}$:

| Priority | Condition | Applied Rule (`DistanceRule`) | Billable Distance Formula | Routed? | Marks Trip `DEGRADED`? |
| :---: | :--- | :--- | :--- | :---: | :---: |
| **1** | $\Delta t < 70\text{ s}$ and $v_{\text{aerial}} \le 120\text{ km/h}$ | `SHORT_GAP_AERIAL` | $d_{\text{aerial}}$ (Haversine) | No | No |
| **2** | $\Delta t \ge 70\text{ s}$ and $v_{\text{aerial}} < 5.0\text{ km/h}$ | `PARKED` | $d_{\text{aerial}}$ (Cab idling/parked) | No | No |
| **3** | Real gap ($\Delta t \ge 70\text{ s}, v_{\text{aerial}} \ge 5\text{ km/h}$) & Rate Limiter throttled | `DEGRADED_RATE_LIMITED` | $1.4 \times d_{\text{aerial}}$ | No | **Yes** |
| **4** | Real gap & Circuit Breaker `OPEN` | `DEGRADED_CIRCUIT_OPEN` | $1.4 \times d_{\text{aerial}}$ | No | **Yes** |
| **5** | Real gap & Provider throws error / `5xx` | `DEGRADED_PROVIDER_ERROR` | $1.4 \times d_{\text{aerial}}$ | No | **Yes** |
| **6** | Provider returns $d_{\text{routed}} > 100.0\text{ km}$ | `REJECTED_MAX_DISTANCE` | $d_{\text{aerial}}$ | No | No |
| **7** | Provider $v_{\text{routed}} > 120\text{ km/h}$ **AND** $1.4 \times v_{\text{aerial}} > 120\text{ km/h}$ | `REJECTED_SPEED` | $d_{\text{aerial}}$ | No | No |
| **8** | Provider returns valid driving distance | `ROUTED` | $d_{\text{routed}}$ (Cache hit or Live API) | **Yes** | No |

Each segment distance is rounded to **3 decimal places** (`RoundingMode.HALF_UP`, meter precision) before accumulation, and the final trip total is rounded to **2 decimal places** (`0.01 km` billing precision).

---

## 5. Billing Model, Versioning & Signed Adjustments

### Why Invoices Are Never Overwritten
In enterprise transport billing, once Version 1 of a trip is calculated and invoiced, mutating `total_distance_km` in place breaks financial audit trails. Instead, `TripDistanceService.recomputeTrip(...)` implements an **immutable, append-only ledger**:

1. **Fetch Latest Version**: Looks up the highest existing version $v_k$ for `(tripId, tenantId)`.
2. **Compute Version $v_{k+1}$**: Runs the full 4-stage pipeline on the updated evidence (e.g., including newly synced late pings or against a recovered routing provider).
3. **Compute Signed Delta (`RecomputeDelta`)**:
   $$\Delta d = \text{round}_2(d_{\text{new}}) - \text{round}_2(d_{\text{prev}})$$
   - **$\Delta d > 0.00\text{ km} \implies$ `CREDIT_NOTE`** (Formatted: `"+2.98 km"` — Vendor is owed additional compensation).
   - **$\Delta d < 0.00\text{ km} \implies$ `DEBIT_NOTE`** (Formatted: `"-8.47 km"` — Vendor was over-billed on prior version).
   - **$\Delta d == 0.00\text{ km} \implies$ `NO_CHANGE`** (Formatted: `"0.00 km"` — Idempotent verification).
4. **Atomic Persistence**: Both the new `TripEntity` ($v_{k+1}$) with its `TripSegmentEntity` children and the `TripAdjustmentEntity` are committed in a single `@Transactional` boundary.

### Routing API Cost Accounting (`ProviderCallStats`)
Every trip calculation tracks third-party routing API usage and financial savings from the spatial cache:
- `callsAttempted = cacheHits + cacheMisses`
- `totalCostUsd = cacheMisses * 0.005` (`\$0.005` per live external routing API lookup)
- `costSavedUsd = cacheHits * 0.005`
- `cacheHitRatePercentage = (cacheHits / callsAttempted) * 100.0`

---

## 6. Quickstart & Running the Engine

### Prerequisites
- **Java 21+** (Compatible with JDK 21 through JDK 26)
- **Maven 3.9+** (or Docker Desktop for containerized run)

### Option A: Local Run with In-Memory H2 Database (Recommended for Evaluation)
```powershell
mvn spring-boot:run
```
- Starts on **`http://localhost:8080`**
- Automatically initializes the in-memory PostgreSQL-compatible H2 schema (`trips`, `trip_segments`, `trip_adjustments`).
- Enables the interactive H2 Web Console at **`http://localhost:8080/h2-console`**.

### Option B: Production Stack with Docker Compose & PostgreSQL 16
```powershell
docker compose up --build
```
- Starts a dedicated `postgres:16-alpine` container (`moveinsync-postgres` on port `5432`) and builds the multi-stage Spring Boot container (`moveinsync-distance-engine` on port `8080`).

---

## 7. Complete REST API Reference & Live Commands

### Security & Multi-Tenancy Headers
All `/api/v1/**` endpoints are protected by `TenantFilter` and Spring Security method-level RBAC:

| Header | Required? | Description | Example Values |
| :--- | :---: | :--- | :--- |
| `X-Tenant-ID` | **Yes** | Hard multi-tenant partition key | `moveinsync-client-1`, `client-acme` |
| `X-User-Role` | **Yes** | RBAC role (`EMPLOYEE`, `OPERATOR`, `ADMIN`) | `ROLE_OPERATOR`, `ROLE_ADMIN`, `ROLE_EMPLOYEE` |
| `X-Trace-ID` | Optional | Distributed correlation ID (auto-generated if omitted) | `trace-blr-9921` |

#### Role Permission Matrix
| Endpoint | Method | `ROLE_EMPLOYEE` | `ROLE_OPERATOR` | `ROLE_ADMIN` |
| :--- | :---: | :---: | :---: | :---: |
| `/api/v1/trips/calculate` | `POST` | ❌ (`403`) | ✅ | ✅ |
| `/api/v1/trips/batch-calculate` | `POST` | ❌ (`403`) | ✅ | ✅ |
| `/api/v1/trips/{tripId}/recompute` | `POST` | ❌ (`403`) | ✅ | ✅ |
| `/api/v1/trips/{tripId}` | `GET` | ✅ | ✅ | ✅ |
| `/api/v1/trips/{tripId}/adjustments` | `GET` | ❌ (`403`) | ✅ | ✅ |
| `/api/v1/admin/circuit-breaker/**` | `GET/POST` | ❌ (`403`) | ❌ (`403`) | ✅ |
| `/api/v1/admin/cache/invalidate` | `POST` | ❌ (`403`) | ❌ (`403`) | ✅ |

---

### Endpoint 1: Ingest & Calculate Trip Distance (`POST /api/v1/trips/calculate`)

#### PowerShell Command
```powershell
$headers = @{
  "Content-Type" = "application/json"
  "X-Tenant-ID"  = "moveinsync-client-1"
  "X-User-Role"  = "ROLE_OPERATOR"
}

$calcBody = @'
{
  "tripId": "TRIP-BLR-1001",
  "tenantId": "moveinsync-client-1",
  "rawPings": [
    {"latitude": 12.9352, "longitude": 77.6245, "timestamp": "2026-10-29T18:00:00Z", "accuracy": 10.0},
    {"latitude": 12.9250, "longitude": 77.6100, "timestamp": "2026-10-29T18:05:00Z", "accuracy": 12.0},
    {"latitude": 12.8500, "longitude": 77.5500, "timestamp": "2026-10-29T18:27:00Z", "accuracy": 8.0}
  ],
  "milestones": [
    {
      "type": "DROP",
      "employeeId": "EMP-RAJESH-1",
      "latitude": 12.8800,
      "longitude": 77.5800,
      "timestamp": "2026-10-29T18:15:00Z",
      "riderType": "PLANNED",
      "status": "COMPLETED"
    }
  ],
  "officeLocation": {
    "officeId": "OFF-BLR-ECOSPACE",
    "name": "Ecospace Tech Park",
    "latitude": 12.9352,
    "longitude": 77.6245
  },
  "tripStartTime": "2026-10-29T18:00:00Z",
  "tripEndTime": "2026-10-29T18:30:00Z"
}
'@

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trips/calculate" -Method Post -Headers $headers -Body $calcBody | ConvertTo-Json -Depth 5
```

---

### Endpoint 2: Recompute Trip with Late Pings (`POST /api/v1/trips/{tripId}/recompute`)

#### PowerShell Command
```powershell
$recomputeCredit = @'
{
  "reason": "FINAL_LEG_UNDERPASS_PINGS_SYNCED",
  "latePings": [
    {"latitude": 12.9352, "longitude": 77.6245, "timestamp": "2026-10-29T18:00:00Z", "accuracy": 10.0},
    {"latitude": 12.9250, "longitude": 77.6100, "timestamp": "2026-10-29T18:05:00Z", "accuracy": 12.0},
    {"latitude": 12.8800, "longitude": 77.5800, "timestamp": "2026-10-29T18:15:00Z", "accuracy": 10.0},
    {"latitude": 12.8500, "longitude": 77.5500, "timestamp": "2026-10-29T18:27:00Z", "accuracy": 8.0},
    {"latitude": 12.8300, "longitude": 77.5300, "timestamp": "2026-10-29T18:32:00Z", "accuracy": 10.0},
    {"latitude": 12.8150, "longitude": 77.5150, "timestamp": "2026-10-29T18:37:00Z", "accuracy": 9.0}
  ]
}
'@

Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trips/TRIP-BLR-1001/recompute" -Method Post -Headers $headers -Body $recomputeCredit | ConvertTo-Json -Depth 5
```

#### Sample Response
```json
{
  "tripId": "TRIP-BLR-1001",
  "tenantId": "moveinsync-client-1",
  "previousVersion": 2,
  "newVersion": 3,
  "previousDistanceKm": 20.06,
  "newDistanceKm": 23.04,
  "deltaKm": 2.98,
  "formattedDelta": "+2.98 km",
  "adjustmentType": "CREDIT_NOTE",
  "reason": "FINAL_LEG_UNDERPASS_PINGS_SYNCED",
  "calculatedAt": "2026-10-30T05:46:49.175808800Z"
}
```

---

### Endpoint 3: Get Trip Audit Trail by Version (`GET /api/v1/trips/{tripId}?version=1`)
```powershell
# Latest version
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trips/TRIP-BLR-1001" -Method Get -Headers $headers | ConvertTo-Json -Depth 5

# Specific historical version (e.g., original invoice v1)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trips/TRIP-BLR-1001?version=1" -Method Get -Headers $headers | ConvertTo-Json -Depth 5
```

---

### Endpoint 4: Get Signed Billing Adjustments Ledger (`GET /api/v1/trips/{tripId}/adjustments`)
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trips/TRIP-BLR-1001/adjustments" -Method Get -Headers $headers | ConvertTo-Json -Depth 5
```

---

### Endpoint 5: Parallel Batch Trip Calculation (`POST /api/v1/trips/batch-calculate?workers=4`)
Processes multiple independent trips concurrently across a bounded worker pool while preserving exact input ordering and tenant context isolation.
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/trips/batch-calculate?workers=4" -Method Post -Headers $headers -Body "[$calcBody]" | ConvertTo-Json -Depth 5
```

---

### Endpoint 6–8: Admin Circuit Breaker & Cache Controls (`/api/v1/admin/**`)
```powershell
$adminHeaders = @{ "X-Tenant-ID" = "moveinsync-client-1"; "X-User-Role" = "ROLE_ADMIN" }

# Inspect Circuit Breaker & Spatial Cache Hit Rate
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/circuit-breaker" -Method Get -Headers $adminHeaders

# Trip Circuit Breaker to OPEN (Simulate external Maps API outage)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/circuit-breaker/trip" -Method Post -Headers $adminHeaders

# Reset Circuit Breaker to CLOSED (Simulate provider recovery)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/circuit-breaker/reset" -Method Post -Headers $adminHeaders

# Invalidate Spatial Routing Cache
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/cache/invalidate" -Method Post -Headers $adminHeaders
```

---

## 8. Manual Database & Billing Verification (H2 Console)

While `mvn spring-boot:run` is active, you can visually inspect the billing tables in your browser:

1. Open **`http://localhost:8080/h2-console`**
2. Enter the connection settings:
   - **Driver Class**: `org.h2.Driver`
   - **JDBC URL**: `jdbc:h2:mem:moveinsync_db`
   - **User Name**: `sa`
   - **Password**: *(leave empty)*
3. Click **Connect**.
4. Click any table on the left sidebar (`TRIP_ADJUSTMENTS`, `TRIPS`, `TRIP_SEGMENTS`) and click **Run**, or execute these audit queries:

```sql
-- 1. Inspect the Signed Billing Adjustment Ledger (Credit & Debit Notes)
SELECT 
    ID, TRIP_ID, TENANT_ID,
    PREVIOUS_VERSION, NEW_VERSION,
    PREVIOUS_DISTANCE_KM, NEW_DISTANCE_KM,
    DELTA_KM, FORMATTED_DELTA, ADJUSTMENT_TYPE, REASON, CREATED_AT
FROM TRIP_ADJUSTMENTS
ORDER BY ID ASC;

-- 2. Inspect Immutable Versioned Trip Invoices (v1, v2, v3...)
SELECT 
    TRIP_ID, TENANT_ID, VERSION, TOTAL_DISTANCE_KM, STATUS, RULE_VERSION,
    RAW_PINGS_COUNT, CLEANED_PINGS_COUNT, TELEPORT_DROPPED_COUNT,
    LOW_ACCURACY_DROPPED_COUNT, ANCHORS_INSERTED_COUNT,
    PROVIDER_CALLS_ATTEMPTED, PROVIDER_CACHE_HITS, PROVIDER_CACHE_MISSES, CALCULATED_AT
FROM TRIPS
ORDER BY TRIP_ID, VERSION ASC;

-- 3. Inspect Per-Segment Explainable Kilometers
SELECT 
    SEGMENT_INDEX, START_TYPE, END_TYPE, DELTA_SECONDS,
    IMPLIED_AERIAL_SPEED_KMH, AERIAL_DISTANCE_KM, FINAL_DISTANCE_KM,
    RULE, ROUTED, EXPLANATION
FROM TRIP_SEGMENTS
ORDER BY ID ASC;
```

---

## 9. Benchmark & Scale Performance (15 Million Pings)

Executed via `ScaleBenchmarkTest` on **AMD Ryzen 5 5500H** (4 Cores / 8 Logical Processors, Windows 11, OpenJDK 64-Bit Server VM), including a midway routing provider outage injection across trips `4,000–4,500`:

```text
==========================================================================
BENCHMARK RESULTS REPORT
==========================================================================
Total Trips Processed       : 10,000
Pings Per Trip              : 1,500
Total Pings Ingested        : 15,000,000
Total Elapsed Time          : 19.65 seconds
Throughput (Trips/sec)      : 508.83 trips/sec
Throughput (Pings/sec)      : 763,242.25 pings/sec
Latency p50                 : 1.782 ms
Latency p95                 : 2.835 ms
Latency p99                 : 4.415 ms
Routing Cache Hits          : 13,497
Routing Cache Misses        : 1,503
Cached Spatial Routes Count : 3
Degraded Trips Count (Fail) : 500 (during midway outage)
Completed Healthy Trips     : 9,500
Memory Delta (Heap)         : 2.27 MB
==========================================================================
```

### Complexity Summary
- **Time Complexity**: $\mathcal{O}(N \log N)$ per trip where $N$ is raw GPS pings (dominated by TimSort after $\mathcal{O}(N)$ hash deduplication, followed by $\mathcal{O}(N)$ single-pass cleaning and $\mathcal{O}(1)$ spatial cache lookups).
- **Space Complexity**: $\mathcal{O}(N)$ strictly linear per in-flight trip; global spatial cache is strictly bounded at `50,000` entries (`~15 MB` heap).

---

## 11. Design Assumptions

The distance rebuilding engine operates under explicit domain and infrastructure assumptions:

1. **Urban Transport Velocity Bounds**:
   - The maximum physically plausible speed for a commuter cab in an urban/suburban corridor is assumed to be **$120.0$ km/h**. Any instantaneous implied aerial velocity above this threshold between consecutive valid fixes is classified as an un-drivable multipath teleport jump.
2. **GPS Accuracy Thresholds**:
   - Commercial smartphone and OBD tracker GPS fixes with an estimated horizontal dilution/accuracy radius $> 50.0$ meters are treated as degraded noise (e.g. reflections in high-rise canyons or basements) and dropped prior to distance accumulation.
3. **Short-Gap Straight-Line Linearity ($< 70$s)**:
   - For consecutive GPS fixes under 70 seconds apart at normal speeds ($\le 120$ km/h), the difference between road-network driving distance and WGS84 geodesic straight-line distance is mathematically negligible ($< 0.1\%$). Calling third-party routing APIs for these micro-segments is assumed to be an unnecessary latency and billing overhead.
4. **Office Campus Perimeter ($400$m)**:
   - Corporate tech parks (e.g., Ecospace, Manyata) span hundreds of meters from entrance gates to interior drop-off zones. An actual trip-end fix within 400 meters of the planned office pin is assumed to be a valid drop at an internal building; beyond 400 meters, it is assumed the driver failed to end the trip at the campus gate.
5. **No-Show Compensation**:
   - Cabs that arrive at an employee pickup location where the rider is marked `NO_SHOW` must still be compensated for the deadhead mileage driven to the planned stop.
6. **Provider Availability & Tortuosity Factor**:
   - External road routing APIs (Google Maps, OSRM) can experience rate-limiting spikes or 3 AM outages. During outages, the ratio of actual road distance to straight-line aerial distance in urban road grids is empirically assumed to be **$1.4\times$** (road tortuosity multiplier).
7. **Append-Only Accounting**:
   - Financial systems require that once an invoice version $v_k$ is issued, it must never be mutated in place. Any post-billing adjustments must produce version $v_{k+1}$ with a signed adjustment note.

---

## 12. Architectural Trade-offs

| Decision | Chosen Approach | Alternative Considered | Trade-off Rationale |
| :--- | :--- | :--- | :--- |
| **1. Resilience (PACELC)** | **Availability & Explicit Degradation** ($1.4\times$ fallback, trip marked `DEGRADED`) | **Strict Consistency** (Block trip completion with `HTTP 503` until Maps API recovers) | Blocking thousands of shift-change cabs at 3 AM freezes enterprise operations. Providing an immediate transparent fallback and auto-reconciling later restores eventual consistency without blocking operations. |
| **2. Audit Ledger** | **Append-Only Versioning** (`v1 \to v2 \to v3` + `trip_adjustments` signed delta) | **In-Place Mutation** (`UPDATE trips SET total_distance_km = ...`) | While in-place mutation saves database rows, silently altering a billed invoice triggers accounting audit failures and vendor disputes. Disk storage is cheap; financial auditability is mandatory. |
| **3. Spatial Caching** | **4-Decimal Grid Snapping** ($0.0001^\circ \approx 11.1$m resolution Caffeine cache) | **Exact Coordinate Caching** (Full 6-decimal floating-point keys) | Vehicle GPS fixes exhibit 5m–15m jitter, yielding a ~0% cache hit rate with exact coordinates. Snapping to an ~11m box yields **90%–99% cache hits** across repeated office commuter corridors with negligible quantization error ($< 0.01$ km). |
| **4. Database Pool Protection** | **Narrow Write Transactions** via `TransactionTemplate` (CPU & Routing I/O executed *outside* DB tx) | **Broad Method-Level `@Transactional`** across the whole calculation pipeline | Holding HikariCP database connections during 200–500ms external HTTP routing calls quickly causes connection pool starvation. Narrow transactions hold DB locks only for the $\sim 2$ms write phase. |
| **5. Core Portability** | **Hexagonal Pure-Java Engine** (Zero Spring / JPA annotations in domain/engine) | **Framework-Coupled Entities** (Domain models carrying JPA/Hibernate annotations) | A pure-Java core allows running 15M-ping benchmarks and mathematical unit tests in milliseconds without launching the Spring ApplicationContext. |

---

## 13. Automated Verification & Test Matrix

Run the entire test suite (14 test classes covering unit, property, concurrency, multi-tenant security, fault-injection, and 15M-ping scale tests):

```powershell
mvn test
```

Run specific test suites:
```powershell
# Run only Billing & Signed Delta tests
mvn test -Dtest=RecomputeSignedDeltaTest,TripControllerE2ETest

# Run 15-Million-Ping Benchmark
mvn test -Dtest=ScaleBenchmarkTest
```

### Complete Test Suite Breakdown
| Test Class | Scope | What It Verifies |
| :--- | :--- | :--- |
| `CoordinateTest` | Unit / Math | WGS84 bounds validation, Haversine accuracy, zero-distance identity, 4-decimal spatial cell snapping (`~11m`). |
| `StreamCleanerTest` | Stage I | Duplicate timestamp resolution preferring higher accuracy, out-of-order sorting, `> 50m` accuracy drop, `8,000 km/h` teleport burst rejection. |
| `AnchorPatchEngineTest` | Stage II | `AD_HOC` rider home fallback, `PLANNED` rider stop fallback, `includeNoShowAnchors` flag toggle, Office `400m` boundary inclusion/exclusion. |
| `SegmentCalculatorEngineTest` | Stage III | `< 70s` `SHORT_GAP_AERIAL`, `>= 70s` at `< 5 km/h` `PARKED`, `ROUTED` gaps, `> 100 km` `REJECTED_MAX_DISTANCE`, `> 120 km/h` `REJECTED_SPEED`. |
| `RecomputeSignedDeltaTest` | Stage IV (Billing) | 22-min underpass dropout reconstruction (`30.6 km`), post-billing late-ping `CREDIT_NOTE` (`+1.8 km`), deterministic `0.00 km` `NO_CHANGE` idempotency. |
| `CircuitBreakerTest` | Resilience | Ring-buffer 50% failure trip to `OPEN`, `1.4x` degraded fallback, `resetTimeout` transition to `HALF_OPEN`, trial recovery to `CLOSED`. |
| `RoutingRateLimiterTest` | Rate Limiting | Token-bucket burst capacity, steady-state replenishment with virtual nano-clock, `DEGRADED_RATE_LIMITED` fallback on quota exhaustion. |
| `ProviderFailureRecoveryTest` | End-to-End | Full lifecycle: provider fails → CB trips → trip saved as `DEGRADED` → provider recovers → recompute heals trip to `COMPLETED` with signed delta. |
| `BatchTripProcessorTest` | Scaling Level 2 | Memory-bounded chunking, over-sized batch guard, transient failure retries, permanent failure isolation. |
| `ParallelTripProcessorTest` | Scaling Level 3 | Multi-worker concurrent execution, deterministic input-order preservation, exception propagation. |
| `ConcurrentTripCalculationTest` | Thread Safety | 100 concurrent threads hammering shared `RoutingCache`, `RoutingCircuitBreaker`, and `TripDistanceRebuildingEngine` with zero race conditions. |
| `TenantIsolationTest` | Security | Verifies Tenant A (`client-alpha`) can never read, recompute, or view billing adjustments of Tenant B (`client-beta`). |
| `TripControllerE2ETest` | Integration | Full Spring MockMvc HTTP lifecycle including RBAC `403 Forbidden` checks, PII masking on errors, and batch calculation endpoints. |
| `ScaleBenchmarkTest` | Performance | Processes **10,000 trips × 1,500 pings (15,000,000 pings)** in `< 20s` with p99 latency `< 5ms` and `< 15 MB` heap growth. |

---

## 11. Configuration Reference

Default engine thresholds in `EngineConfig` and `application.yml`:

| Parameter | Default Value | Description |
| :--- | :--- | :--- |
| `ruleVersion` | `v1.2.0` | Deterministic algorithm rule version stamped on every trip & invoice. |
| `maxAccuracyMeters` | `50.0 m` | Maximum acceptable GPS horizontal dilution of precision / accuracy radius. |
| `teleportSpeedLimitKmh` | `120.0 km/h` | Maximum plausible urban cab speed before a ping or routed gap is rejected. |
| `shortGapSeconds` | `70 s` | Threshold below which straight-line Haversine distance is applied directly. |
| `parkedSpeedLimitKmh` | `5.0 km/h` | Implied aerial speed threshold for long gaps (`>= 70s`) classifying a cab as parked/idling. |
| `officeRadiusMeters` | `400.0 m` | Maximum distance from planned office for accepting actual trip-end coordinates. |
| `maxRoutingDistanceKm` | `100.0 km` | Sanity cap on single-gap driving distance returned by the routing provider. |
| `circuitFallbackMultiplier` | `1.4x` | Road-tortuosity multiplier applied to Haversine aerial distance when routing is degraded. |
| `includeNoShowAnchors` | `true` | Whether `NO_SHOW` milestones are anchored into the route timeline. |
| `snappingGridPrecision` | `4` (`0.0001°`) | Decimal places used to snap coordinates into spatial cache cells (`~11.1 meters`). |
| `billingDecimalPlaces` | `2` (`0.01 km`) | Final decimal rounding scale (`RoundingMode.HALF_UP`) for billed trip totals and deltas. |
| `engine.routing.rate-limit.permits-per-second` | `0` (unlimited) | Client-side outbound routing API rate limit (set `> 0` to enforce token bucket). |


