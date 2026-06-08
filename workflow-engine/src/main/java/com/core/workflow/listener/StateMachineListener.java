package com.core.workflow.listener;

import com.core.workflow.model.StepResult;
import java.util.Map;
import java.util.UUID;

/**
 * Listener interface to receive execution lifecycle callbacks for state machines.
 * Implementing beans will be automatically discovered and notified by the engine.
 */
public interface StateMachineListener {

    /**
     * Triggered when a state machine execution starts.
     *
     * @param executionId      the UUID of the run.
     * @param machineId        the ID of the workflow definition.
     * @param initialVariables the variables passed at startup.
     */
    void onStart(UUID executionId, String machineId, Map<String, Object> initialVariables);

    /**
     * Triggered before a step begins execution.
     *
     * @param executionId the UUID of the run.
     * @param stepId      the ID of the step.
     * @param variables   the current state variables.
     */
    void onStepStart(UUID executionId, String stepId, Map<String, Object> variables);

    /**
     * Triggered after a step completes successfully.
     *
     * @param executionId the UUID of the run.
     * @param stepId      the ID of the step.
     * @param result      the step execution result.
     */
    void onStepSuccess(UUID executionId, String stepId, StepResult result);

    /**
     * Triggered when a step fails (even before retries are exhausted).
     *
     * @param executionId  the UUID of the run.
     * @param stepId       the ID of the step.
     * @param errorMessage the error message.
     */
    void onStepFailure(UUID executionId, String stepId, String errorMessage);

    /**
     * Triggered when a state machine run completes successfully.
     *
     * @param executionId the UUID of the run.
     * @param machineId   the ID of the workflow definition.
     * @param variables   the final state variables.
     */
    void onComplete(UUID executionId, String machineId, Map<String, Object> variables);

    /**
     * Triggered when a state machine run fails completely.
     *
     * @param executionId  the UUID of the run.
     * @param machineId    the ID of the workflow definition.
     * @param errorMessage the error message.
     */
    void onFailure(UUID executionId, String machineId, String errorMessage);
}
