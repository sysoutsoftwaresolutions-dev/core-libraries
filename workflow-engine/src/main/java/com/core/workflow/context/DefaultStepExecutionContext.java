package com.core.workflow.context;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link StepExecutionContext} using a concurrent map.
 */
public class DefaultStepExecutionContext implements StepExecutionContext {

    private final UUID executionId;
    private final String machineId;
    private String currentStepId;
    private final Map<String, Object> variables;

    public DefaultStepExecutionContext(UUID executionId, String machineId, String currentStepId, Map<String, Object> initialVariables) {
        this.executionId = executionId != null ? executionId : UUID.randomUUID();
        this.machineId = machineId;
        this.currentStepId = currentStepId;
        this.variables = new ConcurrentHashMap<>();
        if (initialVariables != null) {
            this.variables.putAll(initialVariables);
        }
    }

    @Override
    public UUID getExecutionId() {
        return executionId;
    }

    @Override
    public String getMachineId() {
        return machineId;
    }

    @Override
    public String getCurrentStepId() {
        return currentStepId;
    }

    /**
     * Package-private setter to update the current step during workflow traversal.
     */
    public void setCurrentStepId(String currentStepId) {
        this.currentStepId = currentStepId;
    }

    @Override
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    @Override
    public Object getVariable(String name) {
        return variables.get(name);
    }

    @Override
    public <T> T getVariable(String name, Class<T> type) {
        Object value = variables.get(name);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    @Override
    public void setVariable(String name, Object value) {
        if (value == null) {
            variables.remove(name);
        } else {
            variables.put(name, value);
        }
    }

    @Override
    public void setVariables(Map<String, Object> newVariables) {
        if (newVariables != null) {
            newVariables.forEach(this::setVariable);
        }
    }

    @Override
    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }
}
