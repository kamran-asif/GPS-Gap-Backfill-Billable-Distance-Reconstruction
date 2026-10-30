package com.moveinsync.distance.domain;

/**
 * Audit trail rule applied to calculate distance for a specific segment.
 * Ensures 100% explainable kilometers for vendor dispute resolution.
 */
public enum DistanceRule {
    /**
     * Gap < 70s at plausible speed (<= 120 km/h). Straight-line (Haversine) applied.
     */
    SHORT_GAP_AERIAL,

    /**
     * Long gap (>= 70s) with implied speed < 5 km/h (cab was idling/parked). Straight-line applied.
     */
    PARKED,

    /**
     * Real gap with driving distance fetched from routing provider within speed and distance thresholds.
     */
    ROUTED,

    /**
     * Provider returned driving distance but implied speed > 120 km/h and 1.4 * aerial > 120 km/h. Rejected.
     */
    REJECTED_SPEED,

    /**
     * Provider returned driving distance > 100 km for a single trip segment. Rejected.
     */
    REJECTED_MAX_DISTANCE,

    /**
     * Routing provider circuit breaker is open. Fallback 1.4 * aerial applied. Trip marked DEGRADED.
     */
    DEGRADED_CIRCUIT_OPEN,

    /**
     * Routing provider returned an error or timed out. Fallback 1.4 * aerial applied. Trip marked DEGRADED.
     */
    DEGRADED_PROVIDER_ERROR,

    /**
     * Outbound routing call was rejected by the client-side rate limiter to protect the provider quota.
     * Fallback 1.4 * aerial applied. Trip marked DEGRADED.
     */
    DEGRADED_RATE_LIMITED,

    /**
     * Generic straight-line fallback.
     */
    FALLBACK_AERIAL
}
