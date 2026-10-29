package com.moveinsync.distance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_adjustments", indexes = {
        @Index(name = "idx_adjustment_trip_tenant", columnList = "trip_id, tenant_id")
})
public class TripAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false)
    private String tripId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "previous_version", nullable = false)
    private int previousVersion;

    @Column(name = "new_version", nullable = false)
    private int newVersion;

    @Column(name = "previous_distance_km", nullable = false)
    private double previousDistanceKm;

    @Column(name = "new_distance_km", nullable = false)
    private double newDistanceKm;

    @Column(name = "delta_km", nullable = false)
    private double deltaKm;

    @Column(name = "formatted_delta", nullable = false)
    private String formattedDelta;

    @Column(name = "adjustment_type", nullable = false)
    private String adjustmentType;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public TripAdjustmentEntity() {}

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

    public int getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(int previousVersion) {
        this.previousVersion = previousVersion;
    }

    public int getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(int newVersion) {
        this.newVersion = newVersion;
    }

    public double getPreviousDistanceKm() {
        return previousDistanceKm;
    }

    public void setPreviousDistanceKm(double previousDistanceKm) {
        this.previousDistanceKm = previousDistanceKm;
    }

    public double getNewDistanceKm() {
        return newDistanceKm;
    }

    public void setNewDistanceKm(double newDistanceKm) {
        this.newDistanceKm = newDistanceKm;
    }

    public double getDeltaKm() {
        return deltaKm;
    }

    public void setDeltaKm(double deltaKm) {
        this.deltaKm = deltaKm;
    }

    public String getFormattedDelta() {
        return formattedDelta;
    }

    public void setFormattedDelta(String formattedDelta) {
        this.formattedDelta = formattedDelta;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(String adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
