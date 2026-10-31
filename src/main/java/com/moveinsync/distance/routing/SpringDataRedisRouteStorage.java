package com.moveinsync.distance.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Spring Data Redis implementation of L2 distributed route storage.
 * Stores routing results as "distanceKm:durationSeconds" string under key prefix "moveinsync:route:".
 */
public class SpringDataRedisRouteStorage implements RedisRouteStorage {

    private static final Logger log = LoggerFactory.getLogger(SpringDataRedisRouteStorage.class);
    private static final String KEY_PREFIX = "moveinsync:route:";

    private final StringRedisTemplate redisTemplate;

    public SpringDataRedisRouteStorage(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RouteResult get(String key) {
        if (redisTemplate == null) return null;
        try {
            String val = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (val != null && val.contains(":")) {
                String[] parts = val.split(":");
                double distanceKm = Double.parseDouble(parts[0]);
                long durationSeconds = Long.parseLong(parts[1]);
                log.debug("L2 Redis cache hit for key {}", key);
                return RouteResult.cacheHit(distanceKm, durationSeconds);
            }
        } catch (Exception e) {
            log.warn("L2 Redis cache read failed for key {}: {}. Falling back gracefully.", key, e.getMessage());
        }
        return null;
    }

    @Override
    public void put(String key, RouteResult result, Duration ttl) {
        if (redisTemplate == null || result == null || !result.isSuccess()) return;
        try {
            String val = result.getDistanceKm() + ":" + result.getDurationSeconds();
            redisTemplate.opsForValue().set(KEY_PREFIX + key, val, ttl);
            log.debug("Stored in L2 Redis cache: key={}", key);
        } catch (Exception e) {
            log.warn("L2 Redis cache write failed for key {}: {}. Continuing gracefully.", key, e.getMessage());
        }
    }

    @Override
    public void invalidateAll() {
        if (redisTemplate == null) return;
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("L2 Redis cache invalidation failed: {}", e.getMessage());
        }
    }
}

