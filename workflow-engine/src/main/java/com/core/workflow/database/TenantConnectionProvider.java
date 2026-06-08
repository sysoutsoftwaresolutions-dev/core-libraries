package com.core.workflow.database;

import org.springframework.data.mongodb.MongoDatabaseFactory;

/**
 * Interface that provides dynamic lookup or creation of MongoDatabaseFactory
 * instances for tenant database routing.
 */
public interface TenantConnectionProvider {

    /**
     * Resolves the {@link MongoDatabaseFactory} for the specified tenant.
     *
     * @param tenantId active tenant ID
     * @return the database factory associated with the tenant
     */
    MongoDatabaseFactory getTenantDatabaseFactory(String tenantId);
}
