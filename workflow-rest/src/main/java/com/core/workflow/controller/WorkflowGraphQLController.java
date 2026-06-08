package com.core.workflow.controller;

import com.core.common.exception.CoreException;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.engine.StateMachineEngine;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.persistence.StateMachineStateRepository;
import com.core.workflow.persistence.WorkflowState;
import com.core.workflow.registry.WorkflowRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphQL Controller resolving queries and mutations defined in schema.graphqls.
 */
@Controller
public class WorkflowGraphQLController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGraphQLController.class);

    private final StateMachineEngine engine;
    private final WorkflowRegistry registry;
    private final StateMachineStateRepository stateRepository;

    public WorkflowGraphQLController(StateMachineEngine engine,
                                     WorkflowRegistry registry,
                                     StateMachineStateRepository stateRepository) {
        this.engine = engine;
        this.registry = registry;
        this.stateRepository = stateRepository;
    }

    /**
     * GraphQL Query to retrieve state machine run details.
     */
    @QueryMapping
    public WorkflowState getExecutionState(@Argument("executionId") UUID executionId) throws CoreException {
        log.info("[GraphQL] Resolving query getExecutionState for execution: {}", executionId);
        return stateRepository.find(executionId)
                .orElseThrow(() -> new CoreException("Workflow execution not found for ID: " + executionId, "EXECUTION_NOT_FOUND"));
    }

    /**
     * GraphQL Mutation to start a state machine execution.
     */
    @MutationMapping
    public ExecutionResult executeWorkflow(
            @Argument("workflowId") String workflowId,
            @Argument("variables") List<VariableInput> variables) throws CoreException {
        
        log.info("[GraphQL] Resolving mutation executeWorkflow for workflow: {}", workflowId);
        StateMachineDefinition definition = registry.get(workflowId);
        
        if (definition == null) {
            throw new CoreException("Workflow definition not registered: " + workflowId, "WORKFLOW_NOT_FOUND");
        }

        // Convert list of VariableInput parameters into a Map
        Map<String, Object> varsMap = new HashMap<>();
        if (variables != null) {
            for (VariableInput var : variables) {
                varsMap.put(var.key(), var.value());
            }
        }

        // Trigger workflow execution
        StepExecutionContext context = engine.execute(definition, varsMap);

        // Convert variables back to GraphQL Variable records
        List<Variable> responseVars = context.getVariables().entrySet().stream()
                .map(e -> new Variable(e.getKey(), e.getValue() != null ? e.getValue().toString() : null))
                .collect(Collectors.toList());

        return new ExecutionResult(
                context.getExecutionId(),
                context.getMachineId(),
                context.getCurrentStepId(),
                responseVars
        );
    }

    /**
     * Schema mapping to bind the Java Map variables from WorkflowState to GraphQL Variable list.
     */
    @SchemaMapping(typeName = "WorkflowState", field = "variables")
    public List<Variable> getWorkflowStateVariables(WorkflowState state) {
        if (state == null || state.variables() == null) {
            return Collections.emptyList();
        }
        return state.variables().entrySet().stream()
                .map(e -> new Variable(e.getKey(), e.getValue() != null ? e.getValue().toString() : null))
                .collect(Collectors.toList());
    }

    // Helper records for GraphQL binding compatibility
    public record VariableInput(String key, String value) {}
    public record Variable(String key, String value) {}
    public record ExecutionResult(
            UUID executionId,
            String machineId,
            String currentStepId,
            List<Variable> variables
    ) {}
}
