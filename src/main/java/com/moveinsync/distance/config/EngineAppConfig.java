package com.moveinsync.distance.config;

import com.moveinsync.distance.domain.EngineConfig;
import com.moveinsync.distance.routing.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class EngineAppConfig {

    @Bean
    public EngineConfig engineConfig() {
        return EngineConfig.defaultConfig();
    }

    @Bean
    public RoutingProvider routingProvider() {
        return new SimulatedRoutingProvider();
    }

    @Bean
    public RoutingCache routingCache(EngineConfig config) {
        return new RoutingCache(config.getSnappingGridPrecision(), 50_000, Duration.ofHours(24));
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
