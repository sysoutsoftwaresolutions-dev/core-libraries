package com.core.workflow.model;

import java.util.Collections;
import java.util.Map;

/**
 * Result returned by a StepExecutor after executing its logic.
 */
public record StepResult(
    StepStatus status,
    Map<String, Object> outputVariables,
    String errorMessage,
    String nextStepOverride
) {
    public StepResult {
        if (outputVariables == null) {
            outputVariables = Collections.emptyMap();
        }
    }

    public static StepResult success() {
        return new StepResult(StepStatus.SUCCESS, Collections.emptyMap(), null, null);
    }

    public static StepResult success(Map<String, Object> outputVariables) {
        return new StepResult(StepStatus.SUCCESS, outputVariables, null, null);
    }

    public static StepResult successWithOverride(Map<String, Object> outputVariables, String nextStepOverride) {
        return new StepResult(StepStatus.SUCCESS, outputVariables, null, nextStepOverride);
    }

    public static StepResult failure(String errorMessage) {
        return new StepResult(StepStatus.FAILED, Collections.emptyMap(), errorMessage, null);
    }

    public static StepResult failure(String errorMessage, Map<String, Object> outputVariables) {
        return new StepResult(StepStatus.FAILED, outputVariables, errorMessage, null);
    }
}
