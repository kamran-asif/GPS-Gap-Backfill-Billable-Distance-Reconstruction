package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.routing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentCalculatorEngineTest {

    private EngineConfig config;
    private SimulatedRoutingProvider provider;
    private RoutingCircuitBreaker circuitBreaker;
    private RoutingCache cache;
    private RoutingService routingService;
    private SegmentCalculatorEngine engine;

    @BeforeEach
    void setUp() {
        config = EngineConfig.defaultConfig();
        provider = new SimulatedRoutingProvider(1.30); // 1.30x road factor
        circuitBreaker = RoutingCircuitBreaker.createDefault();
        cache = new RoutingCache(4, 1000, Duration.ofMinutes(10));
        routingService = new RoutingService(provider, cache, circuitBreaker);
        engine = new SegmentCalculatorEngine(config, routingService);
    }

    @Test
    @DisplayName("Short gap aerial: points 30s apart (< 70s) at 40 km/h use straight-line distance")
    void testShortGapAerial() {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = t0.plusSeconds(30);

        AnchorPoint p0 = new AnchorPoint(Coordinate.of(12.9700, 77.5900), t0, AnchorType.GPS_PING, "GPS");
        AnchorPoint p1 = new AnchorPoint(Coordinate.of(12.9730, 77.5900), t1, AnchorType.GPS_PING, "GPS");

        SegmentCalculatorEngine.SegmentCalculationResult result = engine.calculate(List.of(p0, p1));

        assertThat(result.getSegments()).hasSize(1);
        SegmentResult segment = result.getSegments().get(0);
        assertThat(segment.getRule()).isEqualTo(DistanceRule.SHORT_GAP_AERIAL);
        assertThat(segment.isRouted()).isFalse();
        assertThat(segment.getFinalDistanceKm()).isEqualTo(segment.getAerialDistanceKm());
    }

    @Test
    @DisplayName("Parked cab: long gap of 300s (>= 70s) with speed 1.5 km/h (< 5 km/h) uses straight-line distance")
    void testParkedCabStraightLine() {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = t0.plusSeconds(300); // 5 minutes

        // Moving only 100 meters in 5 minutes = 1.2 km/h
        AnchorPoint p0 = new AnchorPoint(Coordinate.of(12.9700, 77.5900), t0, AnchorType.GPS_PING, "GPS");
        AnchorPoint p1 = new AnchorPoint(Coordinate.of(12.9705, 77.5905), t1, AnchorType.GPS_PING, "GPS");

        SegmentCalculatorEngine.SegmentCalculationResult result = engine.calculate(List.of(p0, p1));

        assertThat(result.getSegments()).hasSize(1);
        SegmentResult segment = result.getSegments().get(0);
        assertThat(segment.getRule()).isEqualTo(DistanceRule.PARKED);
        assertThat(segment.isRouted()).isFalse();
        assertThat(segment.getFinalDistanceKm()).isEqualTo(segment.getAerialDistanceKm());
    }

    @Test
    @DisplayName("Real gap: 15-minute gap across 8 km queries routing provider and applies ROUTED distance")
    void testRealGapRouted() {
        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = t0.plusSeconds(900); // 15 minutes

        // ~8 km aerial distance
        AnchorPoint p0 = new AnchorPoint(Coordinate.of(12.9000, 77.5000), t0, AnchorType.EMPLOYEE_PICKUP, "PICKUP");
        AnchorPoint p1 = new AnchorPoint(Coordinate.of(12.9700, 77.5500), t1, AnchorType.EMPLOYEE_DROP, "DROP");

        SegmentCalculatorEngine.SegmentCalculationResult result = engine.calculate(List.of(p0, p1));

        assertThat(result.getSegments()).hasSize(1);
        SegmentResult segment = result.getSegments().get(0);
        assertThat(segment.getRule()).isEqualTo(DistanceRule.ROUTED);
        assertThat(segment.isRouted()).isTrue();
        assertThat(segment.getFinalDistanceKm()).isGreaterThan(segment.getAerialDistanceKm());
    }

    @Test
    @DisplayName("Rejects routing distance exceeding 100 km for a single segment")
    void testRejectsMaxRoutingDistanceExceeded() {
        // Mock provider returning 150 km
        RoutingProvider mockProvider = (start, end) -> RouteResult.success(150.0, 7200);
        RoutingService customRouting = new RoutingService(mockProvider, cache, circuitBreaker);
        SegmentCalculatorEngine customEngine = new SegmentCalculatorEngine(config, customRouting);

        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = t0.plusSeconds(600); // 10 minutes at 46 km/h (real gap, not parked)

        AnchorPoint p0 = new AnchorPoint(Coordinate.of(12.9000, 77.5000), t0, AnchorType.GPS_PING, "GPS");
        AnchorPoint p1 = new AnchorPoint(Coordinate.of(12.9500, 77.5500), t1, AnchorType.GPS_PING, "GPS");

        SegmentCalculatorEngine.SegmentCalculationResult result = customEngine.calculate(List.of(p0, p1));

        assertThat(result.getSegments()).hasSize(1);
        SegmentResult segment = result.getSegments().get(0);
        assertThat(segment.getRule()).isEqualTo(DistanceRule.REJECTED_MAX_DISTANCE);
        assertThat(segment.getFinalDistanceKm()).isEqualTo(segment.getAerialDistanceKm());
    }

    @Test
    @DisplayName("Degraded mode: when Circuit Breaker is OPEN, applies 1.4x straight-line and marks trip DEGRADED")
    void testCircuitBreakerOpenFallback() {
        circuitBreaker.trip(); // Force OPEN

        Instant t0 = Instant.parse("2026-10-29T10:00:00Z");
        Instant t1 = t0.plusSeconds(600); // 10 minutes gap

        AnchorPoint p0 = new AnchorPoint(Coordinate.of(12.9000, 77.5000), t0, AnchorType.GPS_PING, "GPS");
        AnchorPoint p1 = new AnchorPoint(Coordinate.of(12.9500, 77.5500), t1, AnchorType.GPS_PING, "GPS");

        SegmentCalculatorEngine.SegmentCalculationResult result = engine.calculate(List.of(p0, p1));

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getSegments()).hasSize(1);
        SegmentResult segment = result.getSegments().get(0);
        assertThat(segment.getRule()).isEqualTo(DistanceRule.DEGRADED_CIRCUIT_OPEN);

        double expectedFallback = Math.round(segment.getAerialDistanceKm() * 1.4 * 1000.0) / 1000.0;
        assertThat(segment.getFinalDistanceKm()).isCloseTo(expectedFallback, org.assertj.core.data.Offset.offset(0.01));
    }
}
