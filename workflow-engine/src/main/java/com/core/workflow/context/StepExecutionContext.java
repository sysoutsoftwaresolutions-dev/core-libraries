package com.core.workflow.context;

import java.util.Map;
import java.util.UUID;

/**
 * Provides access to context variables and execution metadata for a running state machine instance.
 */
public interface StepExecutionContext {

    /**
     * Gets the unique UUID of this execution run.
     *
     * @return unique execution run ID.
     */
    UUID getExecutionId();

    /**
     * Gets the ID of the state machine/workflow definition being run.
     *
     * @return workflow ID.
     */
    String getMachineId();

    /**
     * Gets the ID of the current step being executed.
     *
     * @return current step ID.
     */
    String getCurrentStepId();

    /**
     * Gets all variables currently defined in the execution context.
     *
     * @return a map of context variables.
     */
    Map<String, Object> getVariables();

    /**
     * Gets a single variable by name.
     *
     * @param name the variable name.
     * @return the value of the variable, or null if not found.
     */
    Object getVariable(String name);

    /**
     * Gets a single variable by name, cast to the target type.
     *
     * @param name the variable name.
     * @param type the expected type class.
     * @param <T>  the type.
     * @return the cast value, or null if not found.
     */
    <T> T getVariable(String name, Class<T> type);

    /**
     * Sets a variable in the context.
     *
     * @param name  the name of the variable.
     * @param value the value to set.
     */
    void setVariable(String name, Object value);

    /**
     * Merges multiple variables into the context.
     *
     * @param variables the map of variables to merge.
     */
    void setVariables(Map<String, Object> variables);

    /**
     * Checks if a variable is present in the context.
     *
     * @param name the variable name.
     * @return true if the variable exists, false otherwise.
     */
    boolean hasVariable(String name);
}
