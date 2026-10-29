package com.moveinsync.distance.domain;

public enum TripStatus {
    /**
     * Standard calculation completed successfully with high-confidence routing and GPS data.
     */
    COMPLETED,

    /**
     * Calculated in degraded mode due to routing provider failure / circuit breaker trip.
     * Scheduled for automatic recomputation upon provider recovery.
     */
    DEGRADED,

    /**
     * Recomputed following late pings or recovered provider.
     */
    RECOMPUTED
}
