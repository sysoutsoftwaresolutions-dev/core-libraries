package com.core.workflow.listener;

import com.core.workflow.model.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Default listener that logs execution events for debugging and auditing purposes.
 */
public class LoggingStateMachineListener implements StateMachineListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingStateMachineListener.class);

    @Override
    public void onStart(UUID executionId, String machineId, Map<String, Object> initialVariables) {
        log.info("[Workflow-Engine] Starting workflow [{}] (Execution ID: {}) with variables: {}", 
                machineId, executionId, initialVariables);
    }

    @Override
    public void onStepStart(UUID executionId, String stepId, Map<String, Object> variables) {
        log.info("[Workflow-Engine] Execution {}: Starting step [{}] with variables: {}", 
                executionId, stepId, variables);
    }

    @Override
    public void onStepSuccess(UUID executionId, String stepId, StepResult result) {
        log.info("[Workflow-Engine] Execution {}: Step [{}] completed successfully. Status: {}. Output: {}", 
                executionId, stepId, result.status(), result.outputVariables());
    }

    @Override
    public void onStepFailure(UUID executionId, String stepId, String errorMessage) {
        log.warn("[Workflow-Engine] Execution {}: Step [{}] failed. Error: {}", 
                executionId, stepId, errorMessage);
    }

    @Override
    public void onComplete(UUID executionId, String machineId, Map<String, Object> variables) {
        log.info("[Workflow-Engine] Workflow [{}] completed successfully (Execution ID: {}). Final variables: {}", 
                machineId, executionId, variables);
    }

    @Override
    public void onFailure(UUID executionId, String machineId, String errorMessage) {
        log.error("[Workflow-Engine] Workflow [{}] failed (Execution ID: {}). Error: {}", 
                machineId, executionId, errorMessage);
    }
}
