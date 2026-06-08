package com.core.workflow.parser;

import com.core.utils.json.JsonUtils;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.model.StepDefinition;
import com.core.workflow.model.TransitionDefinition;
import com.core.workflow.model.WorkflowValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parser and validator for JSON state machine / workflow definitions.
 */
@Component
public class StateMachineParser {

    private static final Logger log = LoggerFactory.getLogger(StateMachineParser.class);

    /**
     * Parses a workflow definition from a JSON String and validates its structure.
     *
     * @param json the JSON content.
     * @return a validated StateMachineDefinition.
     * @throws WorkflowValidationException if parsing or validation fails.
     */
    public StateMachineDefinition parse(String json) throws WorkflowValidationException {
        try {
            StateMachineDefinition definition = JsonUtils.fromJson(json, StateMachineDefinition.class);
            validate(definition);
            return definition;
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse workflow JSON", e);
            throw new WorkflowValidationException("Failed to parse workflow JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a workflow definition from an InputStream and validates its structure.
     *
     * @param inputStream the JSON content input stream.
     * @return a validated StateMachineDefinition.
     * @throws WorkflowValidationException if parsing or validation fails.
     */
    public StateMachineDefinition parse(InputStream inputStream) throws WorkflowValidationException {
        try {
            StateMachineDefinition definition = JsonUtils.fromJson(inputStream, StateMachineDefinition.class);
            validate(definition);
            return definition;
        } catch (WorkflowValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse workflow JSON stream", e);
            throw new WorkflowValidationException("Failed to parse workflow JSON stream: " + e.getMessage(), e);
        }
    }

    /**
     * Statically validates a state machine definition.
     *
     * @param def the state machine definition to validate.
     * @throws WorkflowValidationException if structural errors are found.
     */
    public void validate(StateMachineDefinition def) throws WorkflowValidationException {
        if (def == null) {
            throw new WorkflowValidationException("Workflow definition cannot be null.");
        }
        if (def.getId() == null || def.getId().strip().isEmpty()) {
            throw new WorkflowValidationException("Workflow 'id' is required.");
        }
        if (def.getStartStep() == null || def.getStartStep().strip().isEmpty()) {
            throw new WorkflowValidationException("Workflow 'startStep' is required.");
        }
        if (def.getSteps() == null || def.getSteps().isEmpty()) {
            throw new WorkflowValidationException("Workflow must contain at least one step.");
        }

        Set<String> stepIds = new HashSet<>();
        for (StepDefinition step : def.getSteps()) {
            if (step.getId() == null || step.getId().strip().isEmpty()) {
                throw new WorkflowValidationException("Step 'id' is required for all steps.");
            }
            if (step.getResource() == null || step.getResource().strip().isEmpty()) {
                throw new WorkflowValidationException("Step 'resource' (executor bean name) is required for step: " + step.getId());
            }
            if (!stepIds.add(step.getId())) {
                throw new WorkflowValidationException("Duplicate step ID found: " + step.getId());
            }
        }

        // Check if startStep exists
        if (!stepIds.contains(def.getStartStep())) {
            throw new WorkflowValidationException("Start step '" + def.getStartStep() + "' is not defined in the steps list.");
        }

        // Validate transitions and verify referenced steps exist
        for (StepDefinition step : def.getSteps()) {
            if (step.getNextStep() != null && !step.getNextStep().strip().isEmpty()) {
                if (!stepIds.contains(step.getNextStep())) {
                    throw new WorkflowValidationException(String.format("Step '%s' transitions to non-existent step '%s'", 
                            step.getId(), step.getNextStep()));
                }
            }
            if (step.getTransitions() != null) {
                for (TransitionDefinition transition : step.getTransitions()) {
                    if (transition.getNextStep() == null || transition.getNextStep().strip().isEmpty()) {
                        throw new WorkflowValidationException("Transition nextStep target cannot be empty in step: " + step.getId());
                    }
                    if (!stepIds.contains(transition.getNextStep())) {
                        throw new WorkflowValidationException(String.format("Step '%s' has conditional transition to non-existent step '%s'", 
                                step.getId(), transition.getNextStep()));
                    }
                }
            }
        }

        // Perform reachability analysis from startStep
        Set<String> reachable = new HashSet<>();
        checkReachability(def.getStartStep(), def, reachable);
        
        // Find unreachable steps
        for (String stepId : stepIds) {
            if (!reachable.contains(stepId)) {
                log.warn("Step '{}' is defined in workflow [{}] but is unreachable from start step '{}'.", 
                        stepId, def.getId(), def.getStartStep());
            }
        }
    }

    private void checkReachability(String currentStepId, StateMachineDefinition def, Set<String> visited) {
        if (visited.contains(currentStepId)) {
            return;
        }
        visited.add(currentStepId);

        StepDefinition step = def.getStep(currentStepId);
        if (step == null) {
            return;
        }

        // Traverse default nextStep
        if (step.getNextStep() != null && !step.getNextStep().strip().isEmpty()) {
            checkReachability(step.getNextStep(), def, visited);
        }

        // Traverse conditional transitions
        if (step.getTransitions() != null) {
            for (TransitionDefinition transition : step.getTransitions()) {
                checkReachability(transition.getNextStep(), def, visited);
            }
        }
    }
}
