package com.moveinsync.distance.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka producer for emitting raw GPS pings, milestones, and trip completion events.
 * Crucially, all messages are keyed by tripId to ensure that all events for a given trip
 * are routed to the exact same Kafka partition for strict FIFO ordering.
 */
@Service
@ConditionalOnProperty(name = "engine.kafka.enabled", havingValue = "true")
public class TripKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(TripKafkaProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String gpsTopic;
    private final String adjustmentsTopic;

    public TripKafkaProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${engine.kafka.topics.gps-pings:trip-gps-pings}") String gpsTopic,
            @Value("${engine.kafka.topics.adjustments:trip-adjustments}") String adjustmentsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.gpsTopic = gpsTopic;
        this.adjustmentsTopic = adjustmentsTopic;
    }

    public void publishPing(TripEvents.GpsPingEvent ping) {
        // Message key is ping.tripId() for single-partition ordering
        kafkaTemplate.send(gpsTopic, ping.tripId(), ping);
        log.debug("Published GPS ping to Kafka: tripId={}, time={}", ping.tripId(), ping.timestamp());
    }

    public void publishMilestone(TripEvents.MilestoneEvent milestone) {
        kafkaTemplate.send(gpsTopic, milestone.tripId(), milestone);
        log.debug("Published milestone to Kafka: tripId={}, type={}", milestone.tripId(), milestone.type());
    }

    public void publishCompletion(TripEvents.TripCompleteEvent completion) {
        kafkaTemplate.send(gpsTopic, completion.tripId(), completion);
        log.info("Published TripCompleteEvent to Kafka: tripId={}", completion.tripId());
    }

    public void publishAdjustment(TripEvents.AdjustmentEvent adjustment) {
        kafkaTemplate.send(adjustmentsTopic, adjustment.tripId(), adjustment);
        log.info("Published signed adjustment event to Kafka: tripId={}, delta={}",
                adjustment.tripId(), adjustment.formattedDelta());
    }
}

