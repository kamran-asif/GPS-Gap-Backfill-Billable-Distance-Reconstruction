package com.moveinsync.distance.service;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.entity.TripAdjustmentEntity;
import com.moveinsync.distance.entity.TripEntity;
import com.moveinsync.distance.entity.TripSegmentEntity;
import com.moveinsync.distance.engine.TripDistanceRebuildingEngine;
import com.moveinsync.distance.monitoring.EngineMetrics;
import com.moveinsync.distance.repository.TripAdjustmentRepository;
import com.moveinsync.distance.repository.TripRepository;
import com.moveinsync.distance.routing.RoutingService;
import com.moveinsync.distance.security.TenantContext;
import com.moveinsync.distance.security.TenantFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class TripDistanceService {
    private static final Logger log = LoggerFactory.getLogger(TripDistanceService.class);

    private final TripRepository tripRepository;
    private final TripAdjustmentRepository adjustmentRepository;
    private final TripDistanceRebuildingEngine engine;
    private final RoutingService routingService;
    private final EngineMetrics metrics;

    public TripDistanceService(TripRepository tripRepository,
                               TripAdjustmentRepository adjustmentRepository,
                               RoutingService routingService,
                               EngineMetrics metrics) {
        this.tripRepository = tripRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.routingService = routingService;
        this.metrics = metrics;
        this.engine = new TripDistanceRebuildingEngine(routingService);
    }

    public TripDistanceRebuildingEngine getEngine() {
        return engine;
    }

    public RoutingService getRoutingService() {
        return routingService;
    }

    @Transactional
    public TripDistanceResult calculateAndSave(TripEvidence evidence) {
        String activeTenant = TenantContext.getTenantId();
        if (activeTenant == null || activeTenant.isBlank()) {
            activeTenant = evidence.getTenantId();
        }

        // Enforce tenant scoping
        String tenantId = activeTenant;
        Instant start = Instant.now();

        // Determine version
        Optional<TripEntity> latestOpt = tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(evidence.getTripId(), tenantId);
        int targetVersion = latestOpt.map(t -> t.getVersion() + 1).orElse(1);

        TripDistanceResult result = engine.calculateTripDistance(evidence, targetVersion);
        Duration elapsed = Duration.between(start, Instant.now());

        // Map and persist entity
        TripEntity entity = mapToEntity(result, evidence);
        tripRepository.save(entity);

        // Record metrics
        metrics.recordTripProcessed(result.getStatus() == TripStatus.DEGRADED, elapsed);
        metrics.recordRoutingStats(
                result.getProviderStats().getCacheHits(),
                result.getProviderStats().getCacheMisses(),
                result.getProviderStats().getCallsFailed()
        );
        metrics.recordTeleportDropped(result.getDroppedTeleportCount());
        metrics.recordLowAccuracyDropped(result.getDroppedAccuracyCount());

        log.info("Persisted trip distance: tripId={}, tenantId={}, v{}, distance={} km, status={}",
                result.getTripId(), result.getTenantId(), result.getVersion(),
                result.getTotalDistanceKm(), result.getStatus());

        return result;
    }

    @Transactional
    public TripDistanceRebuildingEngine.RecomputeExecutionResult recomputeTrip(String tripId,
                                                                               TripEvidence updatedEvidence,
                                                                               String reason) {
        String tenantId = getEnforcedTenantId();

        TripEntity existing = tripRepository.findTopByTripIdAndTenantIdOrderByVersionDesc(tripId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found for id: " + tripId + " in tenant: " + tenantId));

        TripDistanceResult previousResult = mapFromEntity(existing);

        TripDistanceRebuildingEngine.RecomputeExecutionResult recomputeResult =
                engine.recomputeWithLateEvidence(previousResult, updatedEvidence, reason);

        // Save new version trip
        TripEntity newEntity = mapToEntity(recomputeResult.getNewTripResult(), updatedEvidence);
        newEntity.setStatus(TripStatus.RECOMPUTED.name());
        tripRepository.save(newEntity);

        // Save billing adjustment record (credit or debit note)
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

    @Transactional
    public int reconcileDegradedTrips() {
        String tenantId = getEnforcedTenantId();
        List<TripEntity> degradedTrips = tripRepository.findAllByStatusAndTenantId(TripStatus.DEGRADED.name(), tenantId);
        log.info("Found {} degraded trips pending reconciliation for tenant: {}", degradedTrips.size(), tenantId);
        return degradedTrips.size();
    }

    private String getEnforcedTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return TenantFilter.DEFAULT_TENANT_ID;
        }
        return tenantId;
    }

    private TripEntity mapToEntity(TripDistanceResult result, TripEvidence evidence) {
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
                entity.getCalculatedAt()
        );
    }
}
