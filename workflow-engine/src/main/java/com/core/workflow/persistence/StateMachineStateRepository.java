package com.core.workflow.persistence;

import java.util.Optional;
import java.util.UUID;

/**
 * Pluggable repository interface to store and retrieve state machine execution states.
 */
public interface StateMachineStateRepository {

    /**
     * Persists or updates the state of a workflow execution.
     *
     * @param state the workflow state to save.
     */
    void save(WorkflowState state);

    /**
     * Retrieves the persisted state of a workflow execution.
     *
     * @param executionId the execution UUID.
     * @return an Optional containing the state if found.
     */
    Optional<WorkflowState> find(UUID executionId);
}
