package com.core.workflow.model;

/**
 * Exception thrown when static validation of a workflow definition fails.
 */
public class WorkflowValidationException extends WorkflowException {
    
    public WorkflowValidationException(String message) {
        super(message, "WORKFLOW_VALIDATION_FAILED");
    }

    public WorkflowValidationException(String message, Throwable cause) {
        super(message, "WORKFLOW_VALIDATION_FAILED", cause);
    }
}
