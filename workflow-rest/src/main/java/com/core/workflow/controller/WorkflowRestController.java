package com.core.workflow.controller;

import com.core.common.exception.CoreException;
import com.core.common.security.SecuredWorkflow;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.engine.StateMachineEngine;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.parser.StateMachineParser;
import com.core.workflow.persistence.StateMachineStateRepository;
import com.core.workflow.persistence.WorkflowState;
import com.core.workflow.registry.WorkflowRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for registering and executing workflows.
 * Audited by AOP aspects under core-common.
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowRestController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRestController.class);

    private final StateMachineEngine engine;
    private final StateMachineParser parser;
    private final WorkflowRegistry registry;
    private final StateMachineStateRepository stateRepository;

    public WorkflowRestController(StateMachineEngine engine,
                                  StateMachineParser parser,
                                  WorkflowRegistry registry,
                                  StateMachineStateRepository stateRepository) {
        this.engine = engine;
        this.parser = parser;
        this.registry = registry;
        this.stateRepository = stateRepository;
    }

    /**
     * Registers a new workflow from a raw JSON configuration body.
     */
    @PostMapping("/register")
    @SecuredWorkflow("ROLE_ADMIN")
    public ResponseEntity<StateMachineDefinition> registerWorkflow(@RequestBody String jsonDefinition) throws CoreException {
        log.info("[REST-API] Registering new workflow definition...");
        StateMachineDefinition definition = parser.parse(jsonDefinition);
        registry.register(definition);
        return new ResponseEntity<>(definition, HttpStatus.CREATED);
    }

    /**
     * Runs a registered workflow by its ID.
     */
    @PostMapping("/run/{workflowId}")
    public ResponseEntity<Map<String, Object>> runWorkflow(
            @PathVariable("workflowId") String workflowId,
            @RequestBody Map<String, Object> variables) throws CoreException {
        
        log.info("[REST-API] Request to run workflow [{}]", workflowId);
        StateMachineDefinition definition = registry.get(workflowId);
        
        if (definition == null) {
            throw new CoreException("Workflow definition not registered: " + workflowId, "WORKFLOW_NOT_FOUND");
        }

        // Run the state machine
        StepExecutionContext context = engine.execute(definition, variables);

        Map<String, Object> response = new HashMap<>();
        response.put("executionId", context.getExecutionId());
        response.put("machineId", context.getMachineId());
        response.put("currentStepId", context.getCurrentStepId());
        response.put("variables", context.getVariables());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the state of an execution by UUID.
     */
    @GetMapping("/execution/{executionId}")
    public ResponseEntity<WorkflowState> getExecutionState(@PathVariable("executionId") UUID executionId) throws CoreException {
        log.info("[REST-API] Fetching state for execution: {}", executionId);
        WorkflowState state = stateRepository.find(executionId)
                .orElseThrow(() -> new CoreException("Execution not found: " + executionId, "EXECUTION_NOT_FOUND"));
        return ResponseEntity.ok(state);
    }

    /**
     * Patches/updates variables of a running execution.
     */
    @PatchMapping("/execution/{executionId}")
    @SecuredWorkflow({"ROLE_OPERATOR", "ROLE_ADMIN"})
    public ResponseEntity<WorkflowState> patchExecutionVariables(
            @PathVariable("executionId") UUID executionId,
            @RequestBody Map<String, Object> variablesToPatch) throws CoreException {
        
        log.info("[REST-API] Request to patch variables for execution: {}", executionId);
        
        WorkflowState existingState = stateRepository.find(executionId)
                .orElseThrow(() -> new CoreException("Execution not found: " + executionId, "EXECUTION_NOT_FOUND"));

        // Merge variables
        Map<String, Object> currentVariables = new HashMap<>(existingState.variables());
        if (variablesToPatch != null) {
            currentVariables.putAll(variablesToPatch);
        }

        WorkflowState updatedState = new WorkflowState(
                existingState.executionId(),
                existingState.machineId(),
                existingState.currentStepId(),
                currentVariables,
                existingState.completed(),
                existingState.failed(),
                existingState.errorMessage()
        );

        stateRepository.save(updatedState);
        log.info("[REST-API] Patched variables successfully for execution: {}", executionId);
        
        return ResponseEntity.ok(updatedState);
    }
}
