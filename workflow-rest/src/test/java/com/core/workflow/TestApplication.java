package com.core.workflow;

import com.core.workflow.executor.StepExecutor;
import com.core.workflow.model.StepResult;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot bootstrap class for testing the workflow-rest module.
 * Excludes MongoDB and Kafka auto-configurations, replacing them with mocks.
 */
@SpringBootApplication(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        KafkaAutoConfiguration.class
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        return Mockito.mock(MongoTemplate.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return Mockito.mock(KafkaTemplate.class);
    }

    @Bean("addNumberExecutor")
    public StepExecutor addNumberExecutor() {
        return context -> {
            Object raw = context.getVariable("number");
            Integer current = null;
            if (raw instanceof Number) {
                current = ((Number) raw).intValue();
            } else if (raw instanceof String) {
                try {
                    current = Integer.parseInt((String) raw);
                } catch (NumberFormatException ignored) {}
            }
            if (current == null) {
                current = 0;
            }
            int result = current + 10;
            return StepResult.success(java.util.Map.of("number", result));
        };
    }
}
