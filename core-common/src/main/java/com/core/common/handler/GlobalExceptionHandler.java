package com.core.common.handler;

import com.core.common.exception.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global REST exception handler that intercepts CoreExceptions and other unhandled exceptions
 * to construct a standardized JSON response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Intercepts CoreExceptions thrown anywhere in the application.
     *
     * @param ex CoreException instance
     * @return standard error response Map
     */
    @ExceptionHandler(CoreException.class)
    public ResponseEntity<Map<String, Object>> handleCoreException(CoreException ex) {
        log.error("[Global-Exception-Handler] Intercepted CoreException: {} (Code: {})", 
                ex.getMessage(), ex.getErrorCode(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Core Exception");
        body.put("errorCode", ex.getErrorCode());
        body.put("message", ex.getMessage());
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Fallback interceptor for any general unhandled exceptions.
     *
     * @param ex Exception instance
     * @return standard internal error response Map
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("[Global-Exception-Handler] Intercepted general Exception: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
