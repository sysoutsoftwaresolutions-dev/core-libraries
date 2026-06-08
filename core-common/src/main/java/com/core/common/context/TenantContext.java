package com.core.common.context;

/**
 * ThreadLocal container storing the current tenant context for database, security, and log routing.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Prevent instantiation
    }

    /**
     * Retrieves the tenant ID associated with the current thread context.
     *
     * @return active tenant ID or null if not set.
     */
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    /**
     * Sets the tenant ID for the current thread context.
     *
     * @param tenantId the tenant ID to set.
     */
    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Clears the tenant ID thread-local binding to prevent memory leaks in thread pools.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
