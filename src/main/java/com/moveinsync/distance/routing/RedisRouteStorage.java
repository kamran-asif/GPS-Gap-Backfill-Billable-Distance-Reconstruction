package com.moveinsync.distance.routing;

import java.time.Duration;

/**
 * Pluggable L2 distributed storage interface for spatial routing cache.
 */
public interface RedisRouteStorage {
    RouteResult get(String key);
    void put(String key, RouteResult result, Duration ttl);
    void invalidateAll();
}

