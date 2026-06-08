package com.core.workflow.persistence;

import java.util.Map;
import java.util.UUID;

/**
 * Representation of a state machine execution state to be persisted.
 */
public record WorkflowState(
    UUID executionId,
    String machineId,
    String currentStepId,
    Map<String, Object> variables,
    boolean completed,
    boolean failed,
    String errorMessage
) {
}
