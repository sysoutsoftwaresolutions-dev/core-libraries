package com.core.workflow.model;

/**
 * Definition of a transition from one step to another based on a condition.
 */
public class TransitionDefinition {

    private String condition;
    private String nextStep;

    public TransitionDefinition() {
    }

    public TransitionDefinition(String condition, String nextStep) {
        this.condition = condition;
        this.nextStep = nextStep;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }
}
