package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Signed delta generated when recomputing a closed/invoiced trip.
 * "When late pings arrive after billing, recompute and produce a signed delta (+1.8 km) instead of silently changing a closed invoice."
 */
public final class RecomputeDelta implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum AdjustmentType {
        CREDIT_NOTE, // vendor is owed more money (+ delta km)
        DEBIT_NOTE,  // vendor was over-paid (- delta km)
        NO_CHANGE    // delta is 0.00
    }

    private final String tripId;
    private final String tenantId;
    private final int previousVersion;
    private final int newVersion;
    private final double previousDistanceKm;
    private final double newDistanceKm;
    private final double deltaKm;
    private final AdjustmentType adjustmentType;
    private final String reason;
    private final Instant createdAt;

    public RecomputeDelta(String tripId,
                          String tenantId,
                          int previousVersion,
                          int newVersion,
                          double previousDistanceKm,
                          double newDistanceKm,
                          String reason,
                          Instant createdAt) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.previousVersion = previousVersion;
        this.newVersion = newVersion;
        this.previousDistanceKm = BigDecimal.valueOf(previousDistanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        this.newDistanceKm = BigDecimal.valueOf(newDistanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        this.deltaKm = BigDecimal.valueOf(this.newDistanceKm - this.previousDistanceKm).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (this.deltaKm > 0.0) {
            this.adjustmentType = AdjustmentType.CREDIT_NOTE;
        } else if (this.deltaKm < 0.0) {
            this.adjustmentType = AdjustmentType.DEBIT_NOTE;
        } else {
            this.adjustmentType = AdjustmentType.NO_CHANGE;
        }
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public String getTripId() {
        return tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getPreviousVersion() {
        return previousVersion;
    }

    public int getNewVersion() {
        return newVersion;
    }

    public double getPreviousDistanceKm() {
        return previousDistanceKm;
    }

    public double getNewDistanceKm() {
        return newDistanceKm;
    }

    public double getDeltaKm() {
        return deltaKm;
    }

    public String getFormattedDelta() {
        if (deltaKm > 0.0) {
            return "+" + String.format(java.util.Locale.US, "%.2f", deltaKm) + " km";
        } else {
            return String.format(java.util.Locale.US, "%.2f", deltaKm) + " km";
        }
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "RecomputeDelta{trip=" + tripId + " (v" + previousVersion + " -> v" + newVersion + "): " +
                getFormattedDelta() + " [" + adjustmentType + "] - " + reason + "}";
    }
}
