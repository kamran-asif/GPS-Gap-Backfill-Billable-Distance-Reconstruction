package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable Geographic Coordinate (Latitude, Longitude) in WGS84.
 * Includes validated bounds, high-precision Haversine distance, and grid-cell snapping for caching.
 */
public final class Coordinate implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final double EARTH_RADIUS_KM = 6371.0088; // IUGG recommended mean Earth radius

    private final double latitude;
    private final double longitude;

    public Coordinate(double latitude, double longitude) {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and +90 degrees. Received: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and +180 degrees. Received: " + longitude);
        }
        this.latitude = roundCoordinate(latitude);
        this.longitude = roundCoordinate(longitude);
    }

    public static Coordinate of(double latitude, double longitude) {
        return new Coordinate(latitude, longitude);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    /**
     * Calculates the great-circle Haversine distance to another coordinate in kilometers.
     * Uses numerically stable formula and doubles.
     */
    public double haversineDistanceKm(Coordinate other) {
        Objects.requireNonNull(other, "Target coordinate must not be null");
        if (this.equals(other)) {
            return 0.0;
        }

        double lat1Rad = Math.toRadians(this.latitude);
        double lon1Rad = Math.toRadians(this.longitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double lon2Rad = Math.toRadians(other.longitude);

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double sinHalfLat = Math.sin(deltaLat / 2.0);
        double sinHalfLon = Math.sin(deltaLon / 2.0);

        double a = sinHalfLat * sinHalfLat + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfLon * sinHalfLon;
        // Clamp a to [0.0, 1.0] to prevent NaN due to floating-point rounding
        a = Math.min(1.0, Math.max(0.0, a));
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Returns the Haversine distance in meters.
     */
    public double haversineDistanceMeters(Coordinate other) {
        return haversineDistanceKm(other) * 1000.0;
    }

    /**
     * Snaps coordinates to a discrete spatial grid cell for cache keys.
     * Precision of 4 decimal places gives ~11 meters resolution.
     * Precision of 3 decimal places gives ~110 meters resolution.
     */
    public String toGridCell(int decimalPlaces) {
        BigDecimal lat = BigDecimal.valueOf(latitude).setScale(decimalPlaces, RoundingMode.HALF_UP);
        BigDecimal lon = BigDecimal.valueOf(longitude).setScale(decimalPlaces, RoundingMode.HALF_UP);
        return lat.toPlainString() + "," + lon.toPlainString();
    }

    private static double roundCoordinate(double val) {
        return BigDecimal.valueOf(val).setScale(7, RoundingMode.HALF_UP).doubleValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Coordinate that)) return false;
        return Double.compare(that.latitude, latitude) == 0 &&
               Double.compare(that.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "(" + latitude + ", " + longitude + ")";
    }
}
