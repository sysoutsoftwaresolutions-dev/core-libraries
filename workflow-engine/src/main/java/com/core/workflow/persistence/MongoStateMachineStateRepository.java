package com.core.workflow.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * MongoDB implementation of {@link StateMachineStateRepository} utilizing {@link MongoTemplate}.
 */
public class MongoStateMachineStateRepository implements StateMachineStateRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoStateMachineStateRepository.class);
    private final MongoTemplate mongoTemplate;

    public MongoStateMachineStateRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(WorkflowState state) {
        if (state == null || state.executionId() == null) {
            return;
        }

        try {
            MongoWorkflowState document = new MongoWorkflowState();
            document.setExecutionId(state.executionId());
            document.setMachineId(state.machineId());
            document.setCurrentStepId(state.currentStepId());
            document.setVariables(state.variables());
            document.setCompleted(state.completed());
            document.setFailed(state.failed());
            document.setErrorMessage(state.errorMessage());

            mongoTemplate.save(document);
            log.debug("[Workflow-Engine] Persisted state in MongoDB for execution: {}", state.executionId());
        } catch (Exception e) {
            log.error("[Workflow-Engine] Failed to persist workflow state in MongoDB for execution: {}", state.executionId(), e);
        }
    }

    @Override
    public Optional<WorkflowState> find(UUID executionId) {
        if (executionId == null) {
            return Optional.empty();
        }

        try {
            MongoWorkflowState document = mongoTemplate.findById(executionId, MongoWorkflowState.class);
            if (document == null) {
                return Optional.empty();
            }

            WorkflowState state = new WorkflowState(
                    document.getExecutionId(),
                    document.getMachineId(),
                    document.getCurrentStepId(),
                    document.getVariables(),
                    document.isCompleted(),
                    document.isFailed(),
                    document.getErrorMessage()
            );
            return Optional.of(state);
        } catch (Exception e) {
            log.error("[Workflow-Engine] Failed to find state in MongoDB for execution: {}", executionId, e);
            return Optional.empty();
        }
    }
}
