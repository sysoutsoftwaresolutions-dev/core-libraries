package com.core.common.database;

/**
 * Interface defining resolution logic for tenant-specific database names.
 * Shared across all microservices for multi-tenant routing.
 */
public interface TenantDatabaseResolver {

    /**
     * Resolves the MongoDB database name for the given tenant identifier.
     *
     * @param tenantId active tenant ID
     * @return resolved database name
     */
    String resolveDatabaseName(String tenantId);
}
