package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.routing.RoutingCache;
import com.moveinsync.distance.routing.RoutingCircuitBreaker;
import com.moveinsync.distance.routing.RoutingService;
import com.moveinsync.distance.routing.SimulatedRoutingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ScaleBenchmarkTest {
    private static final Logger log = LoggerFactory.getLogger(ScaleBenchmarkTest.class);

    @Test
    @DisplayName("Scale & Benchmark: Process 10,000 synthetic trips of ~1,500 pings each with provider failure midway")
    void testScale10000TripsWithMidwayProviderFailure() {
        int totalTrips = 10_000;
        int pingsPerTrip = 1_500;
        int failureStartTrip = 5_000;
        int failureEndTrip = 5_500;

        log.info("==========================================================================");
        log.info("STARTING SCALE BENCHMARK: {} TRIPS x ~{} PINGS (TOTAL ~{} PINGS)",
                totalTrips, pingsPerTrip, (long) totalTrips * pingsPerTrip);
        log.info("==========================================================================");

        SimulatedRoutingProvider provider = new SimulatedRoutingProvider(1.28);
        RoutingCircuitBreaker circuitBreaker = RoutingCircuitBreaker.createDefault();
        // High-performance spatial cache
        RoutingCache cache = new RoutingCache(4, 100_000, Duration.ofHours(24));
        RoutingService routingService = new RoutingService(provider, cache, circuitBreaker);
        TripDistanceRebuildingEngine engine = new TripDistanceRebuildingEngine(routingService);

        // Pre-generate reusable realistic synthetic trip template to keep benchmark memory-efficient
        List<GpsPing> syntheticPingTemplate = generateSyntheticPingTemplate(pingsPerTrip);

        Coordinate officeCoord = Coordinate.of(12.9352, 77.6245);
        OfficeLocation office = new OfficeLocation("OFF-1", "MoveInSync HQ", officeCoord);

        TripMilestone pickupMilestone = new TripMilestone(
                MilestoneType.PICKUP, "EMP-1", Coordinate.of(12.9100, 77.6000),
                Instant.parse("2026-10-29T08:30:00Z"), RiderType.PLANNED, MilestoneStatus.COMPLETED
        );
        TripMilestone dropMilestone = new TripMilestone(
                MilestoneType.DROP, "EMP-1", Coordinate.of(12.9350, 77.6240),
                Instant.parse("2026-10-29T09:15:00Z"), RiderType.PLANNED, MilestoneStatus.COMPLETED
        );

        long[] latenciesNanos = new long[totalTrips];
        int degradedTripsCount = 0;
        int completedTripsCount = 0;

        System.gc();
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalTrips; i++) {
            if (i > 0 && i % 1000 == 0) {
                log.info("Benchmark progress: {}/{} trips processed ({}%)...", i, totalTrips, (i * 100 / totalTrips));
            }
            // Midway Failure Injection: Provider fails between trip 5,000 and 5,500
            if (i == failureStartTrip) {
                log.warn(">>> [MIDWAY FAILURE INJECTION] Simulating Routing Provider 503 Outage at trip #{}", i);
                cache.invalidateAll(); // Clear cache so requests hit provider directly
                provider.setSimulateFailure(true);
            } else if (i == failureEndTrip) {
                log.info(">>> [PROVIDER RECOVERY] Restoring Routing Provider to normal health at trip #{}", i);
                provider.setSimulateFailure(false);
                circuitBreaker.reset();
            }

            Instant tripStartTime = Instant.parse("2026-10-29T08:00:00Z");
            TripEvidence evidence = new TripEvidence(
                    "scale-trip-" + i,
                    "tenant-benchmark",
                    syntheticPingTemplate,
                    List.of(pickupMilestone, dropMilestone),
                    List.of(),
                    office,
                    Map.of(),
                    tripStartTime,
                    tripStartTime.plusSeconds(pingsPerTrip * 10L),
                    officeCoord,
                    EngineConfig.defaultConfig()
            );

            long tripStartNanos = System.nanoTime();
            TripDistanceResult result = engine.calculateTripDistance(evidence, 1);
            long tripDurationNanos = System.nanoTime() - tripStartNanos;
            latenciesNanos[i] = tripDurationNanos;

            if (result.getStatus() == TripStatus.DEGRADED) {
                degradedTripsCount++;
            } else {
                completedTripsCount++;
            }
        }

        long totalElapsedMillis = System.currentTimeMillis() - startTime;
        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Arrays.sort(latenciesNanos);
        double p50Millis = (latenciesNanos[(int) (totalTrips * 0.50)] / 1_000_000.0);
        double p95Millis = (latenciesNanos[(int) (totalTrips * 0.95)] / 1_000_000.0);
        double p99Millis = (latenciesNanos[(int) (totalTrips * 0.99)] / 1_000_000.0);

        double throughputTripsPerSec = (totalTrips * 1000.0) / totalElapsedMillis;
        double throughputPingsPerSec = ((long) totalTrips * pingsPerTrip * 1000.0) / totalElapsedMillis;

        double memoryUsedMb = (finalMemory - initialMemory) / (1024.0 * 1024.0);

        log.info("==========================================================================");
        log.info("BENCHMARK RESULTS REPORT");
        log.info("==========================================================================");
        log.info("Total Trips Processed       : {}", totalTrips);
        log.info("Pings Per Trip              : {}", pingsPerTrip);
        log.info("Total Pings Ingested        : {}", (long) totalTrips * pingsPerTrip);
        log.info("Total Elapsed Time          : {} ms ({} seconds)", totalElapsedMillis, String.format(Locale.US, "%.2f", totalElapsedMillis / 1000.0));
        log.info("Throughput (Trips/sec)      : {} trips/sec", String.format(Locale.US, "%.2f", throughputTripsPerSec));
        log.info("Throughput (Pings/sec)      : {} pings/sec", String.format(Locale.US, "%.2f", throughputPingsPerSec));
        log.info("Latency p50                 : {} ms", String.format(Locale.US, "%.3f", p50Millis));
        log.info("Latency p95                 : {} ms", String.format(Locale.US, "%.3f", p95Millis));
        log.info("Latency p99                 : {} ms", String.format(Locale.US, "%.3f", p99Millis));
        log.info("Routing Cache Hits          : {}", cache.getHitCount());
        log.info("Routing Cache Misses        : {}", cache.getMissCount());
        log.info("Cached Spatial Routes Count : {}", cache.getEstimatedSize());
        log.info("Degraded Trips Count (Fail) : {}", degradedTripsCount);
        log.info("Completed Healthy Trips     : {}", completedTripsCount);
        log.info("Memory Delta                : {} MB", String.format(Locale.US, "%.2f", memoryUsedMb));
        log.info("==========================================================================");

        assertThat(completedTripsCount).isGreaterThan(9_000);
        assertThat(degradedTripsCount).isGreaterThanOrEqualTo(450); // Tripped during failure window
        assertThat(totalTrips).isEqualTo(10_000);
    }

    private List<GpsPing> generateSyntheticPingTemplate(int count) {
        List<GpsPing> pings = new ArrayList<>(count);
        Instant t0 = Instant.parse("2026-10-29T08:00:00Z");
        double lat = 12.9000;
        double lon = 77.5000;

        for (int i = 0; i < count; i++) {
            Instant timestamp = t0.plusSeconds(i * 10L);

            // Injected noise scenarios:
            if (i == 100) {
                // Injected low accuracy fix (> 50m)
                pings.add(new GpsPing(Coordinate.of(lat, lon), timestamp, 95.0));
            } else if (i == 250) {
                // Injected teleport jump (burst to 9 km away)
                pings.add(new GpsPing(Coordinate.of(lat + 0.08, lon + 0.08), timestamp, 10.0));
            } else if (i == 500) {
                // Duplicate timestamp ping
                pings.add(new GpsPing(Coordinate.of(lat, lon), timestamp.minusSeconds(10), 12.0));
            } else {
                // Standard continuous fix (traveling ~35 km/h)
                lat += 0.0001;
                lon += 0.0001;
                pings.add(new GpsPing(Coordinate.of(lat, lon), timestamp, 10.0));
            }
        }
        return pings;
    }
}
