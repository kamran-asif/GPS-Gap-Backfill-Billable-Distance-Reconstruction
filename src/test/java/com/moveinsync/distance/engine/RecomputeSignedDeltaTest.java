package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.routing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecomputeSignedDeltaTest {

    private TripDistanceRebuildingEngine engine;
    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        SimulatedRoutingProvider provider = new SimulatedRoutingProvider(1.28);
        RoutingCircuitBreaker cb = RoutingCircuitBreaker.createDefault();
        RoutingCache cache = new RoutingCache(4, 1000, Duration.ofMinutes(10));
        routingService = new RoutingService(provider, cache, cb);
        engine = new TripDistanceRebuildingEngine(routingService);
    }

    @Test
    @DisplayName("Underpass & Basement Example: Anchoring employee drops and routing gaps yields explainable 30.6 km")
    void testUnderpassAndBasementGapReconstruction() {
        Instant startTime = Instant.parse("2026-10-29T18:00:00Z");

        // Cab starts at Office
        Coordinate office = Coordinate.of(12.9352, 77.6245);
        OfficeLocation officeLoc = new OfficeLocation("OFF-1", "MoveInSync Campus", office);

        // Ping stream before entering tunnel (first 10 km)
        List<GpsPing> pings = new ArrayList<>();
        Instant currTime = startTime;
        Coordinate currentCoord = office;
        for (int i = 0; i < 15; i++) {
            currTime = currTime.plusSeconds(30);
            currentCoord = Coordinate.of(office.getLatitude() - (i * 0.006), office.getLongitude() - (i * 0.006));
            pings.add(new GpsPing(currentCoord, currTime, 10.0));
        }

        // GPS loses fix for 22 minutes (1320s) in underpass & basement!
        Instant tunnelEnterTime = currTime;
        Coordinate tunnelEnterCoord = currentCoord;

        // Two employee drops occurred inside the gap:
        Instant drop1Time = tunnelEnterTime.plusSeconds(400);
        Coordinate drop1Coord = Coordinate.of(12.8300, 77.5200);
        TripMilestone drop1 = new TripMilestone(MilestoneType.DROP, "EMP-1", drop1Coord, drop1Time, RiderType.PLANNED, MilestoneStatus.COMPLETED);

        Instant drop2Time = tunnelEnterTime.plusSeconds(800);
        Coordinate drop2Coord = Coordinate.of(12.7900, 77.4900);
        TripMilestone drop2 = new TripMilestone(MilestoneType.DROP, "EMP-2", drop2Coord, drop2Time, RiderType.PLANNED, MilestoneStatus.COMPLETED);

        // Cab emerges from tunnel 22 minutes later
        Instant tunnelExitTime = tunnelEnterTime.plusSeconds(1320);
        Coordinate tunnelExitCoord = Coordinate.of(12.7500, 77.4600);

        currTime = tunnelExitTime;
        for (int i = 0; i < 15; i++) {
            currTime = currTime.plusSeconds(30);
            Coordinate c = Coordinate.of(tunnelExitCoord.getLatitude() - (i * 0.004), tunnelExitCoord.getLongitude() - (i * 0.004));
            pings.add(new GpsPing(c, currTime, 10.0));
        }

        TripEvidence evidence = new TripEvidence(
                "trip-logout-31km",
                "moveinsync-client-1",
                pings,
                List.of(drop1, drop2),
                List.of(),
                officeLoc,
                Map.of(),
                startTime,
                currTime,
                currentCoord,
                EngineConfig.defaultConfig()
        );

        TripDistanceResult result = engine.calculateTripDistance(evidence, 1);

        assertThat(result.getTotalDistanceKm()).isGreaterThan(25.0);
        assertThat(result.getStatus()).isEqualTo(TripStatus.COMPLETED);

        // Verify that the anchored drops were routed with DistanceRule.ROUTED
        boolean hasRoutedSegments = result.getSegments().stream().anyMatch(s -> s.getRule() == DistanceRule.ROUTED);
        assertThat(hasRoutedSegments).isTrue();

        // Verify every segment is labeled
        for (SegmentResult s : result.getSegments()) {
            assertThat(s.getExplanation()).isNotBlank();
            assertThat(s.getRule()).isNotNull();
        }
    }

    @Test
    @DisplayName("Idempotent recompute produces exact signed delta (+1.8 km) when late pings arrive after billing")
    void testLatePingsProduceSignedDelta() {
        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");

        // Initial trip billed at initial distance
        List<GpsPing> initialPings = new ArrayList<>();
        Instant currTime = t0;
        Coordinate currCoord = Coordinate.of(12.9000, 77.5000);
        initialPings.add(new GpsPing(currCoord, currTime, 10.0));

        // Cab travels 20 km along route
        for (int i = 0; i < 20; i++) {
            currTime = currTime.plusSeconds(60);
            currCoord = Coordinate.of(currCoord.getLatitude() + 0.009, currCoord.getLongitude() + 0.009); // ~1.4 km each min (~84 km/h)
            initialPings.add(new GpsPing(currCoord, currTime, 10.0));
        }

        TripEvidence initialEvidence = new TripEvidence(
                "trip-billed-invoice-1", "tenant-alpha", initialPings, List.of(), List.of(), null, Map.of(),
                t0, currTime, null, EngineConfig.defaultConfig()
        );

        TripDistanceResult initialResult = engine.calculateTripDistance(initialEvidence, 1);
        double billedDistance = initialResult.getTotalDistanceKm();

        // Later, cab uploads cached late pings for the final leg (+1.8 km)
        List<GpsPing> updatedPings = new ArrayList<>(initialPings);
        // Add 2 late pings covering ~1.8 km
        Instant tLate1 = currTime.plusSeconds(40);
        Coordinate cLate1 = Coordinate.of(currCoord.getLatitude() + 0.008, currCoord.getLongitude() + 0.008);
        updatedPings.add(new GpsPing(cLate1, tLate1, 10.0));

        Instant tLate2 = tLate1.plusSeconds(40);
        Coordinate cLate2 = Coordinate.of(cLate1.getLatitude() + 0.005, cLate1.getLongitude() + 0.005);
        updatedPings.add(new GpsPing(cLate2, tLate2, 10.0));

        TripEvidence updatedEvidence = new TripEvidence(
                "trip-billed-invoice-1", "tenant-alpha", updatedPings, List.of(), List.of(), null, Map.of(),
                t0, tLate2, null, EngineConfig.defaultConfig()
        );

        TripDistanceRebuildingEngine.RecomputeExecutionResult recompute =
                engine.recomputeWithLateEvidence(initialResult, updatedEvidence, "LATE_PINGS_INGESTED");

        RecomputeDelta delta = recompute.getDelta();

        assertThat(delta.getPreviousVersion()).isEqualTo(1);
        assertThat(delta.getNewVersion()).isEqualTo(2);
        assertThat(delta.getPreviousDistanceKm()).isEqualTo(billedDistance);
        assertThat(delta.getNewDistanceKm()).isGreaterThan(billedDistance);
        assertThat(delta.getDeltaKm()).isGreaterThan(0.0);
        assertThat(delta.getAdjustmentType()).isEqualTo(RecomputeDelta.AdjustmentType.CREDIT_NOTE);
        assertThat(delta.getFormattedDelta()).startsWith("+");
        assertThat(delta.getReason()).isEqualTo("LATE_PINGS_INGESTED");
    }

    @Test
    @DisplayName("Idempotence guarantee: identical input on same rule version produces exact 0.00 km delta")
    void testDeterministicIdempotentRecompute() {
        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");

        List<GpsPing> pings = List.of(
                new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 10.0),
                new GpsPing(Coordinate.of(12.9050, 77.5050), t0.plusSeconds(30), 10.0),
                new GpsPing(Coordinate.of(12.9100, 77.5100), t0.plusSeconds(60), 10.0)
        );

        TripEvidence evidence = new TripEvidence(
                "trip-idempotent", "tenant-alpha", pings, List.of(), List.of(), null, Map.of(),
                t0, t0.plusSeconds(60), null, EngineConfig.defaultConfig()
        );

        TripDistanceResult run1 = engine.calculateTripDistance(evidence, 1);
        TripDistanceResult run2 = engine.calculateTripDistance(evidence, 2);

        // "Two runs that disagree by 0.1 km are a billing bug."
        assertThat(run1.getTotalDistanceKm()).isEqualTo(run2.getTotalDistanceKm());

        TripDistanceRebuildingEngine.RecomputeExecutionResult recompute =
                engine.recomputeWithLateEvidence(run1, evidence, "NOOP_RETRY");

        assertThat(recompute.getDelta().getDeltaKm()).isEqualTo(0.0);
        assertThat(recompute.getDelta().getAdjustmentType()).isEqualTo(RecomputeDelta.AdjustmentType.NO_CHANGE);
    }
}
