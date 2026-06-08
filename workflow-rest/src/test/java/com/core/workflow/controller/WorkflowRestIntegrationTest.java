package com.core.workflow.controller;

import com.core.common.exception.CoreException;
import com.core.workflow.kafka.WorkflowKafkaConsumer;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.model.WorkflowSecurityException;
import com.core.workflow.parser.StateMachineParser;
import com.core.workflow.persistence.StateMachineStateRepository;
import com.core.workflow.persistence.WorkflowState;
import com.core.workflow.registry.WorkflowRegistry;
import com.core.workflow.security.WorkflowSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureGraphQlTester
public class WorkflowRestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private WorkflowRegistry registry;

    @Autowired
    private StateMachineParser parser;

    @Autowired
    private WorkflowKafkaConsumer kafkaConsumer;

    @MockBean
    private StateMachineStateRepository stateRepository;

    @MockBean
    private WorkflowSecurityService securityService;

    @BeforeEach
    public void setup() {
        Mockito.reset(stateRepository);
        Mockito.reset(securityService);
        registry.clear();
    }

    private void registerMockWorkflow() throws Exception {
        String json = """
                {
                  "id": "test-rest-workflow",
                  "startStep": "step-one",
                  "steps": [
                    {
                      "id": "step-one",
                      "resource": "addNumberExecutor"
                    }
                  ]
                }
                """;
        StateMachineDefinition def = parser.parse(json);
        registry.register(def);
    }

    @Test
    public void testRegisterAndRunRestController() throws Exception {
        // 1. Test POST /register
        String json = """
                {
                  "id": "test-rest-workflow",
                  "startStep": "step-one",
                  "steps": [
                    {
                      "id": "step-one",
                      "resource": "addNumberExecutor"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/workflows/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("test-rest-workflow"));

        assertNotNull(registry.get("test-rest-workflow"));

        // 2. Test POST /run/{workflowId}
        mockMvc.perform(post("/api/workflows/run/test-rest-workflow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineId").value("test-rest-workflow"))
                .andExpect(jsonPath("$.variables.number").value(20)); // addNumberExecutor adds 10
    }

    @Test
    public void testGetAndPatchExecutionRestController() throws Exception {
        UUID execId = UUID.randomUUID();
        Map<String, Object> vars = new HashMap<>();
        vars.put("number", 42);

        WorkflowState existingState = new WorkflowState(
                execId,
                "test-rest-workflow",
                "step-one",
                vars,
                false,
                false,
                null
        );

        Mockito.when(stateRepository.find(eq(execId))).thenReturn(Optional.of(existingState));

        // 1. Test GET /execution/{id}
        mockMvc.perform(get("/api/workflows/execution/" + execId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(execId.toString()))
                .andExpect(jsonPath("$.variables.number").value(42));

        // 2. Test PATCH /execution/{id}
        mockMvc.perform(patch("/api/workflows/execution/" + execId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newVar\": \"patched-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variables.number").value(42))
                .andExpect(jsonPath("$.variables.newVar").value("patched-value"));

        Mockito.verify(stateRepository, Mockito.atLeastOnce()).save(any(WorkflowState.class));
    }

    @Test
    public void testGraphQLQueryAndMutation() throws Exception {
        registerMockWorkflow();

        // 1. Test executeWorkflow Mutation
        String mutation = """
                mutation {
                  executeWorkflow(workflowId: "test-rest-workflow", variables: [{ key: "number", value: "30" }]) {
                    executionId
                    machineId
                    variables {
                      key
                      value
                    }
                  }
                }
                """;

        graphQlTester.document(mutation)
                .execute()
                .path("executeWorkflow.machineId").entity(String.class).isEqualTo("test-rest-workflow")
                .path("executeWorkflow.variables").entityList(WorkflowGraphQLController.Variable.class)
                .satisfies(list -> {
                    boolean containsNumber = list.stream().anyMatch(v -> "number".equals(v.key()) && "40".equals(v.value()));
                    assertTrue(containsNumber);
                });

        // 2. Test getExecutionState Query
        UUID execId = UUID.randomUUID();
        WorkflowState mockState = new WorkflowState(
                execId,
                "test-rest-workflow",
                "step-one",
                Map.of("hello", "world"),
                true,
                false,
                null
        );
        Mockito.when(stateRepository.find(eq(execId))).thenReturn(Optional.of(mockState));

        String query = String.format("""
                query {
                  getExecutionState(executionId: "%s") {
                    executionId
                    machineId
                    completed
                    variables {
                      key
                      value
                    }
                  }
                }
                """, execId);

        graphQlTester.document(query)
                .execute()
                .path("getExecutionState.executionId").entity(String.class).isEqualTo(execId.toString())
                .path("getExecutionState.completed").entity(Boolean.class).isEqualTo(true)
                .path("getExecutionState.variables").entityList(WorkflowGraphQLController.Variable.class)
                .satisfies(list -> {
                    assertEquals(1, list.size());
                    assertEquals("hello", list.get(0).key());
                    assertEquals("world", list.get(0).value());
                });
    }

    @Test
    public void testKafkaTrigger() throws Exception {
        registerMockWorkflow();

        String kafkaPayload = """
                {
                  "workflowId": "test-rest-workflow",
                  "variables": {
                    "number": 100
                  }
                }
                """;

        // Manually trigger consumer
        kafkaConsumer.consumeTrigger(kafkaPayload);

        // Allow async thread pool execution to complete (Virtual Threads are quick)
        Thread.sleep(200);

        // Verification: check if MongoDB save was called for this execution
        Mockito.verify(stateRepository, Mockito.atLeastOnce()).save(any(WorkflowState.class));
    }

    @Test
    public void testAopSecurityAspectRejection() throws Exception {
        // Mock security service to throw exception for register (SecuredWorkflow ROLE_ADMIN)
        Mockito.doThrow(new WorkflowSecurityException("Security check failed", List.of("ROLE_ADMIN")))
                .when(securityService)
                .checkPermission(any(), any());

        String json = """
                {
                  "id": "test-rest-workflow"
                }
                """;

        // AOP Aspect WorkflowSecurityAspect should catch SecuredWorkflow on Controller, 
        // delegate and throw CoreException which is mapped to 500 by GlobalExceptionHandler
        mockMvc.perform(post("/api/workflows/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Core Exception"))
                .andExpect(jsonPath("$.errorCode").value("SECURITY_UNAUTHORIZED"));
    }
}
