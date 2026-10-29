package com.moveinsync.distance.dto;

import java.time.Instant;

public class PlannedStopDto {
    private String employeeId;
    private double latitude;
    private double longitude;
    private Instant plannedTime;
    private String stopType; // PICKUP or DROP

    public PlannedStopDto() {}

    public PlannedStopDto(String employeeId, double latitude, double longitude, Instant plannedTime, String stopType) {
        this.employeeId = employeeId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.plannedTime = plannedTime;
        this.stopType = stopType;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Instant getPlannedTime() {
        return plannedTime;
    }

    public void setPlannedTime(Instant plannedTime) {
        this.plannedTime = plannedTime;
    }

    public String getStopType() {
        return stopType;
    }

    public void setStopType(String stopType) {
        this.stopType = stopType;
    }
}
