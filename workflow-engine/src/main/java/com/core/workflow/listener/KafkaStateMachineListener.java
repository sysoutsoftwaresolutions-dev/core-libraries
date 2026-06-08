package com.core.workflow.listener;

import com.core.utils.json.JsonUtils;
import com.core.workflow.model.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow lifecycle listener that publishes state machine events to a Kafka topic.
 * Uses an ObjectProvider to degrade gracefully if Kafka is not configured in the host context.
 */
@Component
public class KafkaStateMachineListener implements StateMachineListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaStateMachineListener.class);
    private static final String TOPIC = "workflow-events";

    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    public KafkaStateMachineListener(ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
    }

    private void publishEvent(String eventType, UUID executionId, String machineId, Map<String, Object> details) {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.debug("[Kafka-Listener] KafkaTemplate bean not present. Skipping Kafka broadcast for event: {}", eventType);
            return;
        }

        Map<String, Object> message = new HashMap<>();
        message.put("eventType", eventType);
        message.put("executionId", executionId.toString());
        message.put("machineId", machineId);
        message.put("timestamp", System.currentTimeMillis());
        if (details != null) {
            message.put("details", details);
        }

        try {
            String jsonPayload = JsonUtils.toJson(message);
            // Publish message asynchronously with executionId as partition key
            kafkaTemplate.send(TOPIC, executionId.toString(), jsonPayload);
            log.debug("[Kafka-Listener] Successfully published event [{}] for execution: {}", eventType, executionId);
        } catch (Exception e) {
            log.error("[Kafka-Listener] Failed to publish event [{}] to Kafka for execution: {}", eventType, executionId, e);
        }
    }

    @Override
    public void onStart(UUID executionId, String machineId, Map<String, Object> initialVariables) {
        publishEvent("WORKFLOW_STARTED", executionId, machineId, Map.of("variables", initialVariables));
    }

    @Override
    public void onStepStart(UUID executionId, String stepId, Map<String, Object> variables) {
        publishEvent("STEP_STARTED", executionId, null, Map.of("stepId", stepId, "variables", variables));
    }

    @Override
    public void onStepSuccess(UUID executionId, String stepId, StepResult result) {
        publishEvent("STEP_SUCCESS", executionId, null, Map.of(
                "stepId", stepId,
                "status", result.status().name(),
                "outputs", result.outputVariables()
        ));
    }

    @Override
    public void onStepFailure(UUID executionId, String stepId, String errorMessage) {
        publishEvent("STEP_FAILURE", executionId, null, Map.of(
                "stepId", stepId,
                "errorMessage", errorMessage != null ? errorMessage : "Unknown step failure"
        ));
    }

    @Override
    public void onComplete(UUID executionId, String machineId, Map<String, Object> variables) {
        publishEvent("WORKFLOW_COMPLETED", executionId, machineId, Map.of("variables", variables));
    }

    @Override
    public void onFailure(UUID executionId, String machineId, String errorMessage) {
        publishEvent("WORKFLOW_FAILED", executionId, machineId, Map.of(
                "errorMessage", errorMessage != null ? errorMessage : "Unknown workflow failure"
        ));
    }
}
