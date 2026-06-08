package com.core.workflow.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of a single step within a state machine workflow.
 */
public class StepDefinition {

    private String id;
    private String resource;
    private String nextStep;
    private String module;
    private List<TransitionDefinition> transitions = new ArrayList<>();
    private RetryConfig retry = new RetryConfig();

    public StepDefinition() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }

    public List<TransitionDefinition> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<TransitionDefinition> transitions) {
        this.transitions = transitions;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }
}
