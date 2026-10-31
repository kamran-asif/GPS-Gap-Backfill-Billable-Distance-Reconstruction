package com.moveinsync.distance.kafka;

import com.moveinsync.distance.domain.*;
import com.moveinsync.distance.security.TenantContext;
import com.moveinsync.distance.service.TripCalculationExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory accumulator that buffers Kafka pings and milestones by tripId.
 * When a TripCompleteEvent is received, reconstructs TripEvidence
 * and invokes TripCalculationExecutor.
 */
@Service
public class TripKafkaBufferService {

    private static final Logger log = LoggerFactory.getLogger(TripKafkaBufferService.class);

    private final TripCalculationExecutor calculationExecutor;

    // Buffer keyed by tripId
    private final Map<String, List<GpsPing>> pingsBuffer = new ConcurrentHashMap<>();
    private final Map<String, List<TripMilestone>> milestonesBuffer = new ConcurrentHashMap<>();
    private final Map<String, String> tenantMap = new ConcurrentHashMap<>();

    public TripKafkaBufferService(TripCalculationExecutor calculationExecutor) {
        this.calculationExecutor = calculationExecutor;
    }

    public void addPing(TripEvents.GpsPingEvent event) {
        Coordinate coord = Coordinate.of(event.latitude(), event.longitude());
        GpsPing ping = new GpsPing(coord, event.timestamp(), event.accuracy() != null ? event.accuracy() : 10.0);

        pingsBuffer.computeIfAbsent(event.tripId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(ping);
        tenantMap.put(event.tripId(), event.tenantId());
        log.debug("Buffered Kafka ping for tripId={}, currentCount={}", event.tripId(), pingsBuffer.get(event.tripId()).size());
    }

    public void addMilestone(TripEvents.MilestoneEvent event) {
        Coordinate coord = (event.latitude() != null && event.longitude() != null) ?
                Coordinate.of(event.latitude(), event.longitude()) : null;
        MilestoneType type = event.type() != null ? MilestoneType.valueOf(event.type()) : MilestoneType.PICKUP;
        RiderType riderType = event.riderType() != null ? RiderType.valueOf(event.riderType()) : RiderType.PLANNED;
        MilestoneStatus status = event.status() != null ? MilestoneStatus.valueOf(event.status()) : MilestoneStatus.COMPLETED;

        TripMilestone milestone = new TripMilestone(type, event.employeeId(), coord, event.timestamp(), riderType, status);
        milestonesBuffer.computeIfAbsent(event.tripId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(milestone);
        tenantMap.put(event.tripId(), event.tenantId());
    }

    public TripDistanceResult completeTrip(TripEvents.TripCompleteEvent event) {
        String tripId = event.tripId();
        String tenantId = tenantMap.getOrDefault(tripId, event.tenantId());

        List<GpsPing> pings = pingsBuffer.remove(tripId);
        List<TripMilestone> milestones = milestonesBuffer.remove(tripId);
        tenantMap.remove(tripId);

        if (pings == null || pings.isEmpty()) {
            log.warn("TripCompleteEvent received for tripId={} but ping buffer was empty", tripId);
            return null;
        }

        TripEvidence evidence = new TripEvidence(
                tripId,
                tenantId,
                pings,
                milestones != null ? milestones : Collections.emptyList(),
                Collections.emptyList(),
                null,
                Collections.emptyMap(),
                null,
                event.completedAt(),
                null,
                EngineConfig.defaultConfig()
        );

        try {
            TenantContext.setTenantId(tenantId);
            log.info("Processing Kafka-completed trip: tripId={}, pings={}", tripId, pings.size());
            return calculationExecutor.calculateAndSave(evidence, "kafka-stream");
        } finally {
            TenantContext.clear();
        }
    }

    public int getBufferedPingCount(String tripId) {
        List<GpsPing> list = pingsBuffer.get(tripId);
        return list != null ? list.size() : 0;
    }
}
