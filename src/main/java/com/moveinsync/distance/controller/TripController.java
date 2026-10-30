package com.moveinsync.distance.controller;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.dto.*;
import com.moveinsync.distance.entity.TripAdjustmentEntity;
import com.moveinsync.distance.entity.TripEntity;
import com.moveinsync.distance.entity.TripSegmentEntity;
import com.moveinsync.distance.engine.TripDistanceRebuildingEngine;
import com.moveinsync.distance.security.TenantContext;
import com.moveinsync.distance.service.TripBatchService;
import com.moveinsync.distance.service.TripDistanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripDistanceService tripService;
    private final TripBatchService tripBatchService;

    public TripController(TripDistanceService tripService, TripBatchService tripBatchService) {
        this.tripService = tripService;
        this.tripBatchService = tripBatchService;
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TripCalculationResponse> calculateTripDistance(
            @RequestBody TripCalculationRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (request.getTripId() == null || request.getTripId().isBlank()) {
            throw new IllegalArgumentException("tripId is required");
        }

        TripEvidence evidence = mapRequestToEvidence(request);
        TripDistanceResult result = tripService.calculateAndSave(evidence, idempotencyKey);

        return ResponseEntity.ok(mapResultToResponse(result));
    }

    @PostMapping("/batch-calculate")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<BatchCalculationResponse> batchCalculateTripDistances(
            @RequestBody List<TripCalculationRequest> requests,
            @RequestParam(defaultValue = "4") int workers) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }

        List<TripEvidence> evidences = new ArrayList<>(requests.size());
        for (TripCalculationRequest request : requests) {
            if (request.getTripId() == null || request.getTripId().isBlank()) {
                throw new IllegalArgumentException("tripId is required for every batch item");
            }
            evidences.add(mapRequestToEvidence(request));
        }

        return ResponseEntity.ok(tripBatchService.calculateInParallel(evidences, workers));
    }

    @PostMapping("/{tripId}/recompute")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<RecomputeResponse> recomputeTrip(@PathVariable String tripId,
                                                           @RequestBody RecomputeRequest request) {
        List<GpsPing> latePings = new ArrayList<>();
        if (request.getLatePings() != null) {
            for (GpsPingDto dto : request.getLatePings()) {
                latePings.add(new GpsPing(Coordinate.of(dto.getLatitude(), dto.getLongitude()), dto.getTimestamp(), dto.getAccuracy()));
            }
        }

        List<TripMilestone> milestones = new ArrayList<>();
        if (request.getUpdatedMilestones() != null) {
            for (MilestoneDto md : request.getUpdatedMilestones()) {
                Coordinate coord = (md.getLatitude() != null && md.getLongitude() != null) ?
                        Coordinate.of(md.getLatitude(), md.getLongitude()) : null;
                milestones.add(new TripMilestone(
                        MilestoneType.valueOf(md.getType()),
                        md.getEmployeeId(),
                        coord,
                        md.getTimestamp(),
                        md.getRiderType() != null ? RiderType.valueOf(md.getRiderType()) : RiderType.PLANNED,
                        md.getStatus() != null ? MilestoneStatus.valueOf(md.getStatus()) : MilestoneStatus.COMPLETED
                ));
            }
        }

        TripDistanceRebuildingEngine.RecomputeExecutionResult execResult =
                tripService.recomputeTripWithLatePings(tripId, latePings, milestones, request.getReason());

        RecomputeResponse response = new RecomputeResponse();
        RecomputeDelta delta = execResult.getDelta();
        response.setTripId(delta.getTripId());
        response.setTenantId(delta.getTenantId());
        response.setPreviousVersion(delta.getPreviousVersion());
        response.setNewVersion(delta.getNewVersion());
        response.setPreviousDistanceKm(delta.getPreviousDistanceKm());
        response.setNewDistanceKm(delta.getNewDistanceKm());
        response.setDeltaKm(delta.getDeltaKm());
        response.setFormattedDelta(delta.getFormattedDelta());
        response.setAdjustmentType(delta.getAdjustmentType().name());
        response.setReason(delta.getReason());
        response.setCalculatedAt(delta.getCreatedAt());
        response.setNewTripResult(mapResultToResponse(execResult.getNewTripResult()));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tripId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<TripCalculationResponse> getTripDetails(@PathVariable String tripId,
                                                                  @RequestParam(required = false) Integer version) {
        TripEntity entity = tripService.getTrip(tripId, version)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        TripCalculationResponse response = new TripCalculationResponse();
        response.setTripId(entity.getTripId());
        response.setTenantId(entity.getTenantId());
        response.setVersion(entity.getVersion());
        response.setTotalDistanceKm(entity.getTotalDistanceKm());
        response.setStatus(entity.getStatus());
        response.setRuleVersion(entity.getRuleVersion());
        response.setInputPingCount(entity.getRawPingsCount());
        response.setCleanedPingCount(entity.getCleanedPingsCount());
        response.setDroppedTeleportCount(entity.getTeleportDroppedCount());
        response.setDroppedAccuracyCount(entity.getLowAccuracyDroppedCount());
        response.setAnchorsInsertedCount(entity.getAnchorsInsertedCount());
        response.setCalculatedAt(entity.getCalculatedAt());

        ProviderCallStats stats = new ProviderCallStats(
                entity.getProviderCallsAttempted(),
                entity.getProviderCacheHits(),
                entity.getProviderCacheMisses(),
                entity.getProviderCallsFailed(),
                0.005
        );
        response.setProviderStats(stats);

        List<SegmentResponseDto> segDtos = new ArrayList<>();
        if (entity.getSegments() != null) {
            for (TripSegmentEntity se : entity.getSegments()) {
                SegmentResponseDto sd = new SegmentResponseDto();
                sd.setSegmentIndex(se.getSegmentIndex());
                sd.setStartLatitude(se.getStartLatitude());
                sd.setStartLongitude(se.getStartLongitude());
                sd.setStartTime(se.getStartTime());
                sd.setStartType(se.getStartType());
                sd.setStartDescription(se.getStartDescription());
                sd.setEndLatitude(se.getEndLatitude());
                sd.setEndLongitude(se.getEndLongitude());
                sd.setEndTime(se.getEndTime());
                sd.setEndType(se.getEndType());
                sd.setEndDescription(se.getEndDescription());
                sd.setDeltaSeconds(se.getDeltaSeconds());
                sd.setImpliedAerialSpeedKmh(se.getImpliedAerialSpeedKmh());
                sd.setAerialDistanceKm(se.getAerialDistanceKm());
                sd.setFinalDistanceKm(se.getFinalDistanceKm());
                sd.setRule(se.getRule());
                sd.setExplanation(se.getExplanation());
                sd.setRouted(se.isRouted());
                segDtos.add(sd);
            }
        }
        response.setSegments(segDtos);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tripId}/adjustments")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<TripAdjustmentEntity>> getAdjustments(@PathVariable String tripId) {
        return ResponseEntity.ok(tripService.getAdjustments(tripId));
    }

    private TripEvidence mapRequestToEvidence(TripCalculationRequest request) {
        String tenantId = request.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.getTenantId();
        }

        List<GpsPing> pings = new ArrayList<>();
        if (request.getRawPings() != null) {
            for (GpsPingDto dto : request.getRawPings()) {
                pings.add(new GpsPing(Coordinate.of(dto.getLatitude(), dto.getLongitude()), dto.getTimestamp(), dto.getAccuracy()));
            }
        }

        List<TripMilestone> milestones = new ArrayList<>();
        if (request.getMilestones() != null) {
            for (MilestoneDto md : request.getMilestones()) {
                Coordinate coord = (md.getLatitude() != null && md.getLongitude() != null) ?
                        Coordinate.of(md.getLatitude(), md.getLongitude()) : null;
                milestones.add(new TripMilestone(
                        MilestoneType.valueOf(md.getType()),
                        md.getEmployeeId(),
                        coord,
                        md.getTimestamp(),
                        md.getRiderType() != null ? RiderType.valueOf(md.getRiderType()) : RiderType.PLANNED,
                        md.getStatus() != null ? MilestoneStatus.valueOf(md.getStatus()) : MilestoneStatus.COMPLETED
                ));
            }
        }

        List<PlannedStop> plannedStops = new ArrayList<>();
        if (request.getPlannedStops() != null) {
            for (PlannedStopDto ps : request.getPlannedStops()) {
                plannedStops.add(new PlannedStop(
                        ps.getEmployeeId(),
                        Coordinate.of(ps.getLatitude(), ps.getLongitude()),
                        ps.getPlannedTime(),
                        MilestoneType.valueOf(ps.getStopType())
                ));
            }
        }

        OfficeLocation office = null;
        if (request.getOfficeLocation() != null) {
            office = new OfficeLocation(
                    request.getOfficeLocation().getOfficeId(),
                    request.getOfficeLocation().getName(),
                    Coordinate.of(request.getOfficeLocation().getLatitude(), request.getOfficeLocation().getLongitude())
            );
        }

        Map<String, EmployeeProfile> profileMap = new HashMap<>();
        if (request.getEmployeeProfiles() != null) {
            for (EmployeeProfileDto ep : request.getEmployeeProfiles()) {
                Coordinate home = (ep.getHomeLatitude() != null && ep.getHomeLongitude() != null) ?
                        Coordinate.of(ep.getHomeLatitude(), ep.getHomeLongitude()) : null;
                profileMap.put(ep.getEmployeeId(), new EmployeeProfile(ep.getEmployeeId(), home, ep.getSignInTime()));
            }
        }

        Coordinate tripEndActual = (request.getTripEndActualLatitude() != null && request.getTripEndActualLongitude() != null) ?
                Coordinate.of(request.getTripEndActualLatitude(), request.getTripEndActualLongitude()) : null;

        EngineConfig config = request.getConfig() != null ? request.getConfig().toDomain() : EngineConfig.defaultConfig();

        return new TripEvidence(
                request.getTripId(),
                tenantId,
                pings,
                milestones,
                plannedStops,
                office,
                profileMap,
                request.getTripStartTime(),
                request.getTripEndTime(),
                tripEndActual,
                config
        );
    }

    private TripCalculationResponse mapResultToResponse(TripDistanceResult result) {
        TripCalculationResponse response = new TripCalculationResponse();
        response.setTripId(result.getTripId());
        response.setTenantId(result.getTenantId());
        response.setVersion(result.getVersion());
        response.setTotalDistanceKm(result.getTotalDistanceKm());
        response.setStatus(result.getStatus().name());
        response.setRuleVersion(result.getRuleVersion());
        response.setDistanceByRuleKm(result.getDistanceByRuleKm());
        response.setCountByRule(result.getCountByRule());
        response.setProviderStats(result.getProviderStats());
        response.setInputPingCount(result.getInputPingCount());
        response.setCleanedPingCount(result.getCleanedPingCount());
        response.setDroppedTeleportCount(result.getDroppedTeleportCount());
        response.setDroppedAccuracyCount(result.getDroppedAccuracyCount());
        response.setAnchorsInsertedCount(result.getAnchorsInsertedCount());
        response.setCalculatedAt(result.getCalculatedAt());

        List<SegmentResponseDto> segDtos = new ArrayList<>();
        for (SegmentResult sr : result.getSegments()) {
            SegmentResponseDto sd = new SegmentResponseDto();
            sd.setSegmentIndex(sr.getSegmentIndex());
            sd.setStartLatitude(sr.getStartPoint().getCoordinate().getLatitude());
            sd.setStartLongitude(sr.getStartPoint().getCoordinate().getLongitude());
            sd.setStartTime(sr.getStartPoint().getTimestamp());
            sd.setStartType(sr.getStartPoint().getType().name());
            sd.setStartDescription(sr.getStartPoint().getSourceDescription());
            sd.setEndLatitude(sr.getEndPoint().getCoordinate().getLatitude());
            sd.setEndLongitude(sr.getEndPoint().getCoordinate().getLongitude());
            sd.setEndTime(sr.getEndPoint().getTimestamp());
            sd.setEndType(sr.getEndPoint().getType().name());
            sd.setEndDescription(sr.getEndPoint().getSourceDescription());
            sd.setDeltaSeconds(sr.getDeltaSeconds());
            sd.setImpliedAerialSpeedKmh(sr.getImpliedAerialSpeedKmh());
            sd.setAerialDistanceKm(sr.getAerialDistanceKm());
            sd.setFinalDistanceKm(sr.getFinalDistanceKm());
            sd.setRule(sr.getRule().name());
            sd.setExplanation(sr.getExplanation());
            sd.setRouted(sr.isRouted());
            segDtos.add(sd);
        }
        response.setSegments(segDtos);

        return response;
    }
}
