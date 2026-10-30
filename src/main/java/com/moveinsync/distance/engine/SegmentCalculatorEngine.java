package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.routing.RouteResult;
import com.moveinsync.distance.routing.RouteStatus;
import com.moveinsync.distance.routing.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;

/**
 * Stage III: Distance for Every Segment
 *
 * Rules:
 * 1. Cheap Cases First:
 *    - consecutive points < 70s apart at plausible speed (<= 120 km/h): straight-line (Haversine).
 *    - long gap (>= 70s) with speed < 5 km/h (cab parked/idling): straight-line (Haversine).
 * 2. Driving Distance for Real Gaps:
 *    - ask routing provider for driving distance.
 *    - reject if > 100 km, or if implied speed > 120 km/h and 1.4 * aerial > 120 km/h.
 * 3. Provider Down:
 *    - if circuit open or provider error: straight-line * 1.4, trip marked DEGRADED.
 * 4. Explainable Result:
 *    - every segment carries the rule, distance, and vendor dispute explanation.
 */
public final class SegmentCalculatorEngine {
    private static final Logger log = LoggerFactory.getLogger(SegmentCalculatorEngine.class);

    private final EngineConfig config;
    private final RoutingService routingService;

    public SegmentCalculatorEngine(EngineConfig config, RoutingService routingService) {
        this.config = Objects.requireNonNull(config, "EngineConfig must not be null");
        this.routingService = Objects.requireNonNull(routingService, "RoutingService must not be null");
    }

    public static final class SegmentCalculationResult {
        private final List<SegmentResult> segments;
        private final double totalDistanceKm;
        private final boolean degraded;
        private final Map<DistanceRule, Double> distanceByRuleKm;
        private final Map<DistanceRule, Integer> countByRule;
        private final ProviderCallStats providerStats;

        public SegmentCalculationResult(List<SegmentResult> segments,
                                        double totalDistanceKm,
                                        boolean degraded,
                                        Map<DistanceRule, Double> distanceByRuleKm,
                                        Map<DistanceRule, Integer> countByRule,
                                        ProviderCallStats providerStats) {
            this.segments = segments;
            this.totalDistanceKm = totalDistanceKm;
            this.degraded = degraded;
            this.distanceByRuleKm = distanceByRuleKm;
            this.countByRule = countByRule;
            this.providerStats = providerStats;
        }

        public List<SegmentResult> getSegments() {
            return segments;
        }

        public double getTotalDistanceKm() {
            return totalDistanceKm;
        }

        public boolean isDegraded() {
            return degraded;
        }

        public Map<DistanceRule, Double> getDistanceByRuleKm() {
            return distanceByRuleKm;
        }

        public Map<DistanceRule, Integer> getCountByRule() {
            return countByRule;
        }

        public ProviderCallStats getProviderStats() {
            return providerStats;
        }
    }

    public SegmentCalculationResult calculate(List<AnchorPoint> points) {
        if (points == null || points.size() < 2) {
            return new SegmentCalculationResult(
                    List.of(),
                    0.0,
                    false,
                    Map.of(),
                    Map.of(),
                    ProviderCallStats.zero()
            );
        }

        List<SegmentResult> segments = new ArrayList<>(points.size() - 1);
        RoutingService.RoutingSessionStats sessionStats = new RoutingService.RoutingSessionStats();

        BigDecimal runningTotal = BigDecimal.ZERO;
        boolean isDegraded = false;

        Map<DistanceRule, BigDecimal> ruleDistanceMap = new EnumMap<>(DistanceRule.class);
        Map<DistanceRule, Integer> ruleCountMap = new EnumMap<>(DistanceRule.class);

        for (int i = 0; i < points.size() - 1; i++) {
            AnchorPoint start = points.get(i);
            AnchorPoint end = points.get(i + 1);

            long deltaSeconds = Math.max(0, Duration.between(start.getTimestamp(), end.getTimestamp()).getSeconds());
            double aerialKm = start.getCoordinate().haversineDistanceKm(end.getCoordinate());
            double impliedAerialSpeedKmh = deltaSeconds > 0 ? (aerialKm / deltaSeconds) * 3600.0 : 0.0;

            SegmentResult segmentResult;

            // Rule 1A: Short gap aerial (< 70 seconds at plausible speed <= 120 km/h)
            if (deltaSeconds < config.getShortGapSeconds() && impliedAerialSpeedKmh <= config.getTeleportSpeedLimitKmh()) {
                String explanation = String.format(Locale.US,
                        "Gap of %ds (< %ds) at %.1f km/h: straight-line (Haversine) applied",
                        deltaSeconds, config.getShortGapSeconds(), impliedAerialSpeedKmh);

                segmentResult = new SegmentResult(
                        i + 1, start, end, deltaSeconds, aerialKm, aerialKm,
                        DistanceRule.SHORT_GAP_AERIAL, explanation, false);

            } else if (deltaSeconds >= config.getShortGapSeconds() && impliedAerialSpeedKmh < config.getParkedSpeedLimitKmh()) {
                // Rule 1B: Parked / Idling (long gap >= 70s with speed < 5 km/h)
                String explanation = String.format(Locale.US,
                        "Long gap of %ds with aerial speed %.2f km/h (< %.1f km/h): cab was parked/idling; straight-line applied",
                        deltaSeconds, impliedAerialSpeedKmh, config.getParkedSpeedLimitKmh());

                segmentResult = new SegmentResult(
                        i + 1, start, end, deltaSeconds, aerialKm, aerialKm,
                        DistanceRule.PARKED, explanation, false);

            } else {
                // Rule 2 & 3: Real gap -> query routing provider with Circuit Breaker and Cache
                RouteResult routeResult = routingService.getDrivingRoute(start.getCoordinate(), end.getCoordinate(), sessionStats);

                if (routeResult.getStatus() == RouteStatus.CIRCUIT_OPEN) {
                    isDegraded = true;
                    double fallbackKm = aerialKm * config.getCircuitFallbackMultiplier();
                    String explanation = String.format(Locale.US,
                            "Routing circuit breaker is OPEN; fallback %.2fx aerial (%.3f km) applied; trip marked DEGRADED",
                            config.getCircuitFallbackMultiplier(), fallbackKm);

                    segmentResult = new SegmentResult(
                            i + 1, start, end, deltaSeconds, aerialKm, fallbackKm,
                            DistanceRule.DEGRADED_CIRCUIT_OPEN, explanation, false);

                } else if (routeResult.getStatus() == RouteStatus.PROVIDER_ERROR) {
                    isDegraded = true;
                    double fallbackKm = aerialKm * config.getCircuitFallbackMultiplier();
                    String explanation = String.format(Locale.US,
                            "Routing provider error: %s; fallback %.2fx aerial (%.3f km) applied; trip marked DEGRADED",
                            routeResult.getErrorMessage(), config.getCircuitFallbackMultiplier(), fallbackKm);

                    segmentResult = new SegmentResult(
                            i + 1, start, end, deltaSeconds, aerialKm, fallbackKm,
                            DistanceRule.DEGRADED_PROVIDER_ERROR, explanation, false);

                } else if (routeResult.getStatus() == RouteStatus.RATE_LIMITED) {
                    isDegraded = true;
                    double fallbackKm = aerialKm * config.getCircuitFallbackMultiplier();
                    String explanation = String.format(Locale.US,
                            "Outbound routing call throttled by client-side rate limiter (%s); fallback %.2fx aerial (%.3f km) applied; trip marked DEGRADED",
                            routeResult.getErrorMessage(), config.getCircuitFallbackMultiplier(), fallbackKm);

                    segmentResult = new SegmentResult(
                            i + 1, start, end, deltaSeconds, aerialKm, fallbackKm,
                            DistanceRule.DEGRADED_RATE_LIMITED, explanation, false);

                } else {
                    // Provider succeeded or cache hit
                    double routedKm = routeResult.getDistanceKm();
                    double impliedRoutedSpeedKmh = deltaSeconds > 0 ? (routedKm / deltaSeconds) * 3600.0 : 0.0;
                    double aerial14SpeedKmh = deltaSeconds > 0 ? ((aerialKm * 1.4) / deltaSeconds) * 3600.0 : 0.0;

                    // Rejection criteria
                    if (routedKm > config.getMaxRoutingDistanceKm()) {
                        String explanation = String.format(Locale.US,
                                "Provider routed distance of %.2f km rejected (exceeds %.0f km max limit for single gap); straight-line applied",
                                routedKm, config.getMaxRoutingDistanceKm());

                        segmentResult = new SegmentResult(
                                i + 1, start, end, deltaSeconds, aerialKm, aerialKm,
                                DistanceRule.REJECTED_MAX_DISTANCE, explanation, false);

                    } else if (impliedRoutedSpeedKmh > config.getTeleportSpeedLimitKmh() &&
                            aerial14SpeedKmh > config.getTeleportSpeedLimitKmh()) {
                        String explanation = String.format(Locale.US,
                                "Provider routed distance implied speed %.1f km/h > %.0f km/h and 1.4x aerial speed %.1f km/h also > %.0f km/h; rejected; straight-line applied",
                                impliedRoutedSpeedKmh, config.getTeleportSpeedLimitKmh(),
                                aerial14SpeedKmh, config.getTeleportSpeedLimitKmh());

                        segmentResult = new SegmentResult(
                                i + 1, start, end, deltaSeconds, aerialKm, aerialKm,
                                DistanceRule.REJECTED_SPEED, explanation, false);

                    } else {
                        // Accept routed driving distance
                        String explanation = String.format(Locale.US,
                                "Driving distance of %.3f km retrieved (%s) for gap of %ds",
                                routedKm, (routeResult.getStatus() == RouteStatus.CACHE_HIT ? "cache hit" : "live provider"), deltaSeconds);

                        segmentResult = new SegmentResult(
                                i + 1, start, end, deltaSeconds, aerialKm, routedKm,
                                DistanceRule.ROUTED, explanation, true);
                    }
                }
            }

            segments.add(segmentResult);

            BigDecimal segDist = BigDecimal.valueOf(segmentResult.getFinalDistanceKm()).setScale(3, RoundingMode.HALF_UP);
            runningTotal = runningTotal.add(segDist);

            // Accumulate statistics per rule
            DistanceRule r = segmentResult.getRule();
            ruleDistanceMap.put(r, ruleDistanceMap.getOrDefault(r, BigDecimal.ZERO).add(segDist));
            ruleCountMap.put(r, ruleCountMap.getOrDefault(r, 0) + 1);
        }

        // Final total rounded to billing decimal places (default 2 decimals)
        double totalBilledKm = runningTotal.setScale(config.getBillingDecimalPlaces(), RoundingMode.HALF_UP).doubleValue();

        Map<DistanceRule, Double> finalDistanceByRule = new EnumMap<>(DistanceRule.class);
        for (Map.Entry<DistanceRule, BigDecimal> entry : ruleDistanceMap.entrySet()) {
            finalDistanceByRule.put(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP).doubleValue());
        }

        ProviderCallStats providerStats = ProviderCallStats.of(
                sessionStats.getCacheHits(),
                sessionStats.getCacheMisses(),
                sessionStats.getFailures()
        );

        return new SegmentCalculationResult(
                segments,
                totalBilledKm,
                isDegraded,
                finalDistanceByRule,
                ruleCountMap,
                providerStats
        );
    }
}
