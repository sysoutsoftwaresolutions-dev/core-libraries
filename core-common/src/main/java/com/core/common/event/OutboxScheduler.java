package com.core.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Opt-in scheduled background runner that processes enqueued Outbox events,
 * publishes them to Kafka, and handles transient errors.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "core.outbox.scheduler.enabled", havingValue = "true")
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.beans.factory.annotation.Value("${core.outbox.scheduler.batch-size:50}")
    private int batchSize;

    @org.springframework.beans.factory.annotation.Value("${core.outbox.scheduler.max-attempts:3}")
    private int maxAttempts;

    public OutboxScheduler(MongoTemplate mongoTemplate, KafkaTemplate<String, String> kafkaTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Periodically queries and processes pending outbox event table entries.
     */
    @Scheduled(fixedDelayString = "${core.outbox.scheduler.polling-interval-ms:1000}")
    public void processOutbox() {
        try {
            // Query PENDING or FAILED events that have attempts < maxAttempts
            Query query = new Query();
            query.addCriteria(Criteria.where("status").in("PENDING", "FAILED")
                    .and("attempts").lt(maxAttempts));
            query.limit(batchSize);

            List<OutboxEvent> pendingEvents = mongoTemplate.find(query, OutboxEvent.class);
            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("[OutboxScheduler] Found {} pending/failed events to process", pendingEvents.size());

            for (OutboxEvent event : pendingEvents) {
                // Increment attempts and save immediately to guard against double publishing in crash loop
                event.setAttempts(event.getAttempts() + 1);
                mongoTemplate.save(event);

                try {
                    Message<String> message = MessageBuilder
                            .withPayload(event.getPayload())
                            .setHeader(KafkaHeaders.TOPIC, event.getTopic())
                            .setHeader(KafkaHeaders.KEY, event.getEventId())
                            .setHeader("X-Tenant-ID", event.getTenantId())
                            .build();

                    // Synchronous get to ensure Kafka broker fully acknowledges before status update
                    kafkaTemplate.send(message).get(5, java.util.concurrent.TimeUnit.SECONDS);

                    event.setStatus("PUBLISHED");
                    mongoTemplate.save(event);
                    log.info("[OutboxScheduler] Event [{}] successfully published to Kafka topic [{}]", event.getEventId(), event.getTopic());
                } catch (Exception e) {
                    log.error("[OutboxScheduler] Failed to publish event [{}] (attempt {}/{}): {}", 
                            event.getEventId(), event.getAttempts(), maxAttempts, e.getMessage());
                    
                    event.setStatus("FAILED");
                    mongoTemplate.save(event);
                }
            }
        } catch (Exception e) {
            log.error("[OutboxScheduler] Error running outbox publisher task: {}", e.getMessage());
        }
    }
}
