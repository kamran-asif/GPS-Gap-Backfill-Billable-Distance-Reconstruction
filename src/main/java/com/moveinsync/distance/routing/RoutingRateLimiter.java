package com.moveinsync.distance.routing;

import java.util.function.LongSupplier;

/**
 * Client-side token-bucket rate limiter guarding outbound routing provider calls.
 *
 * <p>Scaling the application tier horizontally multiplies the number of concurrent outbound
 * routing calls (e.g. 10 workers x 100 requests). Without a client-side limiter the third-party
 * provider's quota (e.g. 100 req/s) is exceeded and calls start failing. This limiter caps the
 * outbound call rate so the provider quota is respected regardless of how many app instances or
 * worker threads are running.</p>
 *
 * <p>{@link #unlimited()} is used by default, so existing single-instance behaviour is unchanged.</p>
 */
public final class RoutingRateLimiter {
    private static final LongSupplier SYSTEM_NANO_CLOCK = System::nanoTime;

    private final double permitsPerSecond;
    private final double burstCapacity;
    private final boolean enabled;
    private final LongSupplier nanoClock;

    private double availablePermits;
    private long lastRefillNanos;
    private long acquiredCount;
    private long rejectedCount;

    private RoutingRateLimiter(double permitsPerSecond, double burstCapacity, boolean enabled, LongSupplier nanoClock) {
        this.permitsPerSecond = permitsPerSecond;
        this.burstCapacity = burstCapacity;
        this.enabled = enabled;
        this.nanoClock = nanoClock;
        this.availablePermits = enabled ? burstCapacity : Double.MAX_VALUE;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /**
     * No-op limiter that never rejects a call. Preserves single-instance behaviour by default.
     */
    public static RoutingRateLimiter unlimited() {
        return new RoutingRateLimiter(0.0, 0.0, false, SYSTEM_NANO_CLOCK);
    }

    public static RoutingRateLimiter of(double permitsPerSecond, double burstCapacity) {
        if (permitsPerSecond <= 0) {
            return unlimited();
        }
        double burst = burstCapacity > 0 ? burstCapacity : permitsPerSecond;
        return new RoutingRateLimiter(permitsPerSecond, burst, true, SYSTEM_NANO_CLOCK);
    }

    /**
     * Test seam allowing a deterministic virtual clock to be injected.
     */
    static RoutingRateLimiter of(double permitsPerSecond, double burstCapacity, LongSupplier nanoClock) {
        double burst = burstCapacity > 0 ? burstCapacity : permitsPerSecond;
        return new RoutingRateLimiter(permitsPerSecond, burst, true, nanoClock);
    }

    /**
     * Attempts to consume a single permit without blocking.
     *
     * @return {@code true} if the call is allowed, {@code false} if it must be throttled
     */
    public synchronized boolean tryAcquire() {
        if (!enabled) {
            acquiredCount++;
            return true;
        }
        refill();
        if (availablePermits >= 1.0) {
            availablePermits -= 1.0;
            acquiredCount++;
            return true;
        }
        rejectedCount++;
        return false;
    }

    /**
     * Blocks until a permit becomes available or the timeout elapses.
     *
     * @return {@code true} if a permit was acquired, {@code false} on timeout
     */
    public boolean acquire(long timeoutMillis) throws InterruptedException {
        if (!enabled) {
            tryAcquire();
            return true;
        }
        long deadline = System.nanoTime() + Math.max(0L, timeoutMillis) * 1_000_000L;
        while (true) {
            if (tryAcquire()) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(1L);
        }
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        lastRefillNanos = now;
        double replenished = (elapsedNanos / 1_000_000_000.0) * permitsPerSecond;
        availablePermits = Math.min(burstCapacity, availablePermits + replenished);
    }

    public synchronized long getAcquiredCount() {
        return acquiredCount;
    }

    public synchronized long getRejectedCount() {
        return rejectedCount;
    }

    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    public double getBurstCapacity() {
        return burstCapacity;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
