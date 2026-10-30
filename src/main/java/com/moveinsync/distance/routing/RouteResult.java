package com.moveinsync.distance.routing;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RouteResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double distanceKm;
    private final long durationSeconds;
    private final RouteStatus status;
    private final String errorMessage;

    public RouteResult(double distanceKm, long durationSeconds, RouteStatus status, String errorMessage) {
        this.distanceKm = BigDecimal.valueOf(Math.max(0.0, distanceKm)).setScale(3, RoundingMode.HALF_UP).doubleValue();
        this.durationSeconds = durationSeconds;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static RouteResult success(double distanceKm, long durationSeconds) {
        return new RouteResult(distanceKm, durationSeconds, RouteStatus.SUCCESS, null);
    }

    public static RouteResult cacheHit(double distanceKm, long durationSeconds) {
        return new RouteResult(distanceKm, durationSeconds, RouteStatus.CACHE_HIT, null);
    }

    public static RouteResult circuitOpen() {
        return new RouteResult(0.0, 0, RouteStatus.CIRCUIT_OPEN, "Routing circuit breaker is OPEN");
    }

    public static RouteResult providerError(String message) {
        return new RouteResult(0.0, 0, RouteStatus.PROVIDER_ERROR, message);
    }

    public static RouteResult rateLimited(String message) {
        return new RouteResult(0.0, 0, RouteStatus.RATE_LIMITED, message);
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isSuccess() {
        return status == RouteStatus.SUCCESS || status == RouteStatus.CACHE_HIT;
    }

    @Override
    public String toString() {
        return "RouteResult{" + status + ": " + distanceKm + " km (" + durationSeconds + "s)}";
    }
}
