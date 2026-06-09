package com.core.common.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Entity representing an outbox event to be processed and dispatched to Kafka.
 */
@Document(collection = "outbox_events")
public class OutboxEvent {

    @Id
    private String id;
    
    private String eventId;
    private String topic;
    private String payload;
    private String tenantId;
    
    @Indexed
    private String status; // PENDING, PUBLISHED, FAILED
    
    private int attempts;
    
    @Indexed
    private Instant createdAt;

    public OutboxEvent() {
    }

    public OutboxEvent(String id, String eventId, String topic, String payload, String tenantId, String status, int attempts, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.topic = topic;
        this.payload = payload;
        this.tenantId = tenantId;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
