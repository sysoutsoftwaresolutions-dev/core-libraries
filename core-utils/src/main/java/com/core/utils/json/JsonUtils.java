package com.core.utils.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Production-grade Jackson JSON utility wrapper.
 */
public final class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    private static ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private JsonUtils() {
        // Prevent instantiation
    }

    /**
     * Sets the shared ObjectMapper instance (used by Spring Config).
     *
     * @param mapper ObjectMapper
     */
    public static synchronized void setObjectMapper(ObjectMapper mapper) {
        objectMapper = mapper;
    }

    /**
     * Retrieves the configured shared ObjectMapper instance.
     *
     * @return ObjectMapper
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Serializes an object into its JSON string representation.
     *
     * @param object Object to serialize
     * @return JSON String
     */
    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to serialize object to JSON", e);
            throw new RuntimeException("JSON serialization error", e);
        }
    }

    /**
     * Deserializes a JSON string into an object of the specified class.
     *
     * @param json  JSON string
     * @param clazz Target class
     * @param <T>   Target type
     * @return Deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON to class {}", clazz.getSimpleName(), e);
            throw new RuntimeException("JSON deserialization error", e);
        }
    }

    /**
     * Deserializes a JSON string into an object of the specified type reference (e.g. Map, List).
     *
     * @param json          JSON string
     * @param typeReference Target type reference
     * @param <T>           Target type
     * @return Deserialized object
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON to type reference", e);
            throw new RuntimeException("JSON deserialization error", e);
        }
    }

    /**
     * Deserializes JSON content from an InputStream into an object of the specified class.
     *
     * @param inputStream Source stream
     * @param clazz       Target class
     * @param <T>         Target type
     * @return Deserialized object
     */
    public static <T> T fromJson(InputStream inputStream, Class<T> clazz) {
        try {
            return objectMapper.readValue(inputStream, clazz);
        } catch (Exception e) {
            log.error("Failed to read JSON from input stream to class {}", clazz.getSimpleName(), e);
            throw new RuntimeException("JSON deserialization read error", e);
        }
    }
}
