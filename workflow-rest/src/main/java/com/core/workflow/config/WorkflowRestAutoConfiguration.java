package com.core.workflow.config;

import com.core.workflow.controller.WorkflowGraphQLController;
import com.core.workflow.controller.WorkflowRestController;
import com.core.workflow.kafka.WorkflowKafkaConsumer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration registering the REST, GraphQL, and Kafka Consumer
 * endpoints for the state machine engine.
 */
@AutoConfiguration
@Import({WorkflowRestController.class, WorkflowGraphQLController.class, WorkflowKafkaConsumer.class})
public class WorkflowRestAutoConfiguration {
}
