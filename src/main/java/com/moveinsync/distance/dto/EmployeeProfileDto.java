package com.moveinsync.distance.dto;

import java.time.Instant;

public class EmployeeProfileDto {
    private String employeeId;
    private Double homeLatitude;
    private Double homeLongitude;
    private Instant signInTime;

    public EmployeeProfileDto() {}

    public EmployeeProfileDto(String employeeId, Double homeLatitude, Double homeLongitude, Instant signInTime) {
        this.employeeId = employeeId;
        this.homeLatitude = homeLatitude;
        this.homeLongitude = homeLongitude;
        this.signInTime = signInTime;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Double getHomeLatitude() {
        return homeLatitude;
    }

    public void setHomeLatitude(Double homeLatitude) {
        this.homeLatitude = homeLatitude;
    }

    public Double getHomeLongitude() {
        return homeLongitude;
    }

    public void setHomeLongitude(Double homeLongitude) {
        this.homeLongitude = homeLongitude;
    }

    public Instant getSignInTime() {
        return signInTime;
    }

    public void setSignInTime(Instant signInTime) {
        this.signInTime = signInTime;
    }
}
