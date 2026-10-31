package com.moveinsync.distance.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Event consumer listening to partition-ordered Kafka topics.
 */
@Service
@ConditionalOnProperty(name = "engine.kafka.enabled", havingValue = "true")
public class TripKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TripKafkaConsumer.class);

    private final TripKafkaBufferService bufferService;

    public TripKafkaConsumer(TripKafkaBufferService bufferService) {
        this.bufferService = bufferService;
    }

    @KafkaListener(topics = "${engine.kafka.topics.gps-pings:trip-gps-pings}", groupId = "${spring.kafka.consumer.group-id:moveinsync-trip-distance-group}")
    public void onGpsMessage(Object payload) {
        log.debug("Received Kafka payload: {}", payload);
        if (payload instanceof TripEvents.GpsPingEvent ping) {
            bufferService.addPing(ping);
        } else if (payload instanceof TripEvents.MilestoneEvent milestone) {
            bufferService.addMilestone(milestone);
        } else if (payload instanceof TripEvents.TripCompleteEvent complete) {
            bufferService.completeTrip(complete);
        } else {
            log.warn("Unknown message type in Kafka consumer: {}", payload != null ? payload.getClass().getName() : "null");
        }
    }
}
