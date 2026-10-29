package com.moveinsync.distance.dto;

public class CircuitBreakerStatusResponse {
    private String state;
    private int totalTrips;
    private long cacheHits;
    private long cacheMisses;
    private long cachedRoutesCount;

    public CircuitBreakerStatusResponse() {}

    public CircuitBreakerStatusResponse(String state, int totalTrips, long cacheHits, long cacheMisses, long cachedRoutesCount) {
        this.state = state;
        this.totalTrips = totalTrips;
        this.cacheHits = cacheHits;
        this.cacheMisses = cacheMisses;
        this.cachedRoutesCount = cachedRoutesCount;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getTotalTrips() {
        return totalTrips;
    }

    public void setTotalTrips(int totalTrips) {
        this.totalTrips = totalTrips;
    }

    public long getCacheHits() {
        return cacheHits;
    }

    public void setCacheHits(long cacheHits) {
        this.cacheHits = cacheHits;
    }

    public long getCacheMisses() {
        return cacheMisses;
    }

    public void setCacheMisses(long cacheMisses) {
        this.cacheMisses = cacheMisses;
    }

    public long getCachedRoutesCount() {
        return cachedRoutesCount;
    }

    public void setCachedRoutesCount(long cachedRoutesCount) {
        this.cachedRoutesCount = cachedRoutesCount;
    }
}
