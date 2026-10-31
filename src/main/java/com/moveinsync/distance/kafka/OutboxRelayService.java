package com.moveinsync.distance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.distance.entity.OutboxEventEntity;
import com.moveinsync.distance.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional Outbox Relay: Relays pending financial adjustment records to Kafka.
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private TripKafkaProducer kafkaProducer;

    public OutboxRelayService(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void recordAdjustmentEvent(TripEvents.AdjustmentEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEventEntity entity = new OutboxEventEntity(
                    "TRIP_ADJUSTMENT", event.tripId(), event.tenantId(),
                    event.adjustmentType(), payload
            );
            outboxRepository.save(entity);
            log.debug("Recorded outbox adjustment event for tripId={}", event.tripId());
        } catch (Exception e) {
            log.error("Failed to write to outbox_events: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public int relayPendingEvents() {
        List<OutboxEventEntity> pending = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        if (pending.isEmpty()) return 0;

        int publishedCount = 0;
        for (OutboxEventEntity event : pending) {
            try {
                if (kafkaProducer != null) {
                    TripEvents.AdjustmentEvent adj = objectMapper.readValue(event.getPayload(), TripEvents.AdjustmentEvent.class);
                    kafkaProducer.publishAdjustment(adj);
                }
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                publishedCount++;
            } catch (Exception e) {
                log.warn("Failed to publish outbox event id={}: {}", event.getId(), e.getMessage());
                event.setStatus("FAILED");
            }
        }
        if (publishedCount > 0) {
            log.info("Outbox relay published {} events", publishedCount);
        }
        return publishedCount;
    }
}

