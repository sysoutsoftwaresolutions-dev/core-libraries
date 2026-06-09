package com.core.common.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Shared document model representing a processed Kafka event ID to prevent duplicate actions (idempotency).
 */
@Document(collection = "processed_events")
public class ProcessedEvent {

    @Id
    private String id; // Stores the eventId (UUID)

    @Indexed(expireAfter = "30d") // TTL index for automatic expiry after 30 days
    private Instant processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String id, Instant processedAt) {
        this.id = id;
        this.processedAt = processedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
