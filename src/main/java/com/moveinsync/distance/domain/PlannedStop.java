package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Planned scheduled stop on a cab roster.
 */
public final class PlannedStop implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String employeeId;
    private final Coordinate coordinate;
    private final Instant plannedTime;
    private final MilestoneType stopType; // PICKUP or DROP

    public PlannedStop(String employeeId, Coordinate coordinate, Instant plannedTime, MilestoneType stopType) {
        this.employeeId = Objects.requireNonNull(employeeId, "EmployeeId must not be null");
        this.coordinate = Objects.requireNonNull(coordinate, "Planned coordinate must not be null");
        this.plannedTime = Objects.requireNonNull(plannedTime, "Planned time must not be null");
        this.stopType = Objects.requireNonNull(stopType, "StopType must not be null");
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public Instant getPlannedTime() {
        return plannedTime;
    }

    public MilestoneType getStopType() {
        return stopType;
    }

    @Override
    public String toString() {
        return "PlannedStop{" + stopType + ", emp=" + employeeId + ", " + coordinate + " @ " + plannedTime + "}";
    }
}
