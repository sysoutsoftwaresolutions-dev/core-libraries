package com.core.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.core.utils.json.JsonUtils;
import jakarta.annotation.PostConstruct;

/**
 * Centralized Spring Configuration for Jackson 3 ObjectMapper.
 */
@Configuration
public class JacksonConfig {

    private final ObjectMapper objectMapper;

    public JacksonConfig() {
        this.objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    @PostConstruct
    public void injectStaticObjectMapper() {
        JsonUtils.setObjectMapper(objectMapper);
    }
}
