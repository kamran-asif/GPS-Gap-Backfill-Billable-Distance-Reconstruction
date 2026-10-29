package com.moveinsync.distance.controller;

import com.moveinsync.distance.dto.CircuitBreakerStatusResponse;
import com.moveinsync.distance.monitoring.EngineMetrics;
import com.moveinsync.distance.routing.RoutingCircuitBreaker;
import com.moveinsync.distance.routing.RoutingService;
import com.moveinsync.distance.routing.SimulatedRoutingProvider;
import com.moveinsync.distance.service.TripDistanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class AdminController {

    private final TripDistanceService tripService;
    private final RoutingService routingService;
    private final EngineMetrics metrics;

    public AdminController(TripDistanceService tripService, RoutingService routingService, EngineMetrics metrics) {
        this.tripService = tripService;
        this.routingService = routingService;
        this.metrics = metrics;
    }

    @GetMapping("/circuit-breaker")
    public ResponseEntity<CircuitBreakerStatusResponse> getCircuitBreakerStatus() {
        RoutingCircuitBreaker cb = routingService.getCircuitBreaker();
        CircuitBreakerStatusResponse response = new CircuitBreakerStatusResponse(
                cb.getState().name(),
                cb.getTotalTrips(),
                routingService.getCache().getHitCount(),
                routingService.getCache().getMissCount(),
                routingService.getCache().getEstimatedSize()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/circuit-breaker/trip")
    public ResponseEntity<Map<String, String>> tripCircuitBreaker() {
        routingService.getCircuitBreaker().setForcedOpen(true);
        metrics.recordCircuitTripped();
        return ResponseEntity.ok(Map.of(
                "status", "CIRCUIT_TRIPPED_OPEN",
                "message", "Circuit breaker has been manually tripped to OPEN state for failure testing"
        ));
    }

    @PostMapping("/circuit-breaker/reset")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker() {
        routingService.getCircuitBreaker().reset();
        return ResponseEntity.ok(Map.of(
                "status", "CIRCUIT_RESET_CLOSED",
                "message", "Circuit breaker has been reset to CLOSED state"
        ));
    }

    @PostMapping("/provider/simulate-failure")
    public ResponseEntity<Map<String, Object>> setSimulateFailure(@RequestParam boolean fail) {
        if (routingService.getProvider() instanceof SimulatedRoutingProvider simulated) {
            simulated.setSimulateFailure(fail);
            return ResponseEntity.ok(Map.of(
                    "simulatedFailure", fail,
                    "message", fail ? "Routing provider will now simulate 503 service outage" : "Routing provider restored to normal operation"
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Active provider is not a SimulatedRoutingProvider"));
    }

    @PostMapping("/reconcile-degraded")
    public ResponseEntity<Map<String, Object>> reconcileDegraded() {
        int count = tripService.reconcileDegradedTrips();
        return ResponseEntity.ok(Map.of(
                "reconciledCount", count,
                "message", "Triggered reconciliation check for degraded trips"
        ));
    }
}
