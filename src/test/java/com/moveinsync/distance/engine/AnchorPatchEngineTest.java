package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AnchorPatchEngineTest {

    private final EngineConfig config = EngineConfig.defaultConfig();
    private final AnchorPatchEngine patchEngine = new AnchorPatchEngine(config);

    @Test
    @DisplayName("Employee anchor uses actual pickup point when valid")
    void testEmployeeActualPointUsedWhenValid() {
        Coordinate actualPickup = Coordinate.of(12.9250, 77.6100);
        Instant pickupTime = Instant.parse("2026-10-29T10:15:00Z");

        TripMilestone milestone = new TripMilestone(
                MilestoneType.PICKUP, "EMP-101", actualPickup, pickupTime,
                RiderType.PLANNED, MilestoneStatus.COMPLETED);

        TripEvidence evidence = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(milestone), List.of(), null, Map.of(), null, null, null, config);

        List<AnchorPoint> points = patchEngine.patchAndMerge(List.of(), evidence);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getCoordinate()).isEqualTo(actualPickup);
        assertThat(points.get(0).getType()).isEqualTo(AnchorType.EMPLOYEE_PICKUP);
        assertThat(points.get(0).getSourceDescription()).isEqualTo("EMPLOYEE_ACTUAL");
    }

    @Test
    @DisplayName("Ad-hoc rider fallback: invalid actual coordinate falls back to profile home location and sign-in time")
    void testAdHocRiderFallbackToProfileHome() {
        Coordinate homeCoord = Coordinate.of(12.9100, 77.6200);
        Instant signInTime = Instant.parse("2026-10-29T10:10:00Z");
        Instant milestoneTime = Instant.parse("2026-10-29T10:20:00Z");

        // Milestone with null coordinates
        TripMilestone milestone = new TripMilestone(
                MilestoneType.PICKUP, "EMP-ADHOC-1", null, milestoneTime,
                RiderType.AD_HOC, MilestoneStatus.COMPLETED);

        EmployeeProfile profile = new EmployeeProfile("EMP-ADHOC-1", homeCoord, signInTime);

        TripEvidence evidence = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(milestone), List.of(), null,
                Map.of("EMP-ADHOC-1", profile), null, null, null, config);

        List<AnchorPoint> points = patchEngine.patchAndMerge(List.of(), evidence);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getCoordinate()).isEqualTo(homeCoord);
        assertThat(points.get(0).getTimestamp()).isEqualTo(signInTime);
        assertThat(points.get(0).getSourceDescription()).isEqualTo("EMPLOYEE_HOME_FALLBACK_ADHOC");
    }

    @Test
    @DisplayName("Planned rider fallback: invalid actual coordinate falls back to planned stop location and planned time")
    void testPlannedRiderFallbackToPlannedStop() {
        Coordinate plannedLocation = Coordinate.of(12.9400, 77.6500);
        Instant plannedTime = Instant.parse("2026-10-29T10:30:00Z");
        Instant milestoneTime = Instant.parse("2026-10-29T10:32:00Z");

        TripMilestone milestone = new TripMilestone(
                MilestoneType.PICKUP, "EMP-PLAN-1", Coordinate.of(0.0, 0.0), milestoneTime,
                RiderType.PLANNED, MilestoneStatus.COMPLETED);

        PlannedStop plannedStop = new PlannedStop("EMP-PLAN-1", plannedLocation, plannedTime, MilestoneType.PICKUP);

        TripEvidence evidence = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(milestone), List.of(plannedStop), null,
                Map.of(), null, null, null, config);

        List<AnchorPoint> points = patchEngine.patchAndMerge(List.of(), evidence);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).getCoordinate()).isEqualTo(plannedLocation);
        assertThat(points.get(0).getTimestamp()).isEqualTo(plannedTime);
        assertThat(points.get(0).getSourceDescription()).isEqualTo("PLANNED_STOP_FALLBACK");
    }

    @Test
    @DisplayName("No-Show Anchor is included when flag is enabled, and omitted when flag is disabled")
    void testNoShowAnchorFlag() {
        Coordinate noShowCoord = Coordinate.of(12.9300, 77.6300);
        Instant time = Instant.parse("2026-10-29T10:25:00Z");

        TripMilestone noShowMilestone = new TripMilestone(
                MilestoneType.NO_SHOW, "EMP-NOSHOW", noShowCoord, time,
                RiderType.PLANNED, MilestoneStatus.NO_SHOW);

        // Flag enabled
        EngineConfig enabledConfig = new EngineConfig(
                "v1.2.0", 50.0, 120.0, 70, 5.0, 400.0, 100.0, 1.4, true, 4, 2);
        TripEvidence evidenceEnabled = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(noShowMilestone), List.of(), null, Map.of(), null, null, null, enabledConfig);

        List<AnchorPoint> pointsEnabled = new AnchorPatchEngine(enabledConfig).patchAndMerge(List.of(), evidenceEnabled);
        assertThat(pointsEnabled).hasSize(1);
        assertThat(pointsEnabled.get(0).getType()).isEqualTo(AnchorType.EMPLOYEE_NO_SHOW);

        // Flag disabled
        EngineConfig disabledConfig = new EngineConfig(
                "v1.2.0", 50.0, 120.0, 70, 5.0, 400.0, 100.0, 1.4, false, 4, 2);
        TripEvidence evidenceDisabled = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(noShowMilestone), List.of(), null, Map.of(), null, null, null, disabledConfig);

        List<AnchorPoint> pointsDisabled = new AnchorPatchEngine(disabledConfig).patchAndMerge(List.of(), evidenceDisabled);
        assertThat(pointsDisabled).isEmpty();
    }

    @Test
    @DisplayName("Office Anchor: uses actual trip-end point when within 400m, but falls back to planned office when > 400m")
    void testOfficeAnchor400mRule() {
        Coordinate plannedOffice = Coordinate.of(12.9352, 77.6245);
        OfficeLocation office = new OfficeLocation("OFF-1", "MoveInSync HQ", plannedOffice);
        Instant tripEndTime = Instant.parse("2026-10-29T11:00:00Z");

        // Case A: Actual trip-end is 150m away (<= 400m)
        Coordinate closeTripEnd = Coordinate.of(12.9360, 77.6250);
        TripEvidence evidenceClose = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(), List.of(), office, Map.of(),
                null, tripEndTime, closeTripEnd, config);

        List<AnchorPoint> pointsClose = patchEngine.patchAndMerge(List.of(), evidenceClose);
        assertThat(pointsClose).hasSize(1);
        assertThat(pointsClose.get(0).getCoordinate()).isEqualTo(closeTripEnd);
        assertThat(pointsClose.get(0).getSourceDescription()).isEqualTo("OFFICE_ACTUAL_WITHIN_400M");

        // Case B: Actual trip-end is 900m away (> 400m)
        Coordinate farTripEnd = Coordinate.of(12.9430, 77.6245);
        TripEvidence evidenceFar = new TripEvidence(
                "trip-1", "tenant-1", List.of(), List.of(), List.of(), office, Map.of(),
                null, tripEndTime, farTripEnd, config);

        List<AnchorPoint> pointsFar = patchEngine.patchAndMerge(List.of(), evidenceFar);
        assertThat(pointsFar).hasSize(1);
        assertThat(pointsFar.get(0).getCoordinate()).isEqualTo(plannedOffice);
        assertThat(pointsFar.get(0).getSourceDescription()).isEqualTo("OFFICE_PLANNED_FALLBACK");
    }
}
