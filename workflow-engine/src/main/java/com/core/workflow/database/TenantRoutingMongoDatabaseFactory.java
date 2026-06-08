package com.core.workflow.database;

import com.core.common.context.TenantContext;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * Dynamic routing implementation of {@link org.springframework.data.mongodb.MongoDatabaseFactory}
 * that switches databases based on the current ThreadLocal {@link TenantContext}.
 */
public class TenantRoutingMongoDatabaseFactory extends SimpleMongoClientDatabaseFactory {

    private static final Logger log = LoggerFactory.getLogger(TenantRoutingMongoDatabaseFactory.class);
    private final TenantDatabaseResolver databaseResolver;
    private final String defaultDatabaseName;

    public TenantRoutingMongoDatabaseFactory(MongoClient mongoClient, String defaultDatabaseName, TenantDatabaseResolver databaseResolver) {
        super(mongoClient, defaultDatabaseName);
        this.defaultDatabaseName = defaultDatabaseName;
        this.databaseResolver = databaseResolver;
    }

    @Override
    public MongoDatabase getMongoDatabase() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank() || "default".equalsIgnoreCase(tenantId)) {
            log.trace("[TenantRoutingMongoDatabaseFactory] Routing to default database: {}", defaultDatabaseName);
            return super.getMongoDatabase();
        }
        String resolvedDbName = databaseResolver.resolveDatabaseName(tenantId);
        log.trace("[TenantRoutingMongoDatabaseFactory] Routing tenant [{}] to database: {}", tenantId, resolvedDbName);
        return super.getMongoDatabase(resolvedDbName);
    }
}
