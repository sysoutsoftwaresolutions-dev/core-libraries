package com.core.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Service guard to check and register event idempotency in Kafka consumer streams.
 * Uses MongoTemplate directly to perform atomic checks for duplicate events.
 */
public class EventProcessingGuard {

    private static final Logger log = LoggerFactory.getLogger(EventProcessingGuard.class);
    private final MongoTemplate mongoTemplate;

    public EventProcessingGuard(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Atomically validates if the given eventId under tenantId has already been successfully processed.
     * If not, marks it as processed and returns true. If it has been processed, returns false.
     *
     * @param tenantId the tenant ID scoped to this event
     * @param eventId  unique identifier of the incoming event (UUID)
     * @return true if the event is unique and should be processed; false if it is a duplicate.
     */
    public boolean shouldProcess(String tenantId, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("[EventProcessingGuard] Received event with empty eventId. Processing allowed by default.");
            return true;
        }

        String actualTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        String compoundKey = actualTenant + ":" + eventId;

        try {
            mongoTemplate.insert(new ProcessedEvent(compoundKey, Instant.now()));
            log.info("[EventProcessingGuard] Registered event [{}] under tenant [{}] successfully as processed.", eventId, actualTenant);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.warn("[EventProcessingGuard] Duplicate event detected. Event [{}] under tenant [{}] has already been processed.", eventId, actualTenant);
            return false;
        } catch (Exception e) {
            log.error("[EventProcessingGuard] Database error verifying idempotency for event [{}] under tenant [{}]: {}", eventId, actualTenant, e.getMessage());
            throw e;
        }
    }
}
