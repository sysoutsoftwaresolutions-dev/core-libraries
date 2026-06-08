package com.core.workflow.model;

import java.util.List;

/**
 * Exception thrown when the current context does not possess the roles required to execute a workflow.
 */
public class WorkflowSecurityException extends WorkflowException {
    
    private final List<String> requiredRoles;

    public WorkflowSecurityException(String message, List<String> requiredRoles) {
        super(message, "SECURITY_UNAUTHORIZED");
        this.requiredRoles = requiredRoles;
    }

    public List<String> getRequiredRoles() {
        return requiredRoles;
    }
}
