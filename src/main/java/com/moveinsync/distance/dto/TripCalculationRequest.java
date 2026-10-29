package com.moveinsync.distance.dto;

import java.time.Instant;
import java.util.List;

public class TripCalculationRequest {
    private String tripId;
    private String tenantId;
    private List<GpsPingDto> rawPings;
    private List<MilestoneDto> milestones;
    private List<PlannedStopDto> plannedStops;
    private OfficeLocationDto officeLocation;
    private List<EmployeeProfileDto> employeeProfiles;
    private Instant tripStartTime;
    private Instant tripEndTime;
    private Double tripEndActualLatitude;
    private Double tripEndActualLongitude;
    private EngineConfigDto config;

    public TripCalculationRequest() {}

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<GpsPingDto> getRawPings() {
        return rawPings;
    }

    public void setRawPings(List<GpsPingDto> rawPings) {
        this.rawPings = rawPings;
    }

    public List<MilestoneDto> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<MilestoneDto> milestones) {
        this.milestones = milestones;
    }

    public List<PlannedStopDto> getPlannedStops() {
        return plannedStops;
    }

    public void setPlannedStops(List<PlannedStopDto> plannedStops) {
        this.plannedStops = plannedStops;
    }

    public OfficeLocationDto getOfficeLocation() {
        return officeLocation;
    }

    public void setOfficeLocation(OfficeLocationDto officeLocation) {
        this.officeLocation = officeLocation;
    }

    public List<EmployeeProfileDto> getEmployeeProfiles() {
        return employeeProfiles;
    }

    public void setEmployeeProfiles(List<EmployeeProfileDto> employeeProfiles) {
        this.employeeProfiles = employeeProfiles;
    }

    public Instant getTripStartTime() {
        return tripStartTime;
    }

    public void setTripStartTime(Instant tripStartTime) {
        this.tripStartTime = tripStartTime;
    }

    public Instant getTripEndTime() {
        return tripEndTime;
    }

    public void setTripEndTime(Instant tripEndTime) {
        this.tripEndTime = tripEndTime;
    }

    public Double getTripEndActualLatitude() {
        return tripEndActualLatitude;
    }

    public void setTripEndActualLatitude(Double tripEndActualLatitude) {
        this.tripEndActualLatitude = tripEndActualLatitude;
    }

    public Double getTripEndActualLongitude() {
        return tripEndActualLongitude;
    }

    public void setTripEndActualLongitude(Double tripEndActualLongitude) {
        this.tripEndActualLongitude = tripEndActualLongitude;
    }

    public EngineConfigDto getConfig() {
        return config;
    }

    public void setConfig(EngineConfigDto config) {
        this.config = config;
    }
}
