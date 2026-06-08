package com.core.workflow.mock;

import com.core.common.exception.CoreException;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.executor.StepExecutor;
import com.core.workflow.model.StepResult;
import org.springframework.stereotype.Component;

import java.util.Map;

public class MockStepExecutors {

    @Component("addNumberExecutor")
    public static class AddNumberExecutor implements StepExecutor {
        @Override
        public StepResult execute(StepExecutionContext context) throws CoreException {
            Integer current = context.getVariable("number", Integer.class);
            if (current == null) {
                current = 0;
            }
            int result = current + 10;
            return StepResult.success(Map.of("number", result));
        }
    }

    @Component("decisionExecutor")
    public static class DecisionExecutor implements StepExecutor {
        @Override
        public StepResult execute(StepExecutionContext context) throws CoreException {
            Boolean flag = context.getVariable("flag", Boolean.class);
            boolean decision = flag != null && flag;
            return StepResult.success(Map.of("decision", decision));
        }
    }

    @Component("failingStepExecutor")
    public static class FailingStepExecutor implements StepExecutor {
        @Override
        public StepResult execute(StepExecutionContext context) throws CoreException {
            Integer currentFailures = context.getVariable("failures", Integer.class);
            if (currentFailures == null) {
                currentFailures = 0;
            }
            
            Integer targetFailures = context.getVariable("targetFailures", Integer.class);
            if (targetFailures == null) {
                targetFailures = 1;
            }

            if (currentFailures < targetFailures) {
                int nextFailures = currentFailures + 1;
                context.setVariable("failures", nextFailures);
                // Throw our checked CoreException
                throw new CoreException("Mock failure attempt " + nextFailures, "MOCK_STEP_FAILURE");
            }

            return StepResult.success(Map.of("failures", currentFailures, "retrySuccess", true));
        }
    }

    @Component("stepWithOverrideExecutor")
    public static class StepWithOverrideExecutor implements StepExecutor {
        @Override
        public StepResult execute(StepExecutionContext context) throws CoreException {
            String override = context.getVariable("overrideTarget", String.class);
            return StepResult.successWithOverride(Map.of("executedOverride", true), override);
        }
    }
}
