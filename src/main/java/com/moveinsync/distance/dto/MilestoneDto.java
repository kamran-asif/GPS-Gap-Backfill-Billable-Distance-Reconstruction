package com.moveinsync.distance.dto;

import java.time.Instant;

public class MilestoneDto {
    private String type; // TRIP_START, PICKUP, DROP, NO_SHOW, TRIP_END
    private String employeeId;
    private Double latitude;
    private Double longitude;
    private Instant timestamp;
    private String riderType; // PLANNED, AD_HOC
    private String status; // COMPLETED, NO_SHOW, CANCELLED

    public MilestoneDto() {}

    public MilestoneDto(String type, String employeeId, Double latitude, Double longitude, Instant timestamp, String riderType, String status) {
        this.type = type;
        this.employeeId = employeeId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.riderType = riderType;
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getRiderType() {
        return riderType;
    }

    public void setRiderType(String riderType) {
        this.riderType = riderType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
