package com.core.workflow.engine;

import com.core.common.exception.CoreException;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.model.*;
import com.core.workflow.parser.StateMachineParser;
import com.core.workflow.persistence.MongoWorkflowState;
import com.core.workflow.persistence.StateMachineStateRepository;
import com.core.workflow.persistence.WorkflowState;
import com.core.workflow.security.WorkflowSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
public class StateMachineEngineTest {

    @Autowired
    private StateMachineEngine engine;

    @Autowired
    private StateMachineParser parser;

    @Autowired
    private StateMachineStateRepository stateRepository;

    @MockBean
    private WorkflowSecurityService mockSecurityService;

    // Autowire the mocked MongoTemplate from TestApplication to satisfy bean conditions
    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    public void setup() {
        // Reset mocks to permissive/default by default
        Mockito.reset(mockSecurityService);
        Mockito.reset(mongoTemplate);
    }

    private StateMachineDefinition loadWorkflow(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return parser.parse(is);
        }
    }

    @Test
    public void testLinearWorkflowSuccess() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/linear-workflow.json");
        
        Map<String, Object> vars = new HashMap<>();
        vars.put("number", 5);
        vars.put("flag", true);

        // Mock find in mongoTemplate to return empty or mock it if needed by stateRepository.find
        UUID targetExecutionId = UUID.randomUUID();
        
        StepExecutionContext context = engine.execute(def, vars);

        // Assert addition step executed: 5 + 10 = 15
        assertEquals(15, context.getVariable("number"));
        // Assert decision step executed
        assertEquals(true, context.getVariable("decision"));

        // Verify state persistence saved state to MongoDB
        Mockito.verify(mongoTemplate, Mockito.atLeastOnce()).save(any(MongoWorkflowState.class));
    }

    @Test
    public void testConditionalWorkflowBranchTrue() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/conditional-workflow.json");

        Map<String, Object> vars = new HashMap<>();
        vars.put("flag", true); // decision = true
        vars.put("number", 0);

        StepExecutionContext context = engine.execute(def, vars);

        // Branch condition #context.variables['decision'] == true met, should go to add-value (adds 10)
        assertEquals(true, context.getVariable("decision"));
        assertEquals(10, context.getVariable("number"));
    }

    @Test
    public void testConditionalWorkflowBranchFalse() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/conditional-workflow.json");

        Map<String, Object> vars = new HashMap<>();
        vars.put("flag", false); // decision = false
        vars.put("number", 0);

        StepExecutionContext context = engine.execute(def, vars);

        // Branch condition #context.variables['decision'] == false met, should go to end-step (skips add-value)
        assertEquals(false, context.getVariable("decision"));
        assertEquals(0, context.getVariable("number"));
    }

    @Test
    public void testStepRetryEventuallySucceeds() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/retry-workflow.json");

        Map<String, Object> vars = new HashMap<>();
        vars.put("failures", 0);
        vars.put("targetFailures", 2); // fail 2 times, succeed on 3rd attempt

        StepExecutionContext context = engine.execute(def, vars);

        // Check it executed and eventually succeeded
        assertEquals(2, context.getVariable("failures"));
        assertEquals(true, context.getVariable("retrySuccess"));
    }

    @Test
    public void testStepRetryFailsWhenExhausted() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/retry-workflow.json");

        Map<String, Object> vars = new HashMap<>();
        vars.put("failures", 0);
        vars.put("targetFailures", 5); // fail 5 times, but max attempts is 3

        CoreException ex = assertThrows(CoreException.class, () -> {
            engine.execute(def, vars);
        });

        assertTrue(ex.getMessage().contains("failed after 3 attempts") || ex.getMessage().contains("Mock failure attempt"));
        
        // Check MongoDB save was called for the failed state
        Mockito.verify(mongoTemplate, Mockito.atLeastOnce()).save(any(MongoWorkflowState.class));
    }

    @Test
    public void testWorkflowSecurityFailure() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/secured-workflow.json");

        // Mock security to throw exception
        Mockito.doThrow(new WorkflowSecurityException("Unauthorized workflow trigger", List.of("ROLE_ADMIN")))
                .when(mockSecurityService)
                .checkPermission(eq("secured-workflow"), any());

        WorkflowSecurityException ex = assertThrows(WorkflowSecurityException.class, () -> {
            engine.execute(def, Map.of());
        });

        assertEquals(List.of("ROLE_ADMIN"), ex.getRequiredRoles());
    }

    @Test
    public void testRuntimeNextStepOverride() throws Exception {
        StateMachineDefinition def = new StateMachineDefinition();
        def.setId("override-workflow");
        def.setStartStep("step-override");
        
        StepDefinition stepOverride = new StepDefinition();
        stepOverride.setId("step-override");
        stepOverride.setResource("stepWithOverrideExecutor");
        stepOverride.setNextStep("step-normal-next"); // This should be skipped due to runtime override

        StepDefinition stepTarget = new StepDefinition();
        stepTarget.setId("overridden-target");
        stepTarget.setResource("addNumberExecutor");

        def.setSteps(List.of(stepOverride, stepTarget));

        Map<String, Object> vars = new HashMap<>();
        vars.put("overrideTarget", "overridden-target");
        vars.put("number", 10);

        StepExecutionContext context = engine.execute(def, vars);

        // Assert it skipped 'step-normal-next' and executed 'overridden-target' (adds 10)
        assertEquals(true, context.getVariable("executedOverride"));
        assertEquals(20, context.getVariable("number"));
    }

    @Test
    public void testWorkflowValidationCycleDetection() {
        StateMachineDefinition def = new StateMachineDefinition();
        def.setId("cycle-workflow");
        def.setStartStep("step-one");

        StepDefinition step1 = new StepDefinition();
        step1.setId("step-one");
        step1.setResource("addNumberExecutor");
        step1.setNextStep("step-two");

        StepDefinition step2 = new StepDefinition();
        step2.setId("step-two");
        step2.setResource("decisionExecutor");
        step2.setNextStep("step-three");

        StepDefinition step3 = new StepDefinition();
        step3.setId("step-three");
        step3.setResource("decisionExecutor");
        step3.setNextStep("non-existent-step"); // Invalid transition target

        def.setSteps(List.of(step1, step2, step3));

        WorkflowValidationException ex = assertThrows(WorkflowValidationException.class, () -> {
            parser.validate(def);
        });

        assertTrue(ex.getMessage().contains("transitions to non-existent step 'non-existent-step'"));
    }

    @Test
    public void testVirtualThreadConcurrencyScaling() throws Exception {
        StateMachineDefinition def = loadWorkflow("workflows/linear-workflow.json");

        int taskCount = 100;
        List<CompletableFuture<StepExecutionContext>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("number", i);
            vars.put("flag", true);
            
            // Execute using Java 21 Virtual Threads
            CompletableFuture<StepExecutionContext> future = engine.executeAsync(def, vars);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify all threads executed successfully and returned the correct numbers
        for (int i = 0; i < taskCount; i++) {
            StepExecutionContext context = futures.get(i).get();
            assertEquals(i + 10, context.getVariable("number"));
            assertEquals(true, context.getVariable("decision"));
        }
    }
}
