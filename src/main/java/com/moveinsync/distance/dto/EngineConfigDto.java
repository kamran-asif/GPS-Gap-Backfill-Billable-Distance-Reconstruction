package com.moveinsync.distance.dto;

import com.moveinsync.distance.domain.EngineConfig;

public class EngineConfigDto {
    private String ruleVersion;
    private Double maxAccuracyMeters;
    private Double teleportSpeedLimitKmh;
    private Long shortGapSeconds;
    private Double parkedSpeedLimitKmh;
    private Double officeRadiusMeters;
    private Double maxRoutingDistanceKm;
    private Double circuitFallbackMultiplier;
    private Boolean includeNoShowAnchors;
    private Integer snappingGridPrecision;
    private Integer billingDecimalPlaces;

    public EngineConfigDto() {}

    public EngineConfig toDomain() {
        return new EngineConfig(
                ruleVersion != null ? ruleVersion : EngineConfig.DEFAULT_RULE_VERSION,
                maxAccuracyMeters != null ? maxAccuracyMeters : EngineConfig.DEFAULT_MAX_ACCURACY_METERS,
                teleportSpeedLimitKmh != null ? teleportSpeedLimitKmh : EngineConfig.DEFAULT_TELEPORT_SPEED_LIMIT_KMH,
                shortGapSeconds != null ? shortGapSeconds : EngineConfig.DEFAULT_SHORT_GAP_SECONDS,
                parkedSpeedLimitKmh != null ? parkedSpeedLimitKmh : EngineConfig.DEFAULT_PARKED_SPEED_LIMIT_KMH,
                officeRadiusMeters != null ? officeRadiusMeters : EngineConfig.DEFAULT_OFFICE_RADIUS_METERS,
                maxRoutingDistanceKm != null ? maxRoutingDistanceKm : EngineConfig.DEFAULT_MAX_ROUTING_DISTANCE_KM,
                circuitFallbackMultiplier != null ? circuitFallbackMultiplier : EngineConfig.DEFAULT_CIRCUIT_FALLBACK_MULTIPLIER,
                includeNoShowAnchors != null ? includeNoShowAnchors : EngineConfig.DEFAULT_INCLUDE_NO_SHOW_ANCHORS,
                snappingGridPrecision != null ? snappingGridPrecision : EngineConfig.DEFAULT_SNAPPING_GRID_PRECISION,
                billingDecimalPlaces != null ? billingDecimalPlaces : EngineConfig.DEFAULT_BILLING_DECIMAL_PLACES
        );
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public Double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public void setMaxAccuracyMeters(Double maxAccuracyMeters) {
        this.maxAccuracyMeters = maxAccuracyMeters;
    }

    public Double getTeleportSpeedLimitKmh() {
        return teleportSpeedLimitKmh;
    }

    public void setTeleportSpeedLimitKmh(Double teleportSpeedLimitKmh) {
        this.teleportSpeedLimitKmh = teleportSpeedLimitKmh;
    }

    public Long getShortGapSeconds() {
        return shortGapSeconds;
    }

    public void setShortGapSeconds(Long shortGapSeconds) {
        this.shortGapSeconds = shortGapSeconds;
    }

    public Double getParkedSpeedLimitKmh() {
        return parkedSpeedLimitKmh;
    }

    public void setParkedSpeedLimitKmh(Double parkedSpeedLimitKmh) {
        this.parkedSpeedLimitKmh = parkedSpeedLimitKmh;
    }

    public Double getOfficeRadiusMeters() {
        return officeRadiusMeters;
    }

    public void setOfficeRadiusMeters(Double officeRadiusMeters) {
        this.officeRadiusMeters = officeRadiusMeters;
    }

    public Double getMaxRoutingDistanceKm() {
        return maxRoutingDistanceKm;
    }

    public void setMaxRoutingDistanceKm(Double maxRoutingDistanceKm) {
        this.maxRoutingDistanceKm = maxRoutingDistanceKm;
    }

    public Double getCircuitFallbackMultiplier() {
        return circuitFallbackMultiplier;
    }

    public void setCircuitFallbackMultiplier(Double circuitFallbackMultiplier) {
        this.circuitFallbackMultiplier = circuitFallbackMultiplier;
    }

    public Boolean getIncludeNoShowAnchors() {
        return includeNoShowAnchors;
    }

    public void setIncludeNoShowAnchors(Boolean includeNoShowAnchors) {
        this.includeNoShowAnchors = includeNoShowAnchors;
    }

    public Integer getSnappingGridPrecision() {
        return snappingGridPrecision;
    }

    public void setSnappingGridPrecision(Integer snappingGridPrecision) {
        this.snappingGridPrecision = snappingGridPrecision;
    }

    public Integer getBillingDecimalPlaces() {
        return billingDecimalPlaces;
    }

    public void setBillingDecimalPlaces(Integer billingDecimalPlaces) {
        this.billingDecimalPlaces = billingDecimalPlaces;
    }
}
