package com.core.workflow.model;

/**
 * Exception thrown when a runtime error occurs during the execution of a workflow.
 */
public class WorkflowExecutionException extends WorkflowException {
    
    private final String stepId;

    public WorkflowExecutionException(String message, String stepId) {
        super(message, "WORKFLOW_EXECUTION_FAILED");
        this.stepId = stepId;
    }

    public WorkflowExecutionException(String message, String stepId, Throwable cause) {
        super(message, "WORKFLOW_EXECUTION_FAILED", cause);
        this.stepId = stepId;
    }

    public String getStepId() {
        return stepId;
    }
}
