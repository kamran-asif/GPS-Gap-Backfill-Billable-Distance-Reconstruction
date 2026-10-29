package com.moveinsync.distance.dto;

import java.time.Instant;

public class RecomputeResponse {
    private String tripId;
    private String tenantId;
    private int previousVersion;
    private int newVersion;
    private double previousDistanceKm;
    private double newDistanceKm;
    private double deltaKm;
    private String formattedDelta;
    private String adjustmentType;
    private String reason;
    private Instant calculatedAt;
    private TripCalculationResponse newTripResult;

    public RecomputeResponse() {}

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

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public TripCalculationResponse getNewTripResult() {
        return newTripResult;
    }

    public void setNewTripResult(TripCalculationResponse newTripResult) {
        this.newTripResult = newTripResult;
    }
}
