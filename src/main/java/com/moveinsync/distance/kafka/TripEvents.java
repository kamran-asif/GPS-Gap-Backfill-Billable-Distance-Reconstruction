package com.moveinsync.distance.kafka;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event payloads for event-driven stream ingestion and outbox publishing.
 */
public final class TripEvents {

    private TripEvents() {}

    public record GpsPingEvent(
            String tripId,
            String tenantId,
            double latitude,
            double longitude,
            Instant timestamp,
            Double accuracy
    ) implements Serializable {}

    public record MilestoneEvent(
            String tripId,
            String tenantId,
            String type,
            String employeeId,
            Double latitude,
            Double longitude,
            Instant timestamp,
            String riderType,
            String status
    ) implements Serializable {}

    public record TripCompleteEvent(
            String tripId,
            String tenantId,
            Instant completedAt
    ) implements Serializable {}

    public record AdjustmentEvent(
            String tripId,
            String tenantId,
            int previousVersion,
            int newVersion,
            double previousDistanceKm,
            double newDistanceKm,
            double deltaKm,
            String formattedDelta,
            String adjustmentType,
            String reason,
            Instant createdAt
    ) implements Serializable {}
}
