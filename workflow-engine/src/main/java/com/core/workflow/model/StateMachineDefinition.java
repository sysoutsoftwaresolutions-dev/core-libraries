package com.core.workflow.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of the entire state machine / workflow, parsed from JSON.
 */
public class StateMachineDefinition {

    private String id;
    private String version = "1.0.0";
    private WorkflowType type = WorkflowType.REGULAR;
    private List<String> roles = new ArrayList<>();
    private String startStep;
    private String module;
    private List<StepDefinition> steps = new ArrayList<>();

    public StateMachineDefinition() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public WorkflowType getType() {
        return type;
    }

    public void setType(WorkflowType type) {
        this.type = type;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getStartStep() {
        return startStep;
    }

    public void setStartStep(String startStep) {
        this.startStep = startStep;
    }

    public List<StepDefinition> getSteps() {
        return steps;
    }

    public void setSteps(List<StepDefinition> steps) {
        this.steps = steps;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }
    
    /**
     * Gets a step by its ID.
     *
     * @param stepId the ID of the step to find.
     * @return the StepDefinition, or null if not found.
     */
    public StepDefinition getStep(String stepId) {
        if (steps == null || stepId == null) {
            return null;
        }
        return steps.stream()
                .filter(s -> stepId.equals(s.getId()))
                .findFirst()
                .orElse(null);
    }
}
