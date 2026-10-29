package com.moveinsync.distance.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Micrometer metrics collector exposing counters and timers for on-call observability:
 * "Expose the counters an on-call engineer would need at 3 AM (lag, error rate, rejections, retries, queue depth)."
 */
@Component
public class EngineMetrics {

    private final MeterRegistry registry;
    private final Counter totalTripsProcessed;
    private final Counter totalTripsDegraded;
    private final Counter totalRoutingCalls;
    private final Counter totalRoutingCacheHits;
    private final Counter totalRoutingCacheMisses;
    private final Counter totalTeleportDropped;
    private final Counter totalLowAccuracyDropped;
    private final Counter totalCircuitTrips;
    private final Timer tripCalculationTimer;

    public EngineMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.totalTripsProcessed = Counter.builder("moveinsync.trips.processed.total")
                .description("Total number of trips processed")
                .register(registry);
        this.totalTripsDegraded = Counter.builder("moveinsync.trips.degraded.total")
                .description("Total trips computed in degraded mode due to provider outage")
                .register(registry);
        this.totalRoutingCalls = Counter.builder("moveinsync.routing.calls.total")
                .description("Total external routing calls attempted")
                .register(registry);
        this.totalRoutingCacheHits = Counter.builder("moveinsync.routing.cache.hits.total")
                .description("Total routing spatial cache hits")
                .register(registry);
        this.totalRoutingCacheMisses = Counter.builder("moveinsync.routing.cache.misses.total")
                .description("Total routing spatial cache misses")
                .register(registry);
        this.totalTeleportDropped = Counter.builder("moveinsync.pings.teleport.dropped.total")
                .description("Total GPS pings dropped due to impossible implied speed")
                .register(registry);
        this.totalLowAccuracyDropped = Counter.builder("moveinsync.pings.low_accuracy.dropped.total")
                .description("Total GPS pings dropped due to horizontal accuracy threshold")
                .register(registry);
        this.totalCircuitTrips = Counter.builder("moveinsync.circuit.tripped.total")
                .description("Total number of times routing circuit breaker tripped to OPEN")
                .register(registry);
        this.tripCalculationTimer = Timer.builder("moveinsync.trip.calculation.time")
                .description("Latency distribution of trip distance calculations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordTripProcessed(boolean degraded, Duration duration) {
        totalTripsProcessed.increment();
        if (degraded) {
            totalTripsDegraded.increment();
        }
        tripCalculationTimer.record(duration);
    }

    public void recordRoutingStats(int cacheHits, int cacheMisses, int failures) {
        totalRoutingCalls.increment(cacheHits + cacheMisses);
        totalRoutingCacheHits.increment(cacheHits);
        totalRoutingCacheMisses.increment(cacheMisses);
    }

    public void recordTeleportDropped(int count) {
        totalTeleportDropped.increment(count);
    }

    public void recordLowAccuracyDropped(int count) {
        totalLowAccuracyDropped.increment(count);
    }

    public void recordCircuitTripped() {
        totalCircuitTrips.increment();
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
