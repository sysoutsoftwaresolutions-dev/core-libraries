package com.core.workflow.registry;

import com.core.workflow.model.StateMachineDefinition;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry to hold loaded and validated StateMachineDefinitions.
 * Allows dynamic registration via REST/GraphQL/Kafka triggers.
 */
@Component
public class WorkflowRegistry {

    private final Map<String, StateMachineDefinition> registry = new ConcurrentHashMap<>();

    /**
     * Registers a state machine definition.
     *
     * @param definition the definition to add or update.
     */
    public void register(StateMachineDefinition definition) {
        if (definition != null && definition.getId() != null) {
            registry.put(definition.getId(), definition);
        }
    }

    /**
     * Retrieves a state machine definition by ID.
     *
     * @param workflowId the ID of the workflow.
     * @return the definition, or null if not registered.
     */
    public StateMachineDefinition get(String workflowId) {
        if (workflowId == null) {
            return null;
        }
        return registry.get(workflowId);
    }

    /**
     * Retrieves all registered state machine definitions.
     *
     * @return an unmodifiable map of definitions.
     */
    public Map<String, StateMachineDefinition> getAll() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * Clears all registered definitions.
     */
    public void clear() {
        registry.clear();
    }
}
