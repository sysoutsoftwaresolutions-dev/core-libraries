package com.core.workflow.kafka;

import com.core.utils.json.JsonUtils;
import com.core.workflow.engine.StateMachineEngine;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.registry.WorkflowRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer that listens on the "workflow-trigger" topic.
 * Deserializes execution payloads and triggers the workflow engine asynchronously.
 */
@Component
public class WorkflowKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowKafkaConsumer.class);

    private final StateMachineEngine engine;
    private final WorkflowRegistry registry;

    public WorkflowKafkaConsumer(StateMachineEngine engine, WorkflowRegistry registry) {
        this.engine = engine;
        this.registry = registry;
    }

    /**
     * Consumes message from trigger topic.
     *
     * @param messageJson serialized trigger request
     */
    @KafkaListener(topics = "workflow-trigger", groupId = "workflow-engine-group")
    public void consumeTrigger(String messageJson) {
        log.info("[Kafka-Consumer] Intercepted workflow trigger request: {}", messageJson);
        try {
            WorkflowTriggerRequest request = JsonUtils.fromJson(messageJson, WorkflowTriggerRequest.class);
            if (request == null || request.getWorkflowId() == null) {
                log.error("[Kafka-Consumer] Invalid payload: missing 'workflowId'.");
                return;
            }

            StateMachineDefinition definition = registry.get(request.getWorkflowId());
            if (definition == null) {
                log.error("[Kafka-Consumer] Workflow [{}] is not registered. Cannot trigger execution.", 
                        request.getWorkflowId());
                return;
            }

            log.info("[Kafka-Consumer] Dispatching workflow [{}] execution asynchronously...", request.getWorkflowId());
            engine.executeAsync(definition, request.getVariables())
                    .thenAccept(context -> log.info("[Kafka-Consumer] Async execution finished. ID: {}", context.getExecutionId()))
                    .exceptionally(ex -> {
                        log.error("[Kafka-Consumer] Async execution failed for workflow [{}]", request.getWorkflowId(), ex);
                        return null;
                    });
        } catch (Exception e) {
            log.error("[Kafka-Consumer] Failed to process message payload", e);
        }
    }

    /**
     * Data Transfer Object representing a workflow trigger message.
     */
    public static class WorkflowTriggerRequest {
        private String workflowId;
        private Map<String, Object> variables;

        public WorkflowTriggerRequest() {}

        public WorkflowTriggerRequest(String workflowId, Map<String, Object> variables) {
            this.workflowId = workflowId;
            this.variables = variables;
        }

        public String getWorkflowId() {
            return workflowId;
        }

        public void setWorkflowId(String workflowId) {
            this.workflowId = workflowId;
        }

        public Map<String, Object> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, Object> variables) {
            this.variables = variables;
        }
    }
}
