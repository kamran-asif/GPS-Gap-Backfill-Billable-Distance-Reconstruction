package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Employee profile holding home address coordinate and roster/sign-in details.
 * Used as fallback anchor for ad-hoc riders.
 */
public final class EmployeeProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String employeeId;
    private final Coordinate homeLocation;
    private final Instant signInTime;

    public EmployeeProfile(String employeeId, Coordinate homeLocation, Instant signInTime) {
        this.employeeId = Objects.requireNonNull(employeeId, "EmployeeId must not be null");
        this.homeLocation = homeLocation;
        this.signInTime = signInTime;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public Coordinate getHomeLocation() {
        return homeLocation;
    }

    public Instant getSignInTime() {
        return signInTime;
    }

    public boolean hasValidHomeLocation() {
        return homeLocation != null &&
               !(homeLocation.getLatitude() == 0.0 && homeLocation.getLongitude() == 0.0);
    }

    @Override
    public String toString() {
        return "EmployeeProfile{id=" + employeeId + ", home=" + homeLocation + ", signIn=" + signInTime + "}";
    }
}
