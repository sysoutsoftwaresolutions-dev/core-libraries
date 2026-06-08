package com.core.workflow.config;

import com.core.workflow.engine.ConditionEvaluator;
import com.core.workflow.engine.StateMachineEngine;
import com.core.workflow.listener.LoggingStateMachineListener;
import com.core.workflow.listener.StateMachineListener;
import com.core.workflow.parser.StateMachineParser;
import com.core.workflow.persistence.InMemoryStateRepository;
import com.core.workflow.persistence.MongoStateMachineStateRepository;
import com.core.workflow.persistence.StateMachineStateRepository;
import com.core.workflow.security.WorkflowSecurityService;
import com.core.workflow.database.TenantDatabaseResolver;
import com.core.workflow.database.TenantConnectionProvider;
import com.core.workflow.database.TenantRoutingMongoDatabaseFactory;
import com.core.workflow.aspect.TenantModuleValidationAspect;
import com.core.workflow.registry.TenantModuleRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/**
 * Spring Boot auto-configuration for the State Machine / Workflow Engine Library.
 */
@AutoConfiguration
@Import({
        ConditionEvaluator.class,
        WorkflowSecurityService.class,
        StateMachineParser.class,
        TenantModuleRegistry.class,
        TenantModuleValidationAspect.class
})
public class StateMachineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantDatabaseResolver.class)
    public TenantDatabaseResolver tenantDatabaseResolver() {
        // Default resolver: creates database named workflow_db_<tenantId>
        return tenantId -> "workflow_db_" + tenantId.toLowerCase();
    }

    @Bean
    @ConditionalOnMissingBean(org.springframework.data.mongodb.MongoDatabaseFactory.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(com.mongodb.client.MongoClient.class)
    public org.springframework.data.mongodb.MongoDatabaseFactory mongoDatabaseFactory(
            com.mongodb.client.MongoClient mongoClient,
            org.springframework.boot.autoconfigure.mongo.MongoProperties properties,
            TenantDatabaseResolver databaseResolver) {
        String dbName = properties.getMongoClientDatabase();
        if (dbName == null || dbName.isBlank()) {
            dbName = "workflow_db";
        }
        return new com.core.workflow.database.TenantRoutingMongoDatabaseFactory(mongoClient, dbName, databaseResolver);
    }

    @Bean
    @ConditionalOnMissingBean(com.core.workflow.database.TenantConnectionProvider.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.mongodb.MongoDatabaseFactory.class)
    public com.core.workflow.database.TenantConnectionProvider tenantConnectionProvider(
            org.springframework.data.mongodb.MongoDatabaseFactory databaseFactory) {
        return tenantId -> databaseFactory;
    }

    /**
     * Resolves the StateMachineStateRepository. If MongoTemplate is present and configured in the
     * application context, it registers MongoStateMachineStateRepository; otherwise, it falls back
     * to InMemoryStateRepository.
     *
     * @param mongoTemplateProvider lazy provider for MongoTemplate
     * @return StateMachineStateRepository instance
     */
    @Bean
    @ConditionalOnMissingBean(StateMachineStateRepository.class)
    public StateMachineStateRepository stateMachineStateRepository(ObjectProvider<MongoTemplate> mongoTemplateProvider) {
        MongoTemplate mongoTemplate = mongoTemplateProvider.getIfAvailable();
        if (mongoTemplate != null) {
            return new MongoStateMachineStateRepository(mongoTemplate);
        }
        return new InMemoryStateRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public StateMachineListener loggingStateMachineListener() {
        return new LoggingStateMachineListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public StateMachineEngine stateMachineEngine(ApplicationContext applicationContext,
                                                 ConditionEvaluator conditionEvaluator,
                                                 WorkflowSecurityService securityService,
                                                 StateMachineStateRepository stateRepository,
                                                 List<StateMachineListener> listeners) {
        return new StateMachineEngine(
                applicationContext,
                conditionEvaluator,
                securityService,
                stateRepository,
                listeners
        );
    }
}
