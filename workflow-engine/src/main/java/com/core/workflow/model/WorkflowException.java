package com.core.workflow.model;

import com.core.common.exception.CoreException;

/**
 * Base exception class for all workflow and state-machine-related issues.
 */
public class WorkflowException extends CoreException {
    
    public WorkflowException(String message, String errorCode) {
        super(message, errorCode);
    }

    public WorkflowException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
