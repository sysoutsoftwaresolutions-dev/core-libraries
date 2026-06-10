package com.core.common.config;

import com.core.common.aspect.ControllerErrorHandlerAspect;
import com.core.common.aspect.WorkflowSecurityAspect;
import com.core.common.client.WebClientHelper;
import com.core.common.context.TenantAwareTaskExecutor;
import com.core.common.context.TenantWebInterceptor;
import com.core.common.handler.GlobalExceptionHandler;
import com.core.common.handler.WebMvcGlobalExceptionHandler;
import com.core.common.security.JwtTokenParser;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Spring Boot auto-configuration for core-common utilities, automatically registering
 * the global exception handler, AOP aspects, and WebClient downstream helper.
 */
@AutoConfiguration(
        after = { org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class },
        before = { org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class }
)
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@Import({
        WorkflowSecurityAspect.class,
        ControllerErrorHandlerAspect.class,
        JwtTokenParser.class,
        com.core.common.event.OutboxPublisher.class,
        com.core.common.event.OutboxScheduler.class,
        JacksonConfig.class
})
public class CoreCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "jakarta.servlet.ServletException")
    public static class WebMvcExceptionHandlerConfig {
        @Bean
        @ConditionalOnMissingBean
        public WebMvcGlobalExceptionHandler webMvcGlobalExceptionHandler() {
            return new WebMvcGlobalExceptionHandler();
        }
    }

    @Bean(name = "tenantAwareTaskExecutor")
    @ConditionalOnMissingBean(name = "tenantAwareTaskExecutor")
    public AsyncTaskExecutor tenantAwareTaskExecutor() {
        SimpleAsyncTaskExecutor delegate = new SimpleAsyncTaskExecutor("tenant-async-");
        delegate.setVirtualThreads(true);
        return new TenantAwareTaskExecutor(delegate);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder coreWebClientBuilder() {
        // Production-grade connection pooling configuration
        ConnectionProvider provider = ConnectionProvider.builder("core-connection-pool")
                .maxConnections(500)
                .pendingAcquireTimeout(Duration.ofSeconds(45))
                .maxIdleTime(Duration.ofSeconds(15))
                .build();

        // Configure connection connect timeouts, read/write timeouts, and response timeouts
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 5s connection timeout
                .responseTimeout(Duration.ofSeconds(10)) // 10s response timeout
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))  // 10s read timeout
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS))); // 10s write timeout

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    @Bean
    @ConditionalOnMissingBean
    public WebClientHelper webClientHelper(WebClient.Builder builder) {
        return new WebClientHelper(builder.build());
    }

    /**
     * Web MVC configurations, conditionally active only when WebMvcConfigurer is available on the classpath.
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(WebMvcConfigurer.class)
    public static class WebMvcConfig implements WebMvcConfigurer {

        @Bean
        @ConditionalOnMissingBean
        public TenantWebInterceptor tenantWebInterceptor() {
            return new TenantWebInterceptor();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(tenantWebInterceptor());
        }
    }

    /**
     * MongoDB event processing guard configuration, conditionally active only when MongoTemplate is available.
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(org.springframework.data.mongodb.core.MongoTemplate.class)
    @ConditionalOnMissingBean
    public com.core.common.event.EventProcessingGuard eventProcessingGuard(org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        return new com.core.common.event.EventProcessingGuard(mongoTemplate);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.mongodb.MongoDatabaseFactory.class)
    @ConditionalOnMissingBean(org.springframework.transaction.PlatformTransactionManager.class)
    public org.springframework.data.mongodb.MongoTransactionManager transactionManager(org.springframework.data.mongodb.MongoDatabaseFactory dbFactory) {
        return new org.springframework.data.mongodb.MongoTransactionManager(dbFactory);
    }

    /**
     * Tenant database resolver configuration, resolving premium database names as tenant_<tenantId>
     * and routing standard tenants to the default shared database.
     */
    @Bean
    @ConditionalOnMissingBean(com.core.common.database.TenantDatabaseResolver.class)
    public com.core.common.database.TenantDatabaseResolver tenantDatabaseResolver() {
        return tenantId -> {
            if (tenantId == null || tenantId.isBlank() || "default".equalsIgnoreCase(tenantId) || "shared".equalsIgnoreCase(tenantId)) {
                return "shared_educational_erp";
            }
            // Switched dynamically on thread boundaries to dedicated databases named tenant_<tenantId>
            if (tenantId.toLowerCase().contains("premium")) {
                return "tenant_" + tenantId.toLowerCase();
            }
            return "shared_educational_erp";
        };
    }

    /**
     * Custom MongoDatabaseFactory to dynamically route database operations to standard or premium databases
     * depending on the tenant context.
     */
    @Bean
    @ConditionalOnMissingBean(org.springframework.data.mongodb.MongoDatabaseFactory.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(com.mongodb.client.MongoClient.class)
    public org.springframework.data.mongodb.MongoDatabaseFactory mongoDatabaseFactory(
            com.mongodb.client.MongoClient mongoClient,
            org.springframework.boot.autoconfigure.mongo.MongoProperties properties,
            com.core.common.database.TenantDatabaseResolver databaseResolver) {
        String dbName = properties.getMongoClientDatabase();
        if (dbName == null || dbName.isBlank()) {
            dbName = "shared_educational_erp";
        }
        return new com.core.common.database.TenantRoutingMongoDatabaseFactory(mongoClient, dbName, databaseResolver);
    }

    /**
     * Connection provider for backward compatibility and tenant factory resolution.
     */
    @Bean
    @ConditionalOnMissingBean(com.core.common.database.TenantConnectionProvider.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(org.springframework.data.mongodb.MongoDatabaseFactory.class)
    public com.core.common.database.TenantConnectionProvider tenantConnectionProvider(
            org.springframework.data.mongodb.MongoDatabaseFactory databaseFactory) {
        return tenantId -> databaseFactory;
    }
}
