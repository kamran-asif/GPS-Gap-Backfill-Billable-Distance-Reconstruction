package com.moveinsync.distance.kafka;

import com.moveinsync.distance.domain.DistanceRule;
import com.moveinsync.distance.domain.ProviderCallStats;
import com.moveinsync.distance.domain.TripDistanceResult;
import com.moveinsync.distance.domain.TripStatus;
import com.moveinsync.distance.service.TripCalculationExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaBufferServiceTest {

    private TripKafkaBufferService bufferService;
    private AtomicBoolean calculationExecuted;

    @BeforeEach
    void setUp() {
        calculationExecuted = new AtomicBoolean(false);
        Instant t0 = Instant.parse("2026-10-31T06:00:00Z");

        TripCalculationExecutor stubExecutor = (evidence, idempotencyKey) -> {
            calculationExecuted.set(true);
            return new TripDistanceResult(
                    evidence.getTripId(), evidence.getTenantId(), 1, 15.50, TripStatus.COMPLETED,
                    "v1.2.0", Collections.emptyList(),
                    Map.of(DistanceRule.ROUTED, 15.50), Map.of(DistanceRule.ROUTED, 2),
                    ProviderCallStats.zero(), 2, 2, 0, 0, 1, t0.plusSeconds(1200), 5L
            );
        };

        bufferService = new TripKafkaBufferService(stubExecutor);
    }

    @Test
    @DisplayName("Should buffer incoming Kafka pings and trigger trip distance calculation on completion event")
    void shouldBufferAndCompleteTrip() {
        String tripId = "TRIP-KAFKA-100";
        String tenantId = "tenant-kafka-client";
        Instant t0 = Instant.parse("2026-10-31T06:00:00Z");

        // 1. Buffer pings
        bufferService.addPing(new TripEvents.GpsPingEvent(tripId, tenantId, 12.9352, 77.6245, t0, 10.0));
        bufferService.addPing(new TripEvents.GpsPingEvent(tripId, tenantId, 12.9250, 77.6100, t0.plusSeconds(300), 12.0));
        bufferService.addMilestone(new TripEvents.MilestoneEvent(tripId, tenantId, "DROP", "EMP-1", 12.88, 77.58, t0.plusSeconds(600), "PLANNED", "COMPLETED"));

        assertThat(bufferService.getBufferedPingCount(tripId)).isEqualTo(2);

        // 2. Complete trip
        TripDistanceResult result = bufferService.completeTrip(new TripEvents.TripCompleteEvent(tripId, tenantId, t0.plusSeconds(1200)));

        assertThat(result).isNotNull();
        assertThat(result.getTripId()).isEqualTo(tripId);
        assertThat(result.getTotalDistanceKm()).isEqualTo(15.50);
        assertThat(bufferService.getBufferedPingCount(tripId)).isEqualTo(0);
        assertThat(calculationExecuted.get()).isTrue();
    }
}
