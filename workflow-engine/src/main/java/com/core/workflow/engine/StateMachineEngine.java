package com.core.workflow.engine;

import com.core.common.exception.CoreException;
import com.core.workflow.context.DefaultStepExecutionContext;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.executor.StepExecutor;
import com.core.workflow.listener.StateMachineListener;
import com.core.workflow.model.*;
import com.core.workflow.persistence.StateMachineStateRepository;
import com.core.workflow.persistence.WorkflowState;
import com.core.workflow.security.WorkflowSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread-safe engine that executes JSON-defined state machines.
 * Handles SpEL routing, error retries, lifecycle notifications, and persistence.
 * Leverages Java 21 virtual threads for asynchronous scaling.
 */
@Service
public class StateMachineEngine {

    private static final Logger log = LoggerFactory.getLogger(StateMachineEngine.class);

    private final ApplicationContext applicationContext;
    private final ConditionEvaluator conditionEvaluator;
    private final WorkflowSecurityService securityService;
    private final StateMachineStateRepository stateRepository;
    private final List<StateMachineListener> listeners;
    private final ExecutorService virtualThreadExecutor;

    public StateMachineEngine(ApplicationContext applicationContext,
                              ConditionEvaluator conditionEvaluator,
                              WorkflowSecurityService securityService,
                              StateMachineStateRepository stateRepository,
                              List<StateMachineListener> listeners) {
        this.applicationContext = applicationContext;
        this.conditionEvaluator = conditionEvaluator;
        this.securityService = securityService;
        this.stateRepository = stateRepository;
        this.listeners = listeners;
        // Java 21: Setup a virtual thread per task executor for high-concurrency scalability
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Executes a state machine synchronously.
     *
     * @param definition       the state machine definition.
     * @param initialVariables the variables to populate the context with.
     * @return the final StepExecutionContext containing output variables.
     * @throws CoreException             if authorization fails, execution retries are exhausted, or internal error occurs.
     */
    public StepExecutionContext execute(StateMachineDefinition definition, Map<String, Object> initialVariables) throws CoreException {
        UUID executionId = UUID.randomUUID();
        
        // 1. Security check
        securityService.checkPermission(definition.getId(), definition.getRoles());

        // 2. Initialize context
        DefaultStepExecutionContext context = new DefaultStepExecutionContext(
                executionId,
                definition.getId(),
                definition.getStartStep(),
                initialVariables
        );

        // 3. Notify start
        notifyStart(executionId, definition.getId(), initialVariables);
        saveState(context, false, false, null);

        try {
            String currentStepId = definition.getStartStep();
            
            while (currentStepId != null && !currentStepId.strip().isEmpty()) {
                StepDefinition stepDef = definition.getStep(currentStepId);
                if (stepDef == null) {
                    throw new CoreException("Step definition not found for ID: " + currentStepId, "STEP_DEFINITION_NOT_FOUND");
                }

                context.setCurrentStepId(currentStepId);
                
                // Save current transition step
                saveState(context, false, false, null);

                // Execute step with retry mechanism
                StepResult result = executeStepWithRetry(stepDef, context);

                // Apply output variables to context
                context.setVariables(result.outputVariables());

                // Decide next step
                String nextStep = determineNextStep(stepDef, result, context);
                currentStepId = nextStep;
            }

            // Completed successfully
            notifyComplete(executionId, definition.getId(), context.getVariables());
            saveState(context, true, false, null);
            return context;

        } catch (CoreException e) {
            notifyFailure(executionId, definition.getId(), e.getMessage());
            saveState(context, false, true, e.getMessage());
            throw e;
        } catch (Throwable t) {
            notifyFailure(executionId, definition.getId(), t.getMessage());
            saveState(context, false, true, t.getMessage());
            throw new CoreException("Unexpected workflow failure: " + t.getMessage(), "WORKFLOW_UNEXPECTED_ERROR", t);
        }
    }

    /**
     * Executes a state machine asynchronously using Java 21 Virtual Threads.
     *
     * @param definition       the state machine definition.
     * @param initialVariables the variables to populate the context with.
     * @return a CompletableFuture with the final StepExecutionContext.
     */
    public CompletableFuture<StepExecutionContext> executeAsync(StateMachineDefinition definition, Map<String, Object> initialVariables) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(definition, initialVariables);
            } catch (CoreException e) {
                throw new RuntimeException("Async workflow execution failed", e);
            }
        }, virtualThreadExecutor);
    }

    private StepResult executeStepWithRetry(StepDefinition stepDef, DefaultStepExecutionContext context) throws CoreException {
        String stepId = stepDef.getId();
        StepExecutor executor = resolveExecutor(stepDef, stepId);

        int maxAttempts = Math.max(1, stepDef.getRetry().getMaxAttempts());
        long backoff = stepDef.getRetry().getBackoffPeriodMs();
        
        int attempt = 0;
        StepResult result = null;
        Throwable lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            notifyStepStart(context.getExecutionId(), stepId, context.getVariables());
            
            try {
                result = executor.execute(context);
                if (result.status() == StepStatus.SUCCESS) {
                    notifyStepSuccess(context.getExecutionId(), stepId, result);
                    return result;
                } else {
                    String error = result.errorMessage() != null ? result.errorMessage() : "Step execution returned status " + result.status();
                    lastException = new CoreException(error, "STEP_EXECUTION_FAILED");
                    notifyStepFailure(context.getExecutionId(), stepId, error);
                }
            } catch (CoreException e) {
                lastException = e;
                notifyStepFailure(context.getExecutionId(), stepId, e.getMessage());
            } catch (Throwable t) {
                lastException = new CoreException("Runtime error in step execution: " + t.getMessage(), "STEP_RUNTIME_ERROR", t);
                notifyStepFailure(context.getExecutionId(), stepId, t.getMessage());
            }

            if (attempt < maxAttempts) {
                log.info("[Workflow-Engine] Execution {}: Step [{}] failed on attempt {}/{}. Retrying in {}ms...", 
                        context.getExecutionId(), stepId, attempt, maxAttempts, backoff);
                if (backoff > 0) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CoreException("Workflow step execution interrupted during retry backoff sleep", "RETRIES_INTERRUPTED", e);
                    }
                }
            }
        }

        // Retries exhausted
        if (lastException instanceof CoreException) {
            throw (CoreException) lastException;
        }
        throw new CoreException("Step [" + stepId + "] failed after " + maxAttempts + " attempts.", "STEP_RETRIES_EXHAUSTED", lastException);
    }

    private StepExecutor resolveExecutor(StepDefinition stepDef, String stepId) throws CoreException {
        try {
            return applicationContext.getBean(stepDef.getResource(), StepExecutor.class);
        } catch (Exception e) {
            // Try to load by class name
            try {
                Class<?> clazz = Class.forName(stepDef.getResource());
                if (StepExecutor.class.isAssignableFrom(clazz)) {
                    return (StepExecutor) applicationContext.getBean(clazz);
                } else {
                    throw new CoreException("Class " + stepDef.getResource() + " does not implement StepExecutor interface.", "STEP_EXECUTOR_INTERFACE_MISMATCH");
                }
            } catch (Exception ex) {
                throw new CoreException(
                        String.format("Unable to resolve StepExecutor for resource '%s' in step '%s'. " +
                                "Ensure it is a valid Spring bean name or class path.", stepDef.getResource(), stepId), 
                        "STEP_EXECUTOR_RESOLVE_ERROR", ex);
            }
        }
    }

    private String determineNextStep(StepDefinition stepDef, StepResult result, StepExecutionContext context) {
        // 1. Check runtime nextStep override from the execution result
        if (result.nextStepOverride() != null && !result.nextStepOverride().strip().isEmpty()) {
            return result.nextStepOverride();
        }

        // 2. Evaluate conditional transitions
        if (stepDef.getTransitions() != null && !stepDef.getTransitions().isEmpty()) {
            for (TransitionDefinition transition : stepDef.getTransitions()) {
                if (conditionEvaluator.evaluate(transition.getCondition(), context)) {
                    return transition.getNextStep();
                }
            }
        }

        // 3. Fallback to default nextStep
        return stepDef.getNextStep();
    }

    private void saveState(StepExecutionContext context, boolean completed, boolean failed, String errorMessage) {
        try {
            WorkflowState state = new WorkflowState(
                    context.getExecutionId(),
                    context.getMachineId(),
                    context.getCurrentStepId(),
                    context.getVariables(),
                    completed,
                    failed,
                    errorMessage
            );
            stateRepository.save(state);
        } catch (Exception e) {
            log.error("Failed to persist state for execution ID: {}", context.getExecutionId(), e);
        }
    }

    // Listener notifications helper methods
    private void notifyStart(UUID executionId, String machineId, Map<String, Object> vars) {
        listeners.forEach(l -> {
            try { l.onStart(executionId, machineId, vars); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyStepStart(UUID executionId, String stepId, Map<String, Object> vars) {
        listeners.forEach(l -> {
            try { l.onStepStart(executionId, stepId, vars); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyStepSuccess(UUID executionId, String stepId, StepResult result) {
        listeners.forEach(l -> {
            try { l.onStepSuccess(executionId, stepId, result); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyStepFailure(UUID executionId, String stepId, String error) {
        listeners.forEach(l -> {
            try { l.onStepFailure(executionId, stepId, error); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyComplete(UUID executionId, String machineId, Map<String, Object> vars) {
        listeners.forEach(l -> {
            try { l.onComplete(executionId, machineId, vars); } catch (Exception e) { log.error("Listener error", e); }
        });
    }

    private void notifyFailure(UUID executionId, String machineId, String error) {
        listeners.forEach(l -> {
            try { l.onFailure(executionId, machineId, error); } catch (Exception e) { log.error("Listener error", e); }
        });
    }
}
