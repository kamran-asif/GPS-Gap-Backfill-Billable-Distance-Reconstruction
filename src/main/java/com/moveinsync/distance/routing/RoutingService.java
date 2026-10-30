package com.moveinsync.distance.routing;

import com.moveinsync.distance.domain.Coordinate;
import com.moveinsync.distance.domain.EngineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Routing Service integrating Circuit Breaker and Spatial Grid Caching.
 */
public final class RoutingService {
    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingProvider provider;
    private final RoutingCache cache;
    private final RoutingCircuitBreaker circuitBreaker;
    private final RoutingRateLimiter rateLimiter;

    public RoutingService(RoutingProvider provider, RoutingCache cache, RoutingCircuitBreaker circuitBreaker) {
        this(provider, cache, circuitBreaker, RoutingRateLimiter.unlimited());
    }

    public RoutingService(RoutingProvider provider,
                          RoutingCache cache,
                          RoutingCircuitBreaker circuitBreaker,
                          RoutingRateLimiter rateLimiter) {
        this.provider = Objects.requireNonNull(provider, "RoutingProvider must not be null");
        this.cache = Objects.requireNonNull(cache, "RoutingCache must not be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "RoutingCircuitBreaker must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "RoutingRateLimiter must not be null");
    }

    public static RoutingService createDefault(RoutingProvider provider, EngineConfig config) {
        RoutingCache cache = RoutingCache.createDefault(config.getSnappingGridPrecision());
        RoutingCircuitBreaker cb = RoutingCircuitBreaker.createDefault();
        return new RoutingService(provider, cache, cb);
    }

    public RouteResult getDrivingRoute(Coordinate start, Coordinate end, RoutingSessionStats sessionStats) {
        // 1. Check cache first
        RouteResult cached = cache.get(start, end);
        if (cached != null) {
            if (sessionStats != null) {
                sessionStats.recordCacheHit();
            }
            return cached;
        }

        if (sessionStats != null) {
            sessionStats.recordCacheMiss();
        }

        // 2. Client-side outbound rate limiting (protects provider quota under horizontal scale)
        if (!rateLimiter.tryAcquire()) {
            log.warn("Outbound routing call throttled by client-side rate limiter (limit {} permits/sec) for route {} -> {}",
                    rateLimiter.getPermitsPerSecond(), start, end);
            return RouteResult.rateLimited(String.format(Locale.US,
                    "Client-side rate limiter rejected call (limit %.1f permits/sec)", rateLimiter.getPermitsPerSecond()));
        }

        // 3. Check Circuit Breaker
        if (!circuitBreaker.allowRequest()) {
            log.warn("Routing call rejected by Circuit Breaker (state: {}) for route {} -> {}",
                    circuitBreaker.getState(), start, end);
            return RouteResult.circuitOpen();
        }

        // 4. Invoke external provider
        try {
            RouteResult providerResult = provider.fetchRoute(start, end);
            if (providerResult.isSuccess()) {
                circuitBreaker.recordSuccess();
                cache.put(start, end, providerResult);
                return providerResult;
            } else {
                circuitBreaker.recordFailure();
                if (sessionStats != null) {
                    sessionStats.recordFailure();
                }
                return providerResult;
            }
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            if (sessionStats != null) {
                sessionStats.recordFailure();
            }
            log.error("Routing provider call threw exception for {} -> {}: {}", start, end, ex.getMessage());
            return RouteResult.providerError("Provider Exception: " + ex.getMessage());
        }
    }

    public RoutingCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public RoutingCache getCache() {
        return cache;
    }

    public RoutingProvider getProvider() {
        return provider;
    }

    /**
     * Session statistics accumulator for a single trip calculation.
     */
    public static final class RoutingSessionStats {
        private final AtomicInteger cacheHits = new AtomicInteger(0);
        private final AtomicInteger cacheMisses = new AtomicInteger(0);
        private final AtomicInteger failures = new AtomicInteger(0);

        public void recordCacheHit() {
            cacheHits.incrementAndGet();
        }

        public void recordCacheMiss() {
            cacheMisses.incrementAndGet();
        }

        public void recordFailure() {
            failures.incrementAndGet();
        }

        public int getCacheHits() {
            return cacheHits.get();
        }

        public int getCacheMisses() {
            return cacheMisses.get();
        }

        public int getFailures() {
            return failures.get();
        }
    }
}
