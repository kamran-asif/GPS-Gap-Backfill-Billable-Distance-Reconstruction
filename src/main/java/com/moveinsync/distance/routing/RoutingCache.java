package com.moveinsync.distance.routing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moveinsync.distance.domain.Coordinate;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance spatial routing cache.
 * Keys are snapped start and end spatial grid cells (e.g., 4 decimal places ~ 11 meters).
 * Uses Caffeine LRU eviction policy with bounded capacity and TTL.
 */
public final class RoutingCache {
    private static final int DEFAULT_MAX_SIZE = 50_000;
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final int gridPrecision;
    private final Cache<String, RouteResult> cache;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public RoutingCache(int gridPrecision, int maxSize, Duration ttl) {
        this.gridPrecision = gridPrecision;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE)
                .expireAfterWrite(ttl != null ? ttl : DEFAULT_TTL)
                .recordStats()
                .build();
    }

    public static RoutingCache createDefault(int gridPrecision) {
        return new RoutingCache(gridPrecision, DEFAULT_MAX_SIZE, DEFAULT_TTL);
    }

    public String generateKey(Coordinate start, Coordinate end) {
        Objects.requireNonNull(start, "Start coordinate must not be null");
        Objects.requireNonNull(end, "End coordinate must not be null");
        return start.toGridCell(gridPrecision) + "->" + end.toGridCell(gridPrecision);
    }

    public RouteResult get(Coordinate start, Coordinate end) {
        String key = generateKey(start, end);
        RouteResult result = cache.getIfPresent(key);
        if (result != null) {
            hitCount.incrementAndGet();
            return RouteResult.cacheHit(result.getDistanceKm(), result.getDurationSeconds());
        }
        missCount.incrementAndGet();
        return null;
    }

    public void put(Coordinate start, Coordinate end, RouteResult result) {
        if (result != null && result.isSuccess()) {
            String key = generateKey(start, end);
            cache.put(key, result);
        }
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getEstimatedSize() {
        return cache.estimatedSize();
    }

    public void invalidateAll() {
        cache.invalidateAll();
        hitCount.set(0);
        missCount.set(0);
    }
}
