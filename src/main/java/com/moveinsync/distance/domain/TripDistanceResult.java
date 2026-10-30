package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Deterministic, explainable trip distance result.
 */
public final class TripDistanceResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tripId;
    private final String tenantId;
    private final int version;
    private final double totalDistanceKm;
    private final TripStatus status;
    private final String ruleVersion;
    private final List<SegmentResult> segments;
    private final Map<DistanceRule, Double> distanceByRuleKm;
    private final Map<DistanceRule, Integer> countByRule;
    private final ProviderCallStats providerStats;
    private final int inputPingCount;
    private final int cleanedPingCount;
    private final int droppedTeleportCount;
    private final int droppedAccuracyCount;
    private final int anchorsInsertedCount;
    private final Instant calculatedAt;
    private final long processingTimeMs;

    public TripDistanceResult(String tripId,
                              String tenantId,
                              int version,
                              double totalDistanceKm,
                              TripStatus status,
                              String ruleVersion,
                              List<SegmentResult> segments,
                              Map<DistanceRule, Double> distanceByRuleKm,
                              Map<DistanceRule, Integer> countByRule,
                              ProviderCallStats providerStats,
                              int inputPingCount,
                              int cleanedPingCount,
                              int droppedTeleportCount,
                              int droppedAccuracyCount,
                              int anchorsInsertedCount,
                              Instant calculatedAt,
                              long processingTimeMs) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.version = version;
        this.totalDistanceKm = BigDecimal.valueOf(totalDistanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion must not be null");
        this.segments = segments != null ? List.copyOf(segments) : List.of();
        this.distanceByRuleKm = distanceByRuleKm != null ? Map.copyOf(distanceByRuleKm) : Map.of();
        this.countByRule = countByRule != null ? Map.copyOf(countByRule) : Map.of();
        this.providerStats = providerStats != null ? providerStats : ProviderCallStats.zero();
        this.inputPingCount = inputPingCount;
        this.cleanedPingCount = cleanedPingCount;
        this.droppedTeleportCount = droppedTeleportCount;
        this.droppedAccuracyCount = droppedAccuracyCount;
        this.anchorsInsertedCount = anchorsInsertedCount;
        this.calculatedAt = calculatedAt != null ? calculatedAt : Instant.now();
        this.processingTimeMs = processingTimeMs;
    }

    public String getTripId() {
        return tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getVersion() {
        return version;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public TripStatus getStatus() {
        return status;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public List<SegmentResult> getSegments() {
        return segments;
    }

    public Map<DistanceRule, Double> getDistanceByRuleKm() {
        return distanceByRuleKm;
    }

    public Map<DistanceRule, Integer> getCountByRule() {
        return countByRule;
    }

    public ProviderCallStats getProviderStats() {
        return providerStats;
    }

    public int getInputPingCount() {
        return inputPingCount;
    }

    public int getCleanedPingCount() {
        return cleanedPingCount;
    }

    public int getDroppedTeleportCount() {
        return droppedTeleportCount;
    }

    public int getDroppedAccuracyCount() {
        return droppedAccuracyCount;
    }

    public int getAnchorsInsertedCount() {
        return anchorsInsertedCount;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    @Override
    public String toString() {
        return "TripDistanceResult{" +
                "tripId='" + tripId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", version=" + version +
                ", totalDistanceKm=" + totalDistanceKm +
                ", status=" + status +
                ", ruleVersion='" + ruleVersion + '\'' +
                ", segments=" + segments.size() +
                ", providerStats=" + providerStats +
                '}';
    }
}
