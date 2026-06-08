package com.core.common.exception;

/**
 * Base checked exception class for the core-libraries ecosystem.
 * Extends Exception directly so it can easily represent and catch any library or application error.
 */
public class CoreException extends Exception {

    private final String errorCode;

    public CoreException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CoreException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Gets the system or business error code.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
