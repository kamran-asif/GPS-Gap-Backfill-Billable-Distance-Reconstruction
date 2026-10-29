package com.moveinsync.distance.routing;

import com.moveinsync.distance.domain.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Realistic routing provider implementation.
 * Calculates realistic driving distance using road network detour factor (~1.25x - 1.35x aerial distance).
 * Includes fault injection for testing and live demonstrations.
 */
public final class SimulatedRoutingProvider implements RoutingProvider {
    private static final Logger log = LoggerFactory.getLogger(SimulatedRoutingProvider.class);

    private static final double DEFAULT_ROAD_FACTOR = 1.28; // Average urban road circuity factor
    private static final double AVERAGE_SPEED_KMH = 35.0;   // Urban average speed

    private final AtomicBoolean failureSimulated = new AtomicBoolean(false);
    private final double roadFactor;

    public SimulatedRoutingProvider(double roadFactor) {
        this.roadFactor = roadFactor > 1.0 ? roadFactor : DEFAULT_ROAD_FACTOR;
    }

    public SimulatedRoutingProvider() {
        this(DEFAULT_ROAD_FACTOR);
    }

    @Override
    public RouteResult fetchRoute(Coordinate start, Coordinate end) {
        if (failureSimulated.get()) {
            log.warn("Routing provider simulated failure triggered for route {} -> {}", start, end);
            return RouteResult.providerError("Provider HTTP 503 Service Unavailable (Simulated Outage)");
        }

        double aerialKm = start.haversineDistanceKm(end);
        double drivingKm = BigDecimal.valueOf(aerialKm * roadFactor).setScale(3, RoundingMode.HALF_UP).doubleValue();
        long durationSec = Math.round((drivingKm / AVERAGE_SPEED_KMH) * 3600.0);

        return RouteResult.success(drivingKm, durationSec);
    }

    public void setSimulateFailure(boolean fail) {
        this.failureSimulated.set(fail);
        log.info("SimulatedRoutingProvider failure simulation set to: {}", fail);
    }

    public boolean isFailureSimulated() {
        return failureSimulated.get();
    }
}
