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

class ProviderFailureRecoveryTest {

    private SimulatedRoutingProvider provider;
    private RoutingCircuitBreaker circuitBreaker;
    private RoutingCache cache;
    private RoutingService routingService;
    private TripDistanceRebuildingEngine engine;

    @BeforeEach
    void setUp() {
        provider = new SimulatedRoutingProvider(1.28);
        circuitBreaker = new RoutingCircuitBreaker(4, 0.50, 100, 2);
        cache = new RoutingCache(4, 1000, Duration.ofMinutes(10));
        routingService = new RoutingService(provider, cache, circuitBreaker);
        engine = new TripDistanceRebuildingEngine(routingService);
    }

    @Test
    @DisplayName("Failure-Injection Flow: provider fails -> circuit trips -> degraded fallback -> provider recovers -> trip recomputed")
    void testProviderFailureAndRecoveryLifecycle() {
        Instant t0 = Instant.parse("2026-10-29T09:00:00Z");

        // Gps points with a large gap requiring routing
        List<GpsPing> pings = new ArrayList<>();
        pings.add(new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 10.0));
        pings.add(new GpsPing(Coordinate.of(12.9020, 77.5020), t0.plusSeconds(30), 10.0));
        // Real gap (15 min gap, 12 km)
        pings.add(new GpsPing(Coordinate.of(13.0000, 77.6000), t0.plusSeconds(900), 10.0));

        TripEvidence evidence = new TripEvidence(
                "trip-fault-injection-1",
                "tenant-fault",
                pings,
                List.of(),
                List.of(),
                null,
                Map.of(),
                t0,
                t0.plusSeconds(900),
                null,
                EngineConfig.defaultConfig()
        );

        // 1. INJECT FAILURE: Provider goes down (503 Service Unavailable)
        provider.setSimulateFailure(true);

        // Calculate trip while provider is down
        TripDistanceResult degradedResult = engine.calculateTripDistance(evidence, 1);

        // Trip MUST be marked DEGRADED
        assertThat(degradedResult.getStatus()).isEqualTo(TripStatus.DEGRADED);

        // Degraded fallback applied (1.4x aerial)
        boolean hasDegradedSegment = degradedResult.getSegments().stream()
                .anyMatch(s -> s.getRule() == DistanceRule.DEGRADED_CIRCUIT_OPEN || s.getRule() == DistanceRule.DEGRADED_PROVIDER_ERROR);
        assertThat(hasDegradedSegment).isTrue();

        double degradedDistance = degradedResult.getTotalDistanceKm();

        // 2. PROVIDER RECOVERS: Provider restored to health
        provider.setSimulateFailure(false);
        circuitBreaker.reset(); // Reset circuit breaker

        // 3. RECOMPUTE: Automatically recompute now that provider is online
        TripDistanceRebuildingEngine.RecomputeExecutionResult recomputeResult =
                engine.recomputeWithLateEvidence(degradedResult, evidence, "PROVIDER_RECOVERY_AUTO_RECONCILE");

        TripDistanceResult recoveredResult = recomputeResult.getNewTripResult();
        RecomputeDelta delta = recomputeResult.getDelta();

        // Status is now COMPLETED (or RECOMPUTED) with healthy routing
        assertThat(recoveredResult.getStatus()).isEqualTo(TripStatus.COMPLETED);

        // Segments now show ROUTED rule instead of DEGRADED
        boolean hasRoutedNow = recoveredResult.getSegments().stream()
                .anyMatch(s -> s.getRule() == DistanceRule.ROUTED);
        assertThat(hasRoutedNow).isTrue();

        // Signed delta adjustment is created
        assertThat(delta.getPreviousVersion()).isEqualTo(1);
        assertThat(delta.getNewVersion()).isEqualTo(2);
        assertThat(delta.getReason()).isEqualTo("PROVIDER_RECOVERY_AUTO_RECONCILE");
        assertThat(delta.getPreviousDistanceKm()).isEqualTo(degradedDistance);
    }
}
