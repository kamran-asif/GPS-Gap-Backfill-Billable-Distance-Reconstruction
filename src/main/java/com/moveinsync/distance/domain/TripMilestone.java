package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Business milestone event (trip start, employee pickup, employee drop, no-show, trip end).
 */
public final class TripMilestone implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MilestoneType type;
    private final String employeeId; // Nullable for trip start/end
    private final Coordinate coordinate; // Actual reported coordinate (optional/nullable)
    private final Instant timestamp;
    private final RiderType riderType; // PLANNED or AD_HOC
    private final MilestoneStatus status; // COMPLETED, NO_SHOW, CANCELLED

    public TripMilestone(MilestoneType type,
                         String employeeId,
                         Coordinate coordinate,
                         Instant timestamp,
                         RiderType riderType,
                         MilestoneStatus status) {
        this.type = Objects.requireNonNull(type, "MilestoneType must not be null");
        this.employeeId = employeeId;
        this.coordinate = coordinate;
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp must not be null");
        this.riderType = riderType != null ? riderType : RiderType.PLANNED;
        this.status = status != null ? status : MilestoneStatus.COMPLETED;
    }

    public MilestoneType getType() {
        return type;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public boolean hasValidCoordinate() {
        return coordinate != null &&
               !(coordinate.getLatitude() == 0.0 && coordinate.getLongitude() == 0.0);
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public RiderType getRiderType() {
        return riderType;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "TripMilestone{" + type + ", emp=" + employeeId + ", coord=" + coordinate +
               ", time=" + timestamp + ", rider=" + riderType + ", status=" + status + "}";
    }
}
