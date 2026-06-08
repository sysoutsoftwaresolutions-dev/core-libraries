package com.core.workflow.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory implementation of {@link StateMachineStateRepository}.
 * Used as the default implementation.
 */
public class InMemoryStateRepository implements StateMachineStateRepository {

    private final Map<UUID, WorkflowState> store = new ConcurrentHashMap<>();

    @Override
    public void save(WorkflowState state) {
        if (state != null && state.executionId() != null) {
            store.put(state.executionId(), state);
        }
    }

    @Override
    public Optional<WorkflowState> find(UUID executionId) {
        if (executionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(executionId));
    }
}
