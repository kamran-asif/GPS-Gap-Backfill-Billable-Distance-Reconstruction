package com.moveinsync.distance.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, thread-safe Circuit Breaker for external routing provider.
 * Protects against cascading failures, timeouts, and API rate-limiting spikes.
 */
public final class RoutingCircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(RoutingCircuitBreaker.class);

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int windowSize;
    private final double failureRateThreshold; // e.g. 0.50 (50%)
    private final long resetTimeoutMillis;     // time to wait in OPEN before moving to HALF_OPEN
    private final int halfOpenTrialCalls;

    private volatile State state = State.CLOSED;
    private final AtomicLong lastStateChangeTime = new AtomicLong(System.currentTimeMillis());
    private final boolean[] ringBuffer;
    private int ringIndex = 0;
    private int samplesCount = 0;
    private final AtomicInteger trialSuccesses = new AtomicInteger(0);
    private final AtomicInteger trialCalls = new AtomicInteger(0);
    private final AtomicInteger totalTrips = new AtomicInteger(0);
    private volatile boolean forcedOpen = false;

    private final ReentrantLock lock = new ReentrantLock();

    public RoutingCircuitBreaker(int windowSize, double failureRateThreshold, long resetTimeoutMillis, int halfOpenTrialCalls) {
        this.windowSize = windowSize > 0 ? windowSize : 10;
        this.failureRateThreshold = failureRateThreshold > 0 ? failureRateThreshold : 0.5;
        this.resetTimeoutMillis = resetTimeoutMillis > 0 ? resetTimeoutMillis : 3000;
        this.halfOpenTrialCalls = halfOpenTrialCalls > 0 ? halfOpenTrialCalls : 3;
        this.ringBuffer = new boolean[this.windowSize];
    }

    public static RoutingCircuitBreaker createDefault() {
        return new RoutingCircuitBreaker(10, 0.50, 3000, 3);
    }

    public boolean allowRequest() {
        if (forcedOpen) {
            return false;
        }

        State current = state;
        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastStateChangeTime.get();
            if (elapsed > resetTimeoutMillis) {
                lock.lock();
                try {
                    if (state == State.OPEN && (System.currentTimeMillis() - lastStateChangeTime.get() > resetTimeoutMillis)) {
                        log.info("Circuit breaker transitioning from OPEN -> HALF_OPEN after {}ms", elapsed);
                        state = State.HALF_OPEN;
                        lastStateChangeTime.set(System.currentTimeMillis());
                        trialCalls.set(0);
                        trialSuccesses.set(0);
                        return true;
                    }
                } finally {
                    lock.unlock();
                }
            }
            return false;
        }

        // HALF_OPEN
        return trialCalls.incrementAndGet() <= halfOpenTrialCalls;
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            int successes = trialSuccesses.incrementAndGet();
            if (successes >= halfOpenTrialCalls) {
                lock.lock();
                try {
                    log.info("Circuit breaker transitioning from HALF_OPEN -> CLOSED (provider recovered)");
                    state = State.CLOSED;
                    lastStateChangeTime.set(System.currentTimeMillis());
                    resetBuffer();
                } finally {
                    lock.unlock();
                }
            }
            return;
        }

        lock.lock();
        try {
            recordSample(true);
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure() {
        if (state == State.HALF_OPEN) {
            lock.lock();
            try {
                log.warn("Circuit breaker probe failed in HALF_OPEN. Transitioning back to OPEN.");
                tripOpen();
            } finally {
                lock.unlock();
            }
            return;
        }

        lock.lock();
        try {
            recordSample(false);
            if (samplesCount >= windowSize) {
                int failures = 0;
                for (int i = 0; i < windowSize; i++) {
                    if (!ringBuffer[i]) failures++;
                }
                double failureRate = (double) failures / windowSize;
                if (failureRate >= failureRateThreshold) {
                    log.warn("Circuit breaker failure rate {} >= threshold {}. Tripping to OPEN!",
                            failureRate, failureRateThreshold);
                    tripOpen();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void tripOpen() {
        state = State.OPEN;
        lastStateChangeTime.set(System.currentTimeMillis());
        totalTrips.incrementAndGet();
    }

    private void recordSample(boolean success) {
        ringBuffer[ringIndex] = success;
        ringIndex = (ringIndex + 1) % windowSize;
        if (samplesCount < windowSize) {
            samplesCount++;
        }
    }

    private void resetBuffer() {
        ringIndex = 0;
        samplesCount = 0;
        for (int i = 0; i < windowSize; i++) {
            ringBuffer[i] = true;
        }
    }

    public void trip() {
        lock.lock();
        try {
            tripOpen();
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            state = State.CLOSED;
            forcedOpen = false;
            lastStateChangeTime.set(System.currentTimeMillis());
            resetBuffer();
            log.info("Circuit breaker manually reset to CLOSED");
        } finally {
            lock.unlock();
        }
    }

    public void setForcedOpen(boolean forcedOpen) {
        this.forcedOpen = forcedOpen;
        if (forcedOpen) {
            trip();
        } else {
            reset();
        }
    }

    public State getState() {
        return state;
    }

    public int getTotalTrips() {
        return totalTrips.get();
    }
}
