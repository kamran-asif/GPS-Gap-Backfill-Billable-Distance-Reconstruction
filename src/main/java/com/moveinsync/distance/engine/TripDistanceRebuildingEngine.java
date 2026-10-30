package com.moveinsync.distance.engine;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.routing.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Master Trip Distance Rebuilding Engine.
 *
 * Implements:
 * Stage I: Ingest & Clean Stream (Deduplicate, Order, Low Accuracy, Teleport jumps)
 * Stage II: Patch Gaps with Anchor Points (Employee, No-Show, Office 400m)
 * Stage III: Distance for Every Segment (Short gap aerial, Parked, Routing with Circuit Breaker, Rejections)
 * Stage IV: Cost, Failure & Re-computation (Cache by snapped cells, Idempotent recomputation, Signed Delta)
 */
public final class TripDistanceRebuildingEngine {
    private static final Logger log = LoggerFactory.getLogger(TripDistanceRebuildingEngine.class);

    private final RoutingService routingService;

    public TripDistanceRebuildingEngine(RoutingService routingService) {
        this.routingService = Objects.requireNonNull(routingService, "RoutingService must not be null");
    }

    /**
     * Executes distance rebuilding calculation for a trip.
     * Guaranteed deterministic: same input + same rule version => identical kilometres.
     */
    public TripDistanceResult calculateTripDistance(TripEvidence evidence, int version) {
        Objects.requireNonNull(evidence, "TripEvidence must not be null");
        EngineConfig config = evidence.getConfig() != null ? evidence.getConfig() : EngineConfig.defaultConfig();

        log.debug("Starting trip distance calculation for tripId={}, tenantId={}, version={}, ruleVersion={}",
                evidence.getTripId(), evidence.getTenantId(), version, config.getRuleVersion());

        // Stage I: Clean the GPS stream
        StreamCleaner cleaner = new StreamCleaner(config);
        StreamCleanerResult cleanerResult = cleaner.clean(evidence.getRawPings());

        // Stage II: Patch gaps with business anchors
        AnchorPatchEngine patchEngine = new AnchorPatchEngine(config);
        List<AnchorPoint> unifiedPoints = patchEngine.patchAndMerge(cleanerResult.getCleanedPings(), evidence);

        int anchorsInserted = Math.max(0, unifiedPoints.size() - cleanerResult.getCleanedPings().size());

        // Stage III: Calculate distance for every segment
        SegmentCalculatorEngine calculator = new SegmentCalculatorEngine(config, routingService);
        SegmentCalculatorEngine.SegmentCalculationResult calcResult = calculator.calculate(unifiedPoints);

        TripStatus status = calcResult.isDegraded() ? TripStatus.DEGRADED : TripStatus.COMPLETED;

        log.debug("Trip calculation complete: tripId={}, distance={} km, status={}, segments={}, cacheHitRate={}%",
                evidence.getTripId(), calcResult.getTotalDistanceKm(), status,
                calcResult.getSegments().size(), calcResult.getProviderStats().getCacheHitRatePercentage());

        return new TripDistanceResult(
                evidence.getTripId(),
                evidence.getTenantId(),
                version,
                calcResult.getTotalDistanceKm(),
                status,
                config.getRuleVersion(),
                calcResult.getSegments(),
                calcResult.getDistanceByRuleKm(),
                calcResult.getCountByRule(),
                calcResult.getProviderStats(),
                evidence.getRawPings().size(),
                cleanerResult.getCleanedPings().size(),
                cleanerResult.getTeleportCount(),
                cleanerResult.getLowAccuracyCount(),
                anchorsInserted,
                Instant.now(),
                0L // Will be set by service layer
        );
    }

    /**
     * Recomputes a trip when late pings arrive after billing or provider recovers.
     * Produces a signed delta (+1.8 km) instead of silently mutating the closed invoice.
     */
    public RecomputeExecutionResult recomputeWithLateEvidence(TripDistanceResult previousResult,
                                                              TripEvidence updatedEvidence,
                                                              String recomputeReason) {
        Objects.requireNonNull(previousResult, "previousResult must not be null");
        Objects.requireNonNull(updatedEvidence, "updatedEvidence must not be null");

        int nextVersion = previousResult.getVersion() + 1;
        TripDistanceResult newResult = calculateTripDistance(updatedEvidence, nextVersion);

        RecomputeDelta delta = new RecomputeDelta(
                updatedEvidence.getTripId(),
                updatedEvidence.getTenantId(),
                previousResult.getVersion(),
                nextVersion,
                previousResult.getTotalDistanceKm(),
                newResult.getTotalDistanceKm(),
                recomputeReason != null ? recomputeReason : "LATE_PINGS_INGESTED",
                Instant.now()
        );

        log.info("Trip recomputed: tripId={}, v{} -> v{}, delta={}",
                updatedEvidence.getTripId(), previousResult.getVersion(), nextVersion, delta.getFormattedDelta());

        return new RecomputeExecutionResult(newResult, delta);
    }

    public static final class RecomputeExecutionResult {
        private final TripDistanceResult newTripResult;
        private final RecomputeDelta delta;

        public RecomputeExecutionResult(TripDistanceResult newTripResult, RecomputeDelta delta) {
            this.newTripResult = newTripResult;
            this.delta = delta;
        }

        public TripDistanceResult getNewTripResult() {
            return newTripResult;
        }

        public RecomputeDelta getDelta() {
            return delta;
        }
    }
}
