package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Routing provider performance, cache efficiency, and API cost statistics for a trip calculation.
 */
public final class ProviderCallStats implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final double DEFAULT_COST_PER_CALL_USD = 0.005; // $5 per 1,000 calls (typical Google/Here maps API tier)

    private final int callsAttempted;
    private final int cacheHits;
    private final int cacheMisses;
    private final int callsFailed;
    private final double costPerCallUsd;
    private final double totalCostUsd;
    private final double costSavedUsd;

    public ProviderCallStats(int callsAttempted, int cacheHits, int cacheMisses, int callsFailed, double costPerCallUsd) {
        this.callsAttempted = callsAttempted;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
        this.callsFailed = callsFailed;
        this.costPerCallUsd = costPerCallUsd;
        this.totalCostUsd = round(cacheMisses * costPerCallUsd, 4);
        this.costSavedUsd = round(cacheHits * costPerCallUsd, 4);
    }

    public static ProviderCallStats of(int cacheHits, int cacheMisses, int callsFailed) {
        int total = cacheHits + cacheMisses;
        return new ProviderCallStats(total, cacheHits, cacheMisses, callsFailed, DEFAULT_COST_PER_CALL_USD);
    }

    public static ProviderCallStats zero() {
        return new ProviderCallStats(0, 0, 0, 0, DEFAULT_COST_PER_CALL_USD);
    }

    private static double round(double val, int scale) {
        return BigDecimal.valueOf(val).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public int getCallsAttempted() {
        return callsAttempted;
    }

    public int getCacheHits() {
        return cacheHits;
    }

    public int getCacheMisses() {
        return cacheMisses;
    }

    public int getCallsFailed() {
        return callsFailed;
    }

    public double getCostPerCallUsd() {
        return costPerCallUsd;
    }

    public double getTotalCostUsd() {
        return totalCostUsd;
    }

    public double getCostSavedUsd() {
        return costSavedUsd;
    }

    public double getCacheHitRatePercentage() {
        if (callsAttempted == 0) return 0.0;
        return round(((double) cacheHits / callsAttempted) * 100.0, 1);
    }

    @Override
    public String toString() {
        return "ProviderCallStats{" +
                "attempted=" + callsAttempted +
                ", hits=" + cacheHits +
                ", misses=" + cacheMisses +
                ", failed=" + callsFailed +
                ", hitRate=" + getCacheHitRatePercentage() + "%" +
                ", cost=$" + totalCostUsd +
                ", saved=$" + costSavedUsd +
                '}';
    }
}
