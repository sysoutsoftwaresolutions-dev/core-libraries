package com.core.common.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that extracts the tenant identifier from HTTP headers and maps it
 * to TenantContext for request processing, clearing it on completion.
 */
public class TenantWebInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantWebInterceptor.class);
    public static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.strip().isEmpty()) {
            tenantId = "default";
        }
        
        String cleanTenant = tenantId.trim();
        TenantContext.setCurrentTenant(cleanTenant);
        log.debug("[TenantInterceptor] Associated thread with tenant: {}", cleanTenant);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContext.clear();
        log.debug("[TenantInterceptor] Cleared thread tenant binding");
    }
}
