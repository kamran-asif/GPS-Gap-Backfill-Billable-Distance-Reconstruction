package com.moveinsync.distance.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * Raw evidence input payload for a trip distance rebuilding request.
 */
public final class TripEvidence implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String tripId;
    private final String tenantId;
    private final List<GpsPing> rawPings;
    private final List<TripMilestone> milestones;
    private final List<PlannedStop> plannedStops;
    private final OfficeLocation officeLocation;
    private final Map<String, EmployeeProfile> employeeProfiles;
    private final Instant tripStartTime;
    private final Instant tripEndTime;
    private final Coordinate tripEndActualCoordinate;
    private final EngineConfig config;

    public TripEvidence(String tripId,
                        String tenantId,
                        List<GpsPing> rawPings,
                        List<TripMilestone> milestones,
                        List<PlannedStop> plannedStops,
                        OfficeLocation officeLocation,
                        Map<String, EmployeeProfile> employeeProfiles,
                        Instant tripStartTime,
                        Instant tripEndTime,
                        Coordinate tripEndActualCoordinate,
                        EngineConfig config) {
        this.tripId = Objects.requireNonNull(tripId, "tripId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.rawPings = rawPings != null ? List.copyOf(rawPings) : List.of();
        this.milestones = milestones != null ? List.copyOf(milestones) : List.of();
        this.plannedStops = plannedStops != null ? List.copyOf(plannedStops) : List.of();
        this.officeLocation = officeLocation;
        this.employeeProfiles = employeeProfiles != null ? Map.copyOf(employeeProfiles) : Map.of();
        this.tripStartTime = tripStartTime;
        this.tripEndTime = tripEndTime;
        this.tripEndActualCoordinate = tripEndActualCoordinate;
        this.config = config != null ? config : EngineConfig.defaultConfig();
    }

    public String getTripId() {
        return tripId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public List<GpsPing> getRawPings() {
        return rawPings;
    }

    public List<TripMilestone> getMilestones() {
        return milestones;
    }

    public List<PlannedStop> getPlannedStops() {
        return plannedStops;
    }

    public OfficeLocation getOfficeLocation() {
        return officeLocation;
    }

    public Map<String, EmployeeProfile> getEmployeeProfiles() {
        return employeeProfiles;
    }

    public Instant getTripStartTime() {
        return tripStartTime;
    }

    public Instant getTripEndTime() {
        return tripEndTime;
    }

    public Coordinate getTripEndActualCoordinate() {
        return tripEndActualCoordinate;
    }

    public EngineConfig getConfig() {
        return config;
    }
}
