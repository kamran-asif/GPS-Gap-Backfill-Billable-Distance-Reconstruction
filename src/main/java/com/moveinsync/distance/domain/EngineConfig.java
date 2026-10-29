package com.moveinsync.distance.domain;

import java.io.Serializable;

/**
 * Deterministic engine configuration specifying versioned thresholds and flags.
 * "The rule order must be deterministic and versioned. Two runs that disagree by 0.1 km are a billing bug."
 */
public final class EngineConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_RULE_VERSION = "v1.2.0";
    public static final double DEFAULT_MAX_ACCURACY_METERS = 50.0;
    public static final double DEFAULT_TELEPORT_SPEED_LIMIT_KMH = 120.0;
    public static final long DEFAULT_SHORT_GAP_SECONDS = 70;
    public static final double DEFAULT_PARKED_SPEED_LIMIT_KMH = 5.0;
    public static final double DEFAULT_OFFICE_RADIUS_METERS = 400.0;
    public static final double DEFAULT_MAX_ROUTING_DISTANCE_KM = 100.0;
    public static final double DEFAULT_CIRCUIT_FALLBACK_MULTIPLIER = 1.4;
    public static final boolean DEFAULT_INCLUDE_NO_SHOW_ANCHORS = true;
    public static final int DEFAULT_SNAPPING_GRID_PRECISION = 4;
    public static final int DEFAULT_BILLING_DECIMAL_PLACES = 2;

    private final String ruleVersion;
    private final double maxAccuracyMeters;
    private final double teleportSpeedLimitKmh;
    private final long shortGapSeconds;
    private final double parkedSpeedLimitKmh;
    private final double officeRadiusMeters;
    private final double maxRoutingDistanceKm;
    private final double circuitFallbackMultiplier;
    private final boolean includeNoShowAnchors;
    private final int snappingGridPrecision;
    private final int billingDecimalPlaces;

    public EngineConfig(String ruleVersion,
                        double maxAccuracyMeters,
                        double teleportSpeedLimitKmh,
                        long shortGapSeconds,
                        double parkedSpeedLimitKmh,
                        double officeRadiusMeters,
                        double maxRoutingDistanceKm,
                        double circuitFallbackMultiplier,
                        boolean includeNoShowAnchors,
                        int snappingGridPrecision,
                        int billingDecimalPlaces) {
        this.ruleVersion = ruleVersion != null ? ruleVersion : DEFAULT_RULE_VERSION;
        this.maxAccuracyMeters = maxAccuracyMeters > 0 ? maxAccuracyMeters : DEFAULT_MAX_ACCURACY_METERS;
        this.teleportSpeedLimitKmh = teleportSpeedLimitKmh > 0 ? teleportSpeedLimitKmh : DEFAULT_TELEPORT_SPEED_LIMIT_KMH;
        this.shortGapSeconds = shortGapSeconds > 0 ? shortGapSeconds : DEFAULT_SHORT_GAP_SECONDS;
        this.parkedSpeedLimitKmh = parkedSpeedLimitKmh >= 0 ? parkedSpeedLimitKmh : DEFAULT_PARKED_SPEED_LIMIT_KMH;
        this.officeRadiusMeters = officeRadiusMeters > 0 ? officeRadiusMeters : DEFAULT_OFFICE_RADIUS_METERS;
        this.maxRoutingDistanceKm = maxRoutingDistanceKm > 0 ? maxRoutingDistanceKm : DEFAULT_MAX_ROUTING_DISTANCE_KM;
        this.circuitFallbackMultiplier = circuitFallbackMultiplier > 0 ? circuitFallbackMultiplier : DEFAULT_CIRCUIT_FALLBACK_MULTIPLIER;
        this.includeNoShowAnchors = includeNoShowAnchors;
        this.snappingGridPrecision = snappingGridPrecision;
        this.billingDecimalPlaces = billingDecimalPlaces;
    }

    public static EngineConfig defaultConfig() {
        return new EngineConfig(
                DEFAULT_RULE_VERSION,
                DEFAULT_MAX_ACCURACY_METERS,
                DEFAULT_TELEPORT_SPEED_LIMIT_KMH,
                DEFAULT_SHORT_GAP_SECONDS,
                DEFAULT_PARKED_SPEED_LIMIT_KMH,
                DEFAULT_OFFICE_RADIUS_METERS,
                DEFAULT_MAX_ROUTING_DISTANCE_KM,
                DEFAULT_CIRCUIT_FALLBACK_MULTIPLIER,
                DEFAULT_INCLUDE_NO_SHOW_ANCHORS,
                DEFAULT_SNAPPING_GRID_PRECISION,
                DEFAULT_BILLING_DECIMAL_PLACES
        );
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public double getTeleportSpeedLimitKmh() {
        return teleportSpeedLimitKmh;
    }

    public long getShortGapSeconds() {
        return shortGapSeconds;
    }

    public double getParkedSpeedLimitKmh() {
        return parkedSpeedLimitKmh;
    }

    public double getOfficeRadiusMeters() {
        return officeRadiusMeters;
    }

    public double getMaxRoutingDistanceKm() {
        return maxRoutingDistanceKm;
    }

    public double getCircuitFallbackMultiplier() {
        return circuitFallbackMultiplier;
    }

    public boolean isIncludeNoShowAnchors() {
        return includeNoShowAnchors;
    }

    public int getSnappingGridPrecision() {
        return snappingGridPrecision;
    }

    public int getBillingDecimalPlaces() {
        return billingDecimalPlaces;
    }
}
