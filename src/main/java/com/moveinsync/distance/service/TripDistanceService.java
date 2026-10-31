package com.moveinsync.distance.service;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.entity.TripAdjustmentEntity;
import com.moveinsync.distance.entity.TripEntity;
import com.moveinsync.distance.entity.TripSegmentEntity;
import com.moveinsync.distance.engine.TripDistanceRebuildingEngine;
import com.moveinsync.distance.monitoring.EngineMetrics;
import com.moveinsync.distance.repository.TripAdjustmentRepository;
import com.moveinsync.distance.repository.TripRepository;
import com.moveinsync.distance.routing.RoutingCircuitBreaker;
import com.moveinsync.distance.routing.RoutingService;
import com.moveinsync.distance.security.TenantContext;
import com.moveinsync.distance.security.TenantFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TripDistanceService implements TripCalculationExecutor {
    private static final Logger log = LoggerFactory.getLogger(TripDistanceService.class);

    private final TripRepository tripRepository;
    private final TripAdjustmentRepository adjustmentRepository;
    private final TripDistanceRebuildingEngine engine;
    private final RoutingService routingService;
    private final EngineMetrics metrics;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.moveinsync.distance.kafka.OutboxRelayService outboxRelayService;

    public TripDistanceService(TripRepository tripRepository,
                               TripAdjustmentRepository adjustmentRepository,
                               RoutingService routingService,
                               EngineMetrics metrics,
                               PlatformTransactionManager transactionManager) {
        this.tripRepository = tripRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.routingService = routingService;
        this.metrics = metrics;
        this.engine = new TripDistanceRebuildingEngine(routingService);
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    public TripDistanceRebuildingEngine getEngine() {
        return engine;
    }

    public RoutingService getRoutingService() {
        return routingService;
    }

    public TripDistanceResult calculateAndSave(TripEvidence evidence) {
        return calculateAndSave(evidence, null);
    }

    /**
     * Production-grade trip calculation:
     * 1. Checks optional X-Idempotency-Key inside a lightweight read transaction.
     * 2. Executes CPU + External Routing I/O OUTSIDE any DB transaction (prevents HikariCP connection starvation).
     * 3. Commits the versioned TripEntity + raw TripEvidence snapshot in a narrow write transaction.
     */
    public TripDistanceResult calculateAndSave(TripEvidence evidence, String idempotencyKey) {
        String activeTenant = TenantContext.getTenantId();
        if (activeTenant == null || activeTenant.isBlank()) {
            activeTenant = evidence.getTenantId();
        }
        final String tenantId = activeTenant;
        final String normalizedKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;

        // Step 1: Fast read-only lookup for idempotency & version number
        if (normalizedKey != null) {
            Optional<TripDistanceResult> existingIdempotent = readTx.execute(status ->
                    tripRepository.findFirstByTripIdAndTenantIdAndIdempotencyKeyOrderByVersionDesc(
                                    evidence.getTripId(), tenantId, normalizedKey)
                            .map(this::mapFromEntity)
            );
            if (existingIdempotent != null && existingIdempotent.isPresent()) {
                log.info("Idempotent replay hit for tripId={}, tenantId={}, idempotencyKey={}",
                        evidence.getTripId(), tenantId, normalizedKey);
                return existingIdempotent.get();
            }
        }

        Integer nextVersion = readTx.execute(status ->
                tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(evidence.getTripId(), tenantId)
                        .map(t -> t.getVersion() + 1)
                        .orElse(1)
        );
        int targetVersion = nextVersion != null ? nextVersion : 1;

        // Step 2: Execute CPU + External Routing I/O OUTSIDE DB Transaction
        Instant start = Instant.now();
        TripDistanceResult result = engine.calculateTripDistance(evidence, targetVersion);
        Duration elapsed = Duration.between(start, Instant.now());
        long processingTimeMs = elapsed.toMillis();

        TripDistanceResult resultWithTiming = new TripDistanceResult(
                result.getTripId(),
                tenantId,
                result.getVersion(),
                result.getTotalDistanceKm(),
                result.getStatus(),
                result.getRuleVersion(),
                result.getSegments(),
                result.getDistanceByRuleKm(),
                result.getCountByRule(),
                result.getProviderStats(),
                result.getInputPingCount(),
                result.getCleanedPingCount(),
                result.getDroppedTeleportCount(),
                result.getDroppedAccuracyCount(),
                result.getAnchorsInsertedCount(),
                result.getCalculatedAt(),
                processingTimeMs
        );

        // Step 3: Narrow write transaction (~2ms) to persist entity & raw evidence snapshot
        writeTx.executeWithoutResult(status -> {
            TripEntity entity = mapToEntity(resultWithTiming, evidence, normalizedKey);
            tripRepository.save(entity);
        });

        // Step 4: Record observability metrics
        metrics.recordTripProcessed(result.getStatus() == TripStatus.DEGRADED, elapsed);
        metrics.recordRoutingStats(
                result.getProviderStats().getCacheHits(),
                result.getProviderStats().getCacheMisses(),
                result.getProviderStats().getCallsFailed()
        );
        metrics.recordTeleportDropped(result.getDroppedTeleportCount());
        metrics.recordLowAccuracyDropped(result.getDroppedAccuracyCount());

        log.info("Persisted trip distance: tripId={}, tenantId={}, v{}, distance={} km, status={}, processingTime={}ms",
                result.getTripId(), tenantId, result.getVersion(),
                result.getTotalDistanceKm(), result.getStatus(), processingTimeMs);

        return resultWithTiming;
    }

    /**
     * Recomputes a trip by automatically loading the stored base TripEvidence snapshot
     * and merging incremental late pings + milestone updates.
     */
    public TripDistanceRebuildingEngine.RecomputeExecutionResult recomputeTripWithLatePings(
            String tripId,
            List<GpsPing> latePings,
            List<TripMilestone> updatedMilestones,
            String reason) {
        String tenantId = getEnforcedTenantId();

        TripEntity existing = readTx.execute(status ->
                tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(tripId, tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Trip not found for id: " + tripId + " in tenant: " + tenantId))
        );

        TripEvidence baseEvidence = TripEvidence.fromBase64(existing.getEvidenceSnapshot());
        TripEvidence mergedEvidence;
        if (baseEvidence != null) {
            mergedEvidence = baseEvidence.mergeWithLateEvidence(latePings, updatedMilestones);
        } else {
            mergedEvidence = new TripEvidence(
                    tripId,
                    tenantId,
                    latePings != null ? latePings : List.of(),
                    updatedMilestones != null ? updatedMilestones : List.of(),
                    List.of(),
                    null,
                    Map.of(),
                    null,
                    null,
                    null,
                    EngineConfig.defaultConfig()
            );
        }

        return recomputeTrip(tripId, mergedEvidence, reason);
    }

    /**
     * Executes recomputation outside the DB write lock, then atomically commits the new
     * immutable TripEntity version and signed TripAdjustmentEntity (CREDIT_NOTE / DEBIT_NOTE).
     */
    public TripDistanceRebuildingEngine.RecomputeExecutionResult recomputeTrip(String tripId,
                                                                               TripEvidence updatedEvidence,
                                                                               String reason) {
        String tenantId = getEnforcedTenantId();

        TripDistanceResult previousResult = readTx.execute(status -> {
            TripEntity existing = tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(tripId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found for id: " + tripId + " in tenant: " + tenantId));
            return mapFromEntity(existing);
        });

        // Compute outside DB transaction
        TripDistanceRebuildingEngine.RecomputeExecutionResult recomputeResult =
                engine.recomputeWithLateEvidence(previousResult, updatedEvidence, reason);

        // Persist new version + signed billing adjustment atomically in narrow transaction
        writeTx.executeWithoutResult(status -> {
            TripEntity newEntity = mapToEntity(recomputeResult.getNewTripResult(), updatedEvidence, null);
            if (recomputeResult.getNewTripResult().getStatus() == TripStatus.COMPLETED) {
                newEntity.setStatus(TripStatus.RECOMPUTED.name());
            }
            tripRepository.save(newEntity);

            RecomputeDelta delta = recomputeResult.getDelta();
            TripAdjustmentEntity adjustmentEntity = new TripAdjustmentEntity();
            adjustmentEntity.setTripId(tripId);
            adjustmentEntity.setTenantId(tenantId);
            adjustmentEntity.setPreviousVersion(delta.getPreviousVersion());
            adjustmentEntity.setNewVersion(delta.getNewVersion());
            adjustmentEntity.setPreviousDistanceKm(delta.getPreviousDistanceKm());
            adjustmentEntity.setNewDistanceKm(delta.getNewDistanceKm());
            adjustmentEntity.setDeltaKm(delta.getDeltaKm());
            adjustmentEntity.setFormattedDelta(delta.getFormattedDelta());
            adjustmentEntity.setAdjustmentType(delta.getAdjustmentType().name());
            adjustmentEntity.setReason(delta.getReason());
            adjustmentEntity.setCreatedAt(delta.getCreatedAt());

            adjustmentRepository.save(adjustmentEntity);

            if (outboxRelayService != null) {
                outboxRelayService.recordAdjustmentEvent(new com.moveinsync.distance.kafka.TripEvents.AdjustmentEvent(
                        tripId, tenantId, delta.getPreviousVersion(), delta.getNewVersion(),
                        delta.getPreviousDistanceKm(), delta.getNewDistanceKm(), delta.getDeltaKm(),
                        delta.getFormattedDelta(), delta.getAdjustmentType().name(), delta.getReason(),
                        delta.getCreatedAt()
                ));
            }
        });

        RecomputeDelta delta = recomputeResult.getDelta();
        log.info("Persisted recompute signed delta adjustment: tripId={}, delta={}, type={}, reason={}",
                tripId, delta.getFormattedDelta(), delta.getAdjustmentType(), reason);

        return recomputeResult;
    }

    @Transactional(readOnly = true)
    public Optional<TripEntity> getTrip(String tripId, Integer version) {
        String tenantId = getEnforcedTenantId();
        if (version != null) {
            return tripRepository.findByTripIdAndTenantIdAndVersion(tripId, tenantId, version);
        }
        return tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(tripId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<TripAdjustmentEntity> getAdjustments(String tripId) {
        String tenantId = getEnforcedTenantId();
        return adjustmentRepository.findAllByTripIdAndTenantIdOrderByCreatedAtDesc(tripId, tenantId);
    }

    /**
     * Automatically heals all trips whose latest version is currently in DEGRADED status
     * for the active tenant once the routing provider / circuit breaker is healthy.
     */
    public int reconcileDegradedTrips() {
        String tenantId = getEnforcedTenantId();
        if (routingService.getCircuitBreaker().getState() == RoutingCircuitBreaker.State.OPEN) {
            log.warn("Skipping degraded trip reconciliation for tenant={}: circuit breaker is still OPEN", tenantId);
            return 0;
        }

        List<TripEntity> degradedCandidates = readTx.execute(status ->
                tripRepository.findAllByStatusAndTenantId(TripStatus.DEGRADED.name(), tenantId)
        );
        if (degradedCandidates == null || degradedCandidates.isEmpty()) {
            return 0;
        }

        // Deduplicate by tripId and only heal if the LATEST version of that trip is still DEGRADED
        Set<String> processedTripIds = new HashSet<>();
        int healedCount = 0;

        for (TripEntity candidate : degradedCandidates) {
            String tripId = candidate.getTripId();
            if (!processedTripIds.add(tripId)) {
                continue;
            }
            Optional<TripEntity> latestOpt = readTx.execute(status ->
                    tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(tripId, tenantId)
            );
            if (latestOpt == null || latestOpt.isEmpty()) {
                continue;
            }
            TripEntity latest = latestOpt.get();
            if (!TripStatus.DEGRADED.name().equals(latest.getStatus())) {
                continue; // Already healed in a newer version
            }

            TripEvidence savedEvidence = TripEvidence.fromBase64(latest.getEvidenceSnapshot());
            if (savedEvidence == null) {
                continue;
            }

            TripDistanceRebuildingEngine.RecomputeExecutionResult healed =
                    recomputeTrip(tripId, savedEvidence, "PROVIDER_RECOVERY_AUTO_RECONCILE");
            if (healed.getNewTripResult().getStatus() != TripStatus.DEGRADED) {
                healedCount++;
            }
        }

        log.info("Auto-reconciled {} degraded trips for tenant: {}", healedCount, tenantId);
        return healedCount;
    }

    private String getEnforcedTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return TenantFilter.DEFAULT_TENANT_ID;
        }
        return tenantId;
    }

    private TripEntity mapToEntity(TripDistanceResult result, TripEvidence evidence, String idempotencyKey) {
        TripEntity entity = new TripEntity();
        entity.setTripId(result.getTripId());
        entity.setTenantId(result.getTenantId());
        entity.setVersion(result.getVersion());
        entity.setTotalDistanceKm(result.getTotalDistanceKm());
        entity.setStatus(result.getStatus().name());
        entity.setRuleVersion(result.getRuleVersion());
        entity.setRawPingsCount(result.getInputPingCount());
        entity.setCleanedPingsCount(result.getCleanedPingCount());
        entity.setTeleportDroppedCount(result.getDroppedTeleportCount());
        entity.setLowAccuracyDroppedCount(result.getDroppedAccuracyCount());
        entity.setAnchorsInsertedCount(result.getAnchorsInsertedCount());
        entity.setProviderCallsAttempted(result.getProviderStats().getCallsAttempted());
        entity.setProviderCacheHits(result.getProviderStats().getCacheHits());
        entity.setProviderCacheMisses(result.getProviderStats().getCacheMisses());
        entity.setProviderCallsFailed(result.getProviderStats().getCallsFailed());
        entity.setProviderCostUsd(result.getProviderStats().getTotalCostUsd());
        entity.setCalculatedAt(result.getCalculatedAt());
        entity.setIdempotencyKey(idempotencyKey);
        if (evidence != null) {
            entity.setEvidenceSnapshot(evidence.toBase64());
        }

        List<TripSegmentEntity> segmentEntities = new ArrayList<>();
        for (SegmentResult seg : result.getSegments()) {
            TripSegmentEntity se = new TripSegmentEntity();
            se.setTripId(result.getTripId());
            se.setTenantId(result.getTenantId());
            se.setVersion(result.getVersion());
            se.setSegmentIndex(seg.getSegmentIndex());
            se.setStartLatitude(seg.getStartPoint().getCoordinate().getLatitude());
            se.setStartLongitude(seg.getStartPoint().getCoordinate().getLongitude());
            se.setStartTime(seg.getStartPoint().getTimestamp());
            se.setStartType(seg.getStartPoint().getType().name());
            se.setStartDescription(seg.getStartPoint().getSourceDescription());
            se.setEndLatitude(seg.getEndPoint().getCoordinate().getLatitude());
            se.setEndLongitude(seg.getEndPoint().getCoordinate().getLongitude());
            se.setEndTime(seg.getEndPoint().getTimestamp());
            se.setEndType(seg.getEndPoint().getType().name());
            se.setEndDescription(seg.getEndPoint().getSourceDescription());
            se.setDeltaSeconds(seg.getDeltaSeconds());
            se.setImpliedAerialSpeedKmh(seg.getImpliedAerialSpeedKmh());
            se.setAerialDistanceKm(seg.getAerialDistanceKm());
            se.setFinalDistanceKm(seg.getFinalDistanceKm());
            se.setRule(seg.getRule().name());
            se.setExplanation(seg.getExplanation());
            se.setRouted(seg.isRouted());
            segmentEntities.add(se);
        }
        entity.setSegments(segmentEntities);
        return entity;
    }

    private TripDistanceResult mapFromEntity(TripEntity entity) {
        List<SegmentResult> segments = new ArrayList<>();
        if (entity.getSegments() != null) {
            for (TripSegmentEntity se : entity.getSegments()) {
                AnchorPoint startPoint = new AnchorPoint(
                        Coordinate.of(se.getStartLatitude(), se.getStartLongitude()),
                        se.getStartTime(),
                        AnchorType.valueOf(se.getStartType()),
                        se.getStartDescription()
                );
                AnchorPoint endPoint = new AnchorPoint(
                        Coordinate.of(se.getEndLatitude(), se.getEndLongitude()),
                        se.getEndTime(),
                        AnchorType.valueOf(se.getEndType()),
                        se.getEndDescription()
                );
                segments.add(new SegmentResult(
                        se.getSegmentIndex(),
                        startPoint,
                        endPoint,
                        se.getDeltaSeconds(),
                        se.getAerialDistanceKm(),
                        se.getFinalDistanceKm(),
                        DistanceRule.valueOf(se.getRule()),
                        se.getExplanation(),
                        se.isRouted()
                ));
            }
        }

        ProviderCallStats stats = new ProviderCallStats(
                entity.getProviderCallsAttempted(),
                entity.getProviderCacheHits(),
                entity.getProviderCacheMisses(),
                entity.getProviderCallsFailed(),
                0.005
        );

        return new TripDistanceResult(
                entity.getTripId(),
                entity.getTenantId(),
                entity.getVersion(),
                entity.getTotalDistanceKm(),
                TripStatus.valueOf(entity.getStatus()),
                entity.getRuleVersion(),
                segments,
                Map.of(),
                Map.of(),
                stats,
                entity.getRawPingsCount(),
                entity.getCleanedPingsCount(),
                entity.getTeleportDroppedCount(),
                entity.getLowAccuracyDroppedCount(),
                entity.getAnchorsInsertedCount(),
                entity.getCalculatedAt(),
                0L
        );
    }
}
