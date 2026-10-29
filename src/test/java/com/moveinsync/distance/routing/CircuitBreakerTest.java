package com.moveinsync.distance.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    @Test
    @DisplayName("Circuit breaker starts in CLOSED state and allows requests")
    void testInitialStateClosed() {
        RoutingCircuitBreaker cb = new RoutingCircuitBreaker(4, 0.50, 100, 2);
        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("Circuit breaker trips to OPEN when failure rate meets or exceeds threshold")
    void testTripsToOpenOnFailures() {
        // Window size 4, 50% failure rate threshold
        RoutingCircuitBreaker cb = new RoutingCircuitBreaker(4, 0.50, 1000, 2);

        cb.recordSuccess();
        cb.recordSuccess();
        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.CLOSED);

        cb.recordFailure();
        cb.recordFailure(); // 2 failures out of 4 = 50% => trips to OPEN!

        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
        assertThat(cb.getTotalTrips()).isEqualTo(1);
    }

    @Test
    @DisplayName("Circuit breaker transitions OPEN -> HALF_OPEN after reset timeout, then recovers to CLOSED on success")
    void testTransitionToHalfOpenAndRecovery() throws InterruptedException {
        // Short reset timeout of 50ms
        RoutingCircuitBreaker cb = new RoutingCircuitBreaker(4, 0.50, 50, 2);

        cb.trip();
        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.OPEN);

        Thread.sleep(70); // Wait past timeout

        // First call should transition to HALF_OPEN and allow trial request
        boolean allowed = cb.allowRequest();
        assertThat(allowed).isTrue();
        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.HALF_OPEN);

        // Record 2 successful trial calls to recover
        cb.recordSuccess();
        cb.recordSuccess();

        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }

    @Test
    @DisplayName("Manual trip and reset work deterministically")
    void testManualTripAndReset() {
        RoutingCircuitBreaker cb = RoutingCircuitBreaker.createDefault();

        cb.setForcedOpen(true);
        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        cb.reset();
        assertThat(cb.getState()).isEqualTo(RoutingCircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }
}
