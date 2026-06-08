package com.core.workflow.model;

/**
 * Configuration parameters for executing retries on a specific step.
 */
public class RetryConfig {

    private int maxAttempts = 1;
    private long backoffPeriodMs = 0;

    public RetryConfig() {
    }

    public RetryConfig(int maxAttempts, long backoffPeriodMs) {
        this.maxAttempts = maxAttempts;
        this.backoffPeriodMs = backoffPeriodMs;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBackoffPeriodMs() {
        return backoffPeriodMs;
    }

    public void setBackoffPeriodMs(long backoffPeriodMs) {
        this.backoffPeriodMs = backoffPeriodMs;
    }
}
