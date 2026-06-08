package com.core.common.config;

import com.core.common.aspect.ControllerErrorHandlerAspect;
import com.core.common.aspect.WorkflowSecurityAspect;
import com.core.common.client.WebClientHelper;
import com.core.common.context.TenantAwareTaskExecutor;
import com.core.common.context.TenantWebInterceptor;
import com.core.common.handler.GlobalExceptionHandler;
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
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@Import({
        WorkflowSecurityAspect.class,
        ControllerErrorHandlerAspect.class,
        JwtTokenParser.class
})
public class CoreCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
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
}
