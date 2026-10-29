package com.moveinsync.distance.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trip_tenant_version", columnList = "trip_id, tenant_id, version", unique = true),
        @Index(name = "idx_trip_tenant", columnList = "tenant_id"),
        @Index(name = "idx_trip_status", columnList = "status, tenant_id")
})
public class TripEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private String tripId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private int version;

    @Column(name = "total_distance_km", nullable = false)
    private double totalDistanceKm;

    @Column(nullable = false)
    private String status; // COMPLETED, DEGRADED, RECOMPUTED

    @Column(name = "rule_version", nullable = false)
    private String ruleVersion;

    @Column(name = "raw_pings_count", nullable = false)
    private int rawPingsCount;

    @Column(name = "cleaned_pings_count", nullable = false)
    private int cleanedPingsCount;

    @Column(name = "teleport_dropped_count", nullable = false)
    private int teleportDroppedCount;

    @Column(name = "low_accuracy_dropped_count", nullable = false)
    private int lowAccuracyDroppedCount;

    @Column(name = "anchors_inserted_count", nullable = false)
    private int anchorsInsertedCount;

    @Column(name = "provider_calls_attempted", nullable = false)
    private int providerCallsAttempted;

    @Column(name = "provider_cache_hits", nullable = false)
    private int providerCacheHits;

    @Column(name = "provider_cache_misses", nullable = false)
    private int providerCacheMisses;

    @Column(name = "provider_calls_failed", nullable = false)
    private int providerCallsFailed;

    @Column(name = "provider_cost_usd", nullable = false)
    private double providerCostUsd;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_record_id")
    private List<TripSegmentEntity> segments = new ArrayList<>();

    public TripEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public int getRawPingsCount() {
        return rawPingsCount;
    }

    public void setRawPingsCount(int rawPingsCount) {
        this.rawPingsCount = rawPingsCount;
    }

    public int getCleanedPingsCount() {
        return cleanedPingsCount;
    }

    public void setCleanedPingsCount(int cleanedPingsCount) {
        this.cleanedPingsCount = cleanedPingsCount;
    }

    public int getTeleportDroppedCount() {
        return teleportDroppedCount;
    }

    public void setTeleportDroppedCount(int teleportDroppedCount) {
        this.teleportDroppedCount = teleportDroppedCount;
    }

    public int getLowAccuracyDroppedCount() {
        return lowAccuracyDroppedCount;
    }

    public void setLowAccuracyDroppedCount(int lowAccuracyDroppedCount) {
        this.lowAccuracyDroppedCount = lowAccuracyDroppedCount;
    }

    public int getAnchorsInsertedCount() {
        return anchorsInsertedCount;
    }

    public void setAnchorsInsertedCount(int anchorsInsertedCount) {
        this.anchorsInsertedCount = anchorsInsertedCount;
    }

    public int getProviderCallsAttempted() {
        return providerCallsAttempted;
    }

    public void setProviderCallsAttempted(int providerCallsAttempted) {
        this.providerCallsAttempted = providerCallsAttempted;
    }

    public int getProviderCacheHits() {
        return providerCacheHits;
    }

    public void setProviderCacheHits(int providerCacheHits) {
        this.providerCacheHits = providerCacheHits;
    }

    public int getProviderCacheMisses() {
        return providerCacheMisses;
    }

    public void setProviderCacheMisses(int providerCacheMisses) {
        this.providerCacheMisses = providerCacheMisses;
    }

    public int getProviderCallsFailed() {
        return providerCallsFailed;
    }

    public void setProviderCallsFailed(int providerCallsFailed) {
        this.providerCallsFailed = providerCallsFailed;
    }

    public double getProviderCostUsd() {
        return providerCostUsd;
    }

    public void setProviderCostUsd(double providerCostUsd) {
        this.providerCostUsd = providerCostUsd;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public List<TripSegmentEntity> getSegments() {
        return segments;
    }

    public void setSegments(List<TripSegmentEntity> segments) {
        this.segments = segments;
    }
}
