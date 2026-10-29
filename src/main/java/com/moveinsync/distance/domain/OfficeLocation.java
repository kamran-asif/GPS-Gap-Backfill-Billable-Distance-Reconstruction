package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * MoveInSync client office location.
 */
public final class OfficeLocation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String officeId;
    private final String name;
    private final Coordinate coordinate;

    public OfficeLocation(String officeId, String name, Coordinate coordinate) {
        this.officeId = Objects.requireNonNull(officeId, "OfficeId must not be null");
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.coordinate = Objects.requireNonNull(coordinate, "Office coordinate must not be null");
    }

    public String getOfficeId() {
        return officeId;
    }

    public String getName() {
        return name;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    @Override
    public String toString() {
        return "OfficeLocation{" + officeId + " - " + name + " @ " + coordinate + "}";
    }
}
