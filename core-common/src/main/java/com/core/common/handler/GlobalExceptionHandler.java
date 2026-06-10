package com.core.common.handler;

import com.core.common.exception.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global REST exception handler that intercepts CoreExceptions, wrapping exceptions,
 * and standard Web/Validation exceptions to construct standardized user-friendly JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Intercepts CoreExceptions directly.
     *
     * @param ex CoreException instance
     * @return standard error response Map
     */
    @ExceptionHandler(CoreException.class)
    public ResponseEntity<Map<String, Object>> handleCoreException(CoreException ex) {
        log.error("[Global-Exception-Handler] Intercepted CoreException: {} (Code: {})", 
                ex.getMessage(), ex.getErrorCode(), ex);
        
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String error = "Core Error";
        
        if (ex.getErrorCode() != null) {
            String code = ex.getErrorCode();
            if (code.equals("SECURITY_UNAUTHORIZED") || code.startsWith("SECURITY_")) {
                String msg = ex.getMessage();
                // Differentiate authentication failures (401) from authorization/role failures (403)
                if (msg != null && 
                    (msg.contains("signature") || 
                     msg.contains("validation failure") || 
                     msg.contains("expired") || 
                     msg.contains("Invalid token") || 
                     msg.contains("Token is missing") ||
                     msg.contains("forbidden") ||
                     msg.contains("issuer") ||
                     msg.contains("audience") ||
                     msg.contains("JWT signature"))) {
                    status = HttpStatus.UNAUTHORIZED;
                    error = "Unauthorized";
                } else {
                    status = HttpStatus.FORBIDDEN;
                    error = "Forbidden";
                }
            } else if (code.contains("NOT_FOUND")) {
                status = HttpStatus.NOT_FOUND;
                error = "Not Found";
            } else if (code.contains("VALIDATION") || code.contains("INVALID")) {
                status = HttpStatus.BAD_REQUEST;
                error = "Bad Request";
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("errorCode", ex.getErrorCode());
        body.put("message", ex.getMessage());
        
        return new ResponseEntity<>(body, status);
    }

    /**
     * Intercepts SecurityExceptions thrown by JWT parsing or validations.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.error("[Global-Exception-Handler] Intercepted SecurityException: {}", ex.getMessage(), ex);
        
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String error = "Unauthorized";
        
        // If it's a role or permission-based access denied, return 403 Forbidden
        if (ex.getMessage() != null && 
            (ex.getMessage().contains("Access denied") || 
             ex.getMessage().contains("authorized") || 
             ex.getMessage().contains("possess"))) {
            status = HttpStatus.FORBIDDEN;
            error = "Forbidden";
        }
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("errorCode", "SECURITY_UNAUTHORIZED");
        body.put("message", ex.getMessage());
        
        return new ResponseEntity<>(body, status);
    }

    /**
     * Intercepts JSON Web Token specific validation exceptions.
     */
    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwtException(io.jsonwebtoken.JwtException ex) {
        log.error("[Global-Exception-Handler] Intercepted JwtException: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("errorCode", "TOKEN_INVALID");
        body.put("message", "JWT validation failed: " + ex.getMessage());
        
        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Intercepts CGLIB AOP wrapping checked exceptions.
     */
    @ExceptionHandler(UndeclaredThrowableException.class)
    public ResponseEntity<Map<String, Object>> handleUndeclaredThrowable(UndeclaredThrowableException ex) {
        Throwable actual = getRootOrCoreException(ex);
        log.error("[Global-Exception-Handler] Unwrapped UndeclaredThrowableException to: {}", 
                actual != null ? actual.getClass().getName() : "null");
        if (actual instanceof CoreException) {
            return handleCoreException((CoreException) actual);
        } else if (actual instanceof SecurityException) {
            return handleSecurityException((SecurityException) actual);
        } else if (actual instanceof Exception) {
            return handleGenericException((Exception) actual);
        }
        return handleGenericException(ex);
    }

    /**
     * Intercepts Reflection invocation target failures.
     */
    @ExceptionHandler(InvocationTargetException.class)
    public ResponseEntity<Map<String, Object>> handleInvocationTarget(InvocationTargetException ex) {
        Throwable actual = getRootOrCoreException(ex);
        log.error("[Global-Exception-Handler] Unwrapped InvocationTargetException to: {}", 
                actual != null ? actual.getClass().getName() : "null");
        if (actual instanceof CoreException) {
            return handleCoreException((CoreException) actual);
        } else if (actual instanceof SecurityException) {
            return handleSecurityException((SecurityException) actual);
        } else if (actual instanceof Exception) {
            return handleGenericException((Exception) actual);
        }
        return handleGenericException(ex);
    }

    /**
     * Handles Validation failures for RequestBody bindings (@Valid).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("[Global-Exception-Handler] Validation failed for request argument: {}", ex.getMessage());
        
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> {
                    Map<String, String> fErr = new HashMap<>();
                    fErr.put("field", err.getField());
                    fErr.put("rejectedValue", String.valueOf(err.getRejectedValue()));
                    fErr.put("message", err.getDefaultMessage());
                    return fErr;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("errorCode", "VALIDATION_FAILED");
        body.put("message", "Validation failed for request body.");
        body.put("details", fieldErrors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles Validation failures for Path parameters / query inputs.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("[Global-Exception-Handler] Constraint violation: {}", ex.getMessage());
        
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("errorCode", "CONSTRAINT_VIOLATION");
        body.put("message", "Constraint violations detected.");
        body.put("details", details);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
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
        body.put("message", "An unexpected error occurred. Please contact the administrator.");
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Throwable getRootOrCoreException(Throwable ex) {
        if (ex == null) {
            return null;
        }
        Throwable current = ex;
        while (current != null) {
            if (current instanceof CoreException) {
                return current;
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        
        current = ex;
        while (current instanceof UndeclaredThrowableException 
                || current instanceof InvocationTargetException) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }
}
