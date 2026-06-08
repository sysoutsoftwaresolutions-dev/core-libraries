package com.core.workflow.executor;

import com.core.common.exception.CoreException;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.model.StepResult;

/**
 * Interface to be implemented by all component classes that execute workflow steps.
 * Each implementing class should be registered as a Spring Bean (e.g. annotated with @Component).
 */
public interface StepExecutor {
    
    /**
     * Executes the logic of a single workflow step.
     *
     * @param context the execution context containing state data and variables.
     * @return the StepResult containing completion status and output variables.
     * @throws CoreException if an error occurs during execution.
     */
    StepResult execute(StepExecutionContext context) throws CoreException;
}
