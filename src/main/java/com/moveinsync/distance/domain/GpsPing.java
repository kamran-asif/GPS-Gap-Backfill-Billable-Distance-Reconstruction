package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Raw GPS ping emitted by cab device or driver phone.
 */
public final class GpsPing implements Serializable, Comparable<GpsPing> {
    private static final long serialVersionUID = 1L;

    private final Coordinate coordinate;
    private final Instant timestamp;
    private final Double accuracyMeters; // Horizontal accuracy (null or positive)

    public GpsPing(Coordinate coordinate, Instant timestamp, Double accuracyMeters) {
        this.coordinate = Objects.requireNonNull(coordinate, "Coordinate must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        this.accuracyMeters = accuracyMeters;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public boolean hasValidAccuracy(double maxAllowedMeters) {
        return accuracyMeters != null && !Double.isNaN(accuracyMeters) && accuracyMeters > 0 && accuracyMeters <= maxAllowedMeters;
    }

    @Override
    public int compareTo(GpsPing other) {
        return this.timestamp.compareTo(other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GpsPing gpsPing)) return false;
        return Objects.equals(coordinate, gpsPing.coordinate) &&
               Objects.equals(timestamp, gpsPing.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinate, timestamp);
    }

    @Override
    public String toString() {
        return "GpsPing{" + coordinate + " @ " + timestamp + ", acc=" + accuracyMeters + "m}";
    }
}
