package com.core.utils.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Production-grade Jackson JSON utility wrapper.
 */
public final class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private JsonUtils() {
        // Prevent instantiation
    }

    /**
     * Retrieves the configured shared ObjectMapper instance.
     *
     * @return ObjectMapper
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * Serializes an object into its JSON string representation.
     *
     * @param object Object to serialize
     * @return JSON String
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
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
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
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
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
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
            return OBJECT_MAPPER.readValue(inputStream, clazz);
        } catch (IOException e) {
            log.error("Failed to read JSON from input stream to class {}", clazz.getSimpleName(), e);
            throw new RuntimeException("JSON deserialization read error", e);
        }
    }
}
