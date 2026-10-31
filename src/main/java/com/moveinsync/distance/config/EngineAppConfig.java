package com.moveinsync.distance.config;

import com.moveinsync.distance.domain.EngineConfig;
import com.moveinsync.distance.routing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class EngineAppConfig {

    private static final Logger log = LoggerFactory.getLogger(EngineAppConfig.class);

    @Bean
    public EngineConfig engineConfig() {
        return EngineConfig.defaultConfig();
    }

    @Bean
    public RoutingProvider routingProvider(
            @Value("${engine.routing.provider-type:simulated}") String providerType,
            @Value("${engine.routing.osrm.base-url:http://router.project-osrm.org}") String osrmBaseUrl,
            @Value("${engine.routing.osrm.timeout-ms:1500}") long osrmTimeoutMs) {
        if ("osrm".equalsIgnoreCase(providerType)) {
            log.info("Configuring live OSRM RoutingProvider with baseUrl={}", osrmBaseUrl);
            return new OsrmRoutingProvider(osrmBaseUrl, Duration.ofMillis(osrmTimeoutMs));
        }
        log.info("Configuring SimulatedRoutingProvider (default)");
        return new SimulatedRoutingProvider();
    }

    @Bean
    public RoutingCache routingCache(
            EngineConfig config,
            @Value("${engine.routing.redis-cache.enabled:false}") boolean redisEnabled,
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        RedisRouteStorage redisStorage = null;
        if (redisEnabled && redisTemplate != null) {
            log.info("L2 Redis distributed route cache enabled");
            redisStorage = new SpringDataRedisRouteStorage(redisTemplate);
        } else {
            log.info("Using standalone L1 Caffeine in-memory route cache");
        }
        return new RoutingCache(config.getSnappingGridPrecision(), 50_000, Duration.ofHours(24), redisStorage);
    }

    @Bean
    public RoutingCircuitBreaker routingCircuitBreaker() {
        return RoutingCircuitBreaker.createDefault();
    }

    @Bean
    public RoutingService routingService(RoutingProvider provider, RoutingCache cache, RoutingCircuitBreaker circuitBreaker) {
        return new RoutingService(provider, cache, circuitBreaker);
    }
}
