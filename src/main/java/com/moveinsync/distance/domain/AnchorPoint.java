package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Anchor point representing a chronological milestone or valid GPS fix in the trajectory.
 */
public final class AnchorPoint implements Serializable, Comparable<AnchorPoint> {
    private static final long serialVersionUID = 1L;

    private final Coordinate coordinate;
    private final Instant timestamp;
    private final AnchorType type;
    private final String sourceDescription; // e.g. "GPS_FIX", "EMPLOYEE_ACTUAL", "EMPLOYEE_HOME_FALLBACK", "PLANNED_STOP_FALLBACK", "OFFICE_ACTUAL", "OFFICE_PLANNED"

    public AnchorPoint(Coordinate coordinate, Instant timestamp, AnchorType type, String sourceDescription) {
        this.coordinate = Objects.requireNonNull(coordinate, "Coordinate must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        this.type = Objects.requireNonNull(type, "AnchorType must not be null");
        this.sourceDescription = sourceDescription != null ? sourceDescription : type.name();
    }

    public static AnchorPoint fromGps(GpsPing ping) {
        return new AnchorPoint(ping.getCoordinate(), ping.getTimestamp(), AnchorType.GPS_PING, "GPS_FIX");
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public AnchorType getType() {
        return type;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public boolean isBusinessAnchor() {
        return type != AnchorType.GPS_PING;
    }

    @Override
    public int compareTo(AnchorPoint other) {
        int cmp = this.timestamp.compareTo(other.timestamp);
        if (cmp != 0) return cmp;
        // Business anchors take priority over raw GPS pings at exact same timestamp
        return Integer.compare(other.type.ordinal(), this.type.ordinal());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnchorPoint that)) return false;
        return Objects.equals(coordinate, that.coordinate) &&
               Objects.equals(timestamp, that.timestamp) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(coordinate, timestamp, type);
    }

    @Override
    public String toString() {
        return "AnchorPoint{" + type + " (" + sourceDescription + ") @ " + coordinate + " " + timestamp + "}";
    }
}
