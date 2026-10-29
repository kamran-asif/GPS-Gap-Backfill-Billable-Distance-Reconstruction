package com.moveinsync.distance.dto;

import com.moveinsync.distance.domain.DistanceRule;
import com.moveinsync.distance.domain.ProviderCallStats;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class TripCalculationResponse {
    private String tripId;
    private String tenantId;
    private int version;
    private double totalDistanceKm;
    private String status;
    private String ruleVersion;
    private List<SegmentResponseDto> segments;
    private Map<DistanceRule, Double> distanceByRuleKm;
    private Map<DistanceRule, Integer> countByRule;
    private ProviderCallStats providerStats;
    private int inputPingCount;
    private int cleanedPingCount;
    private int droppedTeleportCount;
    private int droppedAccuracyCount;
    private int anchorsInsertedCount;
    private Instant calculatedAt;

    public TripCalculationResponse() {}

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public List<SegmentResponseDto> getSegments() {
        return segments;
    }

    public void setSegments(List<SegmentResponseDto> segments) {
        this.segments = segments;
    }

    public Map<DistanceRule, Double> getDistanceByRuleKm() {
        return distanceByRuleKm;
    }

    public void setDistanceByRuleKm(Map<DistanceRule, Double> distanceByRuleKm) {
        this.distanceByRuleKm = distanceByRuleKm;
    }

    public Map<DistanceRule, Integer> getCountByRule() {
        return countByRule;
    }

    public void setCountByRule(Map<DistanceRule, Integer> countByRule) {
        this.countByRule = countByRule;
    }

    public ProviderCallStats getProviderStats() {
        return providerStats;
    }

    public void setProviderStats(ProviderCallStats providerStats) {
        this.providerStats = providerStats;
    }

    public int getInputPingCount() {
        return inputPingCount;
    }

    public void setInputPingCount(int inputPingCount) {
        this.inputPingCount = inputPingCount;
    }

    public int getCleanedPingCount() {
        return cleanedPingCount;
    }

    public void setCleanedPingCount(int cleanedPingCount) {
        this.cleanedPingCount = cleanedPingCount;
    }

    public int getDroppedTeleportCount() {
        return droppedTeleportCount;
    }

    public void setDroppedTeleportCount(int droppedTeleportCount) {
        this.droppedTeleportCount = droppedTeleportCount;
    }

    public int getDroppedAccuracyCount() {
        return droppedAccuracyCount;
    }

    public void setDroppedAccuracyCount(int droppedAccuracyCount) {
        this.droppedAccuracyCount = droppedAccuracyCount;
    }

    public int getAnchorsInsertedCount() {
        return anchorsInsertedCount;
    }

    public void setAnchorsInsertedCount(int anchorsInsertedCount) {
        this.anchorsInsertedCount = anchorsInsertedCount;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
