package com.moveinsync.distance.routing;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.engine.TripDistanceRebuildingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingRateLimiterTest {

    @Test
    @DisplayName("Unlimited limiter never rejects a call (default single-instance behaviour)")
    void testUnlimitedLimiterAlwaysAllows() {
        RoutingRateLimiter limiter = RoutingRateLimiter.unlimited();
        for (int i = 0; i < 10_000; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.getRejectedCount()).isZero();
        assertThat(limiter.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("8000 calls/sec burst=100: first 100 pass, remainder throttled until tokens refill")
    void testBurstThenThrottle() {
        AtomicLong virtualNanos = new AtomicLong(0L);
        RoutingRateLimiter limiter = RoutingRateLimiter.of(8_000, 100, virtualNanos::get);

        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < 200; i++) {
            if (limiter.tryAcquire()) {
                allowed++;
            } else {
                rejected++;
            }
        }

        assertThat(allowed).isEqualTo(100);
        assertThat(rejected).isEqualTo(100);
        assertThat(limiter.getAcquiredCount()).isEqualTo(100);
        assertThat(limiter.getRejectedCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("Tokens replenish based on elapsed time (virtual clock advance)")
    void testTokensReplenishOverTime() {
        AtomicLong virtualNanos = new AtomicLong(0L);
        RoutingRateLimiter limiter = RoutingRateLimiter.of(8_000, 100, virtualNanos::get);

        // Drain the bucket
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();

        // Advance 12.5 ms => 8000 * 0.0125 = 100 permits refilled
        virtualNanos.addAndGet(Duration.ofMillis(125).toNanos() / 10);

        int allowedAfterRefill = 0;
        for (int i = 0; i < 100; i++) {
            if (limiter.tryAcquire()) {
                allowedAfterRefill++;
            }
        }
        assertThat(allowedAfterRefill).isEqualTo(100);
    }

    @Test
    @DisplayName("Throttled calls never reach the routing provider when the limiter is exhausted")
    void testExhaustedLimiterTripsMarkedDegraded() {
        // burst = 1 with a negligible refill rate: the single token is consumed up-front so every
        // outbound routing call during the test is throttled deterministically.
        RoutingRateLimiter limiter = RoutingRateLimiter.of(0.0001, 1);
        assertThat(limiter.tryAcquire()).isTrue();

        SimulatedRoutingProvider provider = new SimulatedRoutingProvider(1.28);
        RoutingCircuitBreaker circuitBreaker = RoutingCircuitBreaker.createDefault();
        RoutingCache cache = new RoutingCache(4, 1000, Duration.ofHours(1));
        RoutingService routingService = new RoutingService(provider, cache, circuitBreaker, limiter);
        TripDistanceRebuildingEngine engine = new TripDistanceRebuildingEngine(routingService);

        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");
        // Two fixes 120s apart, ~0.9 km apart (~27 km/h) => real gap requiring a routing call.
        TripEvidence evidence = new TripEvidence(
                "ratelimit-trip-1",
                "tenant-rate",
                List.of(
                        new GpsPing(Coordinate.of(12.9000, 77.5000), t0, 10.0),
                        new GpsPing(Coordinate.of(12.9060, 77.5000), t0.plusSeconds(120), 10.0)
                ),
                List.of(),
                List.of(),
                null,
                Map.of(),
                t0,
                t0.plusSeconds(120),
                null,
                EngineConfig.defaultConfig()
        );

        TripDistanceResult result = engine.calculateTripDistance(evidence, 1);

        assertThat(result.getStatus()).isEqualTo(TripStatus.DEGRADED);
        assertThat(result.getSegments()).hasSize(1);
        assertThat(result.getSegments().get(0).getRule()).isEqualTo(DistanceRule.DEGRADED_RATE_LIMITED);
        assertThat(result.getSegments().get(0).getExplanation()).contains("rate limiter");
        assertThat(limiter.getRejectedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("acquire(timeout) blocks briefly and succeeds once a token refills")
    void testAcquireWithTimeout() throws InterruptedException {
        // 50 permits/sec => a single token takes ~20 ms to refill, so the immediate second
        // non-blocking attempt must fail while the blocking acquire() succeeds within 500 ms.
        RoutingRateLimiter limiter = RoutingRateLimiter.of(50, 1);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        boolean acquired = limiter.acquire(500);
        assertThat(acquired).isTrue();
    }
}
