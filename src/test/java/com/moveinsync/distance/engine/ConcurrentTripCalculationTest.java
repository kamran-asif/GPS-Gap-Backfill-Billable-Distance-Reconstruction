package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.Coordinate;
import com.moveinsync.distance.domain.EngineConfig;
import com.moveinsync.distance.domain.GpsPing;
import com.moveinsync.distance.domain.TripDistanceResult;
import com.moveinsync.distance.domain.TripEvidence;
import com.moveinsync.distance.routing.RoutingCache;
import com.moveinsync.distance.routing.RoutingCircuitBreaker;
import com.moveinsync.distance.routing.RoutingService;
import com.moveinsync.distance.routing.SimulatedRoutingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentTripCalculationTest {

    @Test
    @DisplayName("Thread safety: 16 concurrent threads processing trips with shared routing cache and circuit breaker")
    void testConcurrentExecutionSafety() throws InterruptedException, ExecutionException {
        int threads = 16;
        int tripsPerThread = 25; // 400 trips total
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        SimulatedRoutingProvider provider = new SimulatedRoutingProvider(1.28);
        RoutingCircuitBreaker circuitBreaker = RoutingCircuitBreaker.createDefault();
        RoutingCache sharedCache = new RoutingCache(4, 5000, Duration.ofMinutes(10));
        RoutingService routingService = new RoutingService(provider, sharedCache, circuitBreaker);
        TripDistanceRebuildingEngine engine = new TripDistanceRebuildingEngine(routingService);

        Instant baseTime = Instant.parse("2026-10-29T08:00:00Z");
        List<Callable<TripDistanceResult>> tasks = new ArrayList<>();

        for (int i = 0; i < threads * tripsPerThread; i++) {
            final int tripIndex = i;
            tasks.add(() -> {
                List<GpsPing> pings = new ArrayList<>();
                for (int j = 0; j < 20; j++) {
                    long offsetSec = j * 30L + (j >= 10 ? 500L : 0L);
                    pings.add(new GpsPing(
                            Coordinate.of(12.9000 + (j * 0.005) + (tripIndex * 0.0001), 77.5000 + (j * 0.005)),
                            baseTime.plusSeconds(offsetSec),
                            10.0
                    ));
                }
                TripEvidence evidence = new TripEvidence(
                        "concurrent-trip-" + tripIndex,
                        "tenant-concurrent",
                        pings,
                        List.of(),
                        List.of(),
                        null,
                        Map.of(),
                        baseTime,
                        baseTime.plusSeconds(600),
                        null,
                        EngineConfig.defaultConfig()
                );
                return engine.calculateTripDistance(evidence, 1);
            });
        }

        List<Future<TripDistanceResult>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        for (Future<TripDistanceResult> future : futures) {
            TripDistanceResult res = future.get();
            assertThat(res).isNotNull();
            assertThat(res.getTotalDistanceKm()).isGreaterThan(0.0);
            assertThat(res.getSegments()).hasSize(19);
        }

        // Shared cache should reflect hits and misses
        assertThat(sharedCache.getHitCount() + sharedCache.getMissCount()).isGreaterThan(0);
    }
}
