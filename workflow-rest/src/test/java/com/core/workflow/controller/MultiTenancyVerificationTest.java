package com.core.workflow.controller;

import com.core.common.context.TenantContext;
import com.core.common.security.JwtTokenParser;
import com.core.common.database.TenantDatabaseResolver;
import com.core.common.database.TenantRoutingMongoDatabaseFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.task.AsyncTaskExecutor;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "core.security.jwt.allow-unsigned=true"
})
public class MultiTenancyVerificationTest {

    @Autowired
    private JwtTokenParser jwtTokenParser;

    @Autowired
    private TenantDatabaseResolver databaseResolver;

    @Autowired
    @Qualifier("tenantAwareTaskExecutor")
    private AsyncTaskExecutor tenantAwareTaskExecutor;

    @MockitoBean
    private MongoClient mongoClient;

    @Test
    public void testDatabaseResolver() {
        String dbNameStandard = databaseResolver.resolveDatabaseName("TenantXYZ");
        assertEquals("shared_educational_erp", dbNameStandard);

        String dbNamePremium = databaseResolver.resolveDatabaseName("TenantXYZPremium");
        assertEquals("tenant_tenantxyzpremium", dbNamePremium);
    }

    @Test
    public void testRoutingMongoDatabaseFactory() {
        MongoDatabase mockDb = Mockito.mock(MongoDatabase.class);
        Mockito.when(mongoClient.getDatabase("tenant_tenantxyzpremium")).thenReturn(mockDb);

        TenantRoutingMongoDatabaseFactory factory = new TenantRoutingMongoDatabaseFactory(
                mongoClient, "shared_educational_erp", databaseResolver
        );

        // 1. Without context
        TenantContext.clear();
        factory.getMongoDatabase();
        Mockito.verify(mongoClient, Mockito.atLeastOnce()).getDatabase("shared_educational_erp");

        // 2. With tenant context
        TenantContext.setCurrentTenant("TenantXYZPremium");
        factory.getMongoDatabase();
        Mockito.verify(mongoClient, Mockito.atLeastOnce()).getDatabase("tenant_tenantxyzpremium");
        
        TenantContext.clear();
    }

    @Test
    public void testTenantContextPropagation() throws Exception {
        assertNotNull(tenantAwareTaskExecutor, "tenantAwareTaskExecutor bean should be registered");

        TenantContext.setCurrentTenant("TenantABC");
        assertEquals("TenantABC", TenantContext.getCurrentTenant());

        Future<String> future = tenantAwareTaskExecutor.submit(() -> {
            String threadTenant = TenantContext.getCurrentTenant();
            return threadTenant;
        });

        String propagatedTenant = future.get();
        assertEquals("TenantABC", propagatedTenant, "TenantContext should propagate to execution thread");

        TenantContext.clear();
    }

    @Test
    public void testJwtParsing() {
        Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String token = Jwts.builder()
                .setSubject("test-user")
                .claim("tenantId", "Tenant123")
                .claim("roles", List.of("ADMIN"))
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();

        var claims = jwtTokenParser.parseTokenWithoutSignature(token);
        assertEquals("Tenant123", jwtTokenParser.getTenant(claims));
        assertTrue(jwtTokenParser.getRoles(claims).contains("ADMIN"));
    }
}
