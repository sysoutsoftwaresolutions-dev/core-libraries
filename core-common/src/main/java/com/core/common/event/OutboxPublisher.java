package com.core.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Service to save outgoing Kafka events into the transactional database outbox.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final MongoTemplate mongoTemplate;

    public OutboxPublisher(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Enqueues an event to the outbox table.
     *
     * @param eventId  the business unique ID of the event
     * @param topic    the Kafka topic to publish to
     * @param payload  the serialized JSON payload of the event
     * @param tenantId the tenant ID scope
     */
    public void enqueue(String eventId, String topic, String payload, String tenantId) {
        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                eventId,
                topic,
                payload,
                tenantId != null ? tenantId : "default",
                "PENDING",
                0,
                Instant.now()
        );
        mongoTemplate.save(outboxEvent);
        log.info("[OutboxPublisher] Enqueued event [{}] to outbox for topic [{}] under tenant [{}]", 
                eventId, topic, tenantId);
    }
}
