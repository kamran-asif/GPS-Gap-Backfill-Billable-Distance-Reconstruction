package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Stage II: Patch the Gaps with Anchor Points
 *
 * Rules:
 * 1. Employee Anchors: for each employee who travelled, add actual pickup/drop if valid.
 *    If invalid, fall back in defined order:
 *    - Ad-hoc rider: profile home location & sign-in time
 *    - Planned rider: planned location & planned time
 * 2. No-Show Anchors: behind configuration flag, add no-show location (or planned if invalid).
 * 3. Office Anchor: use actual trip-end point ONLY IF within 400 m of planned office.
 *    Otherwise use planned office location with trip-end time.
 */
public final class AnchorPatchEngine {
    private static final Logger log = LoggerFactory.getLogger(AnchorPatchEngine.class);

    private final EngineConfig config;

    public AnchorPatchEngine(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "EngineConfig must not be null");
    }

    public List<AnchorPoint> patchAndMerge(List<GpsPing> cleanedPings, TripEvidence evidence) {
        List<AnchorPoint> anchors = new ArrayList<>();

        // 1. Add all cleaned GPS pings
        if (cleanedPings != null) {
            for (GpsPing ping : cleanedPings) {
                anchors.add(AnchorPoint.fromGps(ping));
            }
        }

        // Index planned stops by employeeId and milestone type for fast lookup
        Map<String, PlannedStop> plannedStopMap = new HashMap<>();
        if (evidence.getPlannedStops() != null) {
            for (PlannedStop ps : evidence.getPlannedStops()) {
                String key = ps.getEmployeeId() + "_" + ps.getStopType().name();
                plannedStopMap.put(key, ps);
                plannedStopMap.putIfAbsent(ps.getEmployeeId(), ps); // fallback key
            }
        }

        Map<String, EmployeeProfile> profileMap = evidence.getEmployeeProfiles() != null ?
                evidence.getEmployeeProfiles() : Map.of();

        // 2. Employee Anchors & No-Show Anchors from Milestones
        if (evidence.getMilestones() != null) {
            for (TripMilestone milestone : evidence.getMilestones()) {
                if (milestone.getType() == MilestoneType.TRIP_START) {
                    if (milestone.hasValidCoordinate()) {
                        anchors.add(new AnchorPoint(
                                milestone.getCoordinate(),
                                milestone.getTimestamp(),
                                AnchorType.TRIP_START,
                                "TRIP_START_ACTUAL"
                        ));
                    }
                    continue;
                }

                if (milestone.getType() == MilestoneType.TRIP_END) {
                    continue; // Handled under Office Anchor
                }

                boolean isNoShow = milestone.getType() == MilestoneType.NO_SHOW ||
                        milestone.getStatus() == MilestoneStatus.NO_SHOW;

                if (isNoShow) {
                    // No-Show Anchor handling behind flag
                    if (config.isIncludeNoShowAnchors()) {
                        AnchorPoint noShowAnchor = resolveNoShowAnchor(milestone, plannedStopMap, profileMap);
                        if (noShowAnchor != null) {
                            anchors.add(noShowAnchor);
                            log.debug("Added No-Show anchor: {}", noShowAnchor);
                        }
                    }
                } else if (milestone.getStatus() == MilestoneStatus.COMPLETED) {
                    // Employee who travelled (Pickup or Drop)
                    AnchorPoint employeeAnchor = resolveEmployeeAnchor(milestone, plannedStopMap, profileMap);
                    if (employeeAnchor != null) {
                        anchors.add(employeeAnchor);
                        log.debug("Added Employee anchor: {}", employeeAnchor);
                    }
                }
            }
        }

        // 3. Office Anchor
        if (evidence.getOfficeLocation() != null) {
            AnchorPoint officeAnchor = resolveOfficeAnchor(evidence);
            if (officeAnchor != null) {
                anchors.add(officeAnchor);
                log.debug("Added Office anchor: {}", officeAnchor);
            }
        }

        // 4. Chronologically sort and deduplicate coincident points
        return sortAndDeduplicate(anchors);
    }

    private AnchorPoint resolveEmployeeAnchor(TripMilestone milestone,
                                               Map<String, PlannedStop> plannedStops,
                                               Map<String, EmployeeProfile> profiles) {
        AnchorType anchorType = (milestone.getType() == MilestoneType.PICKUP) ?
                AnchorType.EMPLOYEE_PICKUP : AnchorType.EMPLOYEE_DROP;

        // Actual pickup/drop point if valid
        if (milestone.hasValidCoordinate()) {
            return new AnchorPoint(milestone.getCoordinate(), milestone.getTimestamp(), anchorType, "EMPLOYEE_ACTUAL");
        }

        // Fallback in defined order:
        // Ad-hoc rider: profile home location and sign-in time
        if (milestone.getRiderType() == RiderType.AD_HOC) {
            EmployeeProfile profile = profiles.get(milestone.getEmployeeId());
            if (profile != null && profile.hasValidHomeLocation()) {
                Instant time = profile.getSignInTime() != null ? profile.getSignInTime() : milestone.getTimestamp();
                return new AnchorPoint(profile.getHomeLocation(), time, anchorType, "EMPLOYEE_HOME_FALLBACK_ADHOC");
            }
            // Fallback to planned stop if profile missing
            PlannedStop plannedStop = findPlannedStop(plannedStops, milestone);
            if (plannedStop != null) {
                return new AnchorPoint(plannedStop.getCoordinate(), plannedStop.getPlannedTime(), anchorType, "PLANNED_STOP_FALLBACK");
            }
        } else {
            // Planned rider: planned location and planned time
            PlannedStop plannedStop = findPlannedStop(plannedStops, milestone);
            if (plannedStop != null) {
                return new AnchorPoint(plannedStop.getCoordinate(), plannedStop.getPlannedTime(), anchorType, "PLANNED_STOP_FALLBACK");
            }
            // Fallback to profile home location if planned stop missing
            EmployeeProfile profile = profiles.get(milestone.getEmployeeId());
            if (profile != null && profile.hasValidHomeLocation()) {
                Instant time = profile.getSignInTime() != null ? profile.getSignInTime() : milestone.getTimestamp();
                return new AnchorPoint(profile.getHomeLocation(), time, anchorType, "EMPLOYEE_HOME_FALLBACK");
            }
        }

        return null;
    }

    private AnchorPoint resolveNoShowAnchor(TripMilestone milestone,
                                             Map<String, PlannedStop> plannedStops,
                                             Map<String, EmployeeProfile> profiles) {
        if (milestone.hasValidCoordinate()) {
            return new AnchorPoint(milestone.getCoordinate(), milestone.getTimestamp(), AnchorType.EMPLOYEE_NO_SHOW, "EMPLOYEE_NO_SHOW_ACTUAL");
        }

        // Fallback to planned location
        PlannedStop plannedStop = findPlannedStop(plannedStops, milestone);
        if (plannedStop != null) {
            return new AnchorPoint(plannedStop.getCoordinate(), plannedStop.getPlannedTime(), AnchorType.EMPLOYEE_NO_SHOW, "NO_SHOW_PLANNED_FALLBACK");
        }

        // Fallback to home location
        EmployeeProfile profile = profiles.get(milestone.getEmployeeId());
        if (profile != null && profile.hasValidHomeLocation()) {
            Instant time = profile.getSignInTime() != null ? profile.getSignInTime() : milestone.getTimestamp();
            return new AnchorPoint(profile.getHomeLocation(), time, AnchorType.EMPLOYEE_NO_SHOW, "NO_SHOW_HOME_FALLBACK");
        }

        return null;
    }

    private AnchorPoint resolveOfficeAnchor(TripEvidence evidence) {
        OfficeLocation office = evidence.getOfficeLocation();
        Instant endTime = evidence.getTripEndTime() != null ? evidence.getTripEndTime() : Instant.now();
        Coordinate actualCoord = evidence.getTripEndActualCoordinate();

        // "use the actual trip-end point only if it is within 400 m of the planned office.
        // Otherwise use the planned office location with the trip-end time."
        if (actualCoord != null) {
            double distanceToOfficeMeters = office.getCoordinate().haversineDistanceMeters(actualCoord);
            if (distanceToOfficeMeters <= config.getOfficeRadiusMeters()) {
                return new AnchorPoint(actualCoord, endTime, AnchorType.OFFICE_END, "OFFICE_ACTUAL_WITHIN_400M");
            } else {
                log.info("Actual trip-end ({}) is {}m from office (> {}m limit). Falling back to planned office location.",
                        actualCoord, String.format(Locale.US, "%.1f", distanceToOfficeMeters), config.getOfficeRadiusMeters());
            }
        }

        return new AnchorPoint(office.getCoordinate(), endTime, AnchorType.OFFICE_END, "OFFICE_PLANNED_FALLBACK");
    }

    private PlannedStop findPlannedStop(Map<String, PlannedStop> plannedStops, TripMilestone milestone) {
        if (milestone.getEmployeeId() == null) return null;
        String key = milestone.getEmployeeId() + "_" + milestone.getType().name();
        PlannedStop stop = plannedStops.get(key);
        if (stop != null) return stop;
        return plannedStops.get(milestone.getEmployeeId());
    }

    private List<AnchorPoint> sortAndDeduplicate(List<AnchorPoint> anchors) {
        if (anchors.isEmpty()) return List.of();

        anchors.sort(Comparator.naturalOrder());

        List<AnchorPoint> result = new ArrayList<>(anchors.size());
        AnchorPoint prev = null;

        for (AnchorPoint current : anchors) {
            if (prev == null) {
                result.add(current);
                prev = current;
                continue;
            }

            // If same timestamp and nearly identical position (< 5m), keep the more authoritative anchor
            long deltaSec = Duration.between(prev.getTimestamp(), current.getTimestamp()).getSeconds();
            double distanceMeters = prev.getCoordinate().haversineDistanceMeters(current.getCoordinate());

            if (deltaSec == 0 && distanceMeters < 5.0) {
                // If current is a business anchor and prev was just a GPS ping, replace prev
                if (current.isBusinessAnchor() && !prev.isBusinessAnchor()) {
                    result.set(result.size() - 1, current);
                    prev = current;
                }
                // Otherwise skip current duplicate
                continue;
            }

            result.add(current);
            prev = current;
        }

        return result;
    }
}
