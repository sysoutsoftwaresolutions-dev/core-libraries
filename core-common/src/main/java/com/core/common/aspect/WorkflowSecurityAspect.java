package com.core.common.aspect;

import com.core.common.context.TenantContext;
import com.core.common.exception.CoreException;
import com.core.common.security.JwtTokenParser;
import com.core.common.security.SecuredWorkflow;
import io.jsonwebtoken.Claims;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * Aspect to dynamically enforce role security on controller methods
 * annotated with {@link SecuredWorkflow}.
 * First verifies local JWT signature, tenant context compatibility, and roles.
 * Fallbacks to dynamic delegation to WorkflowSecurityService or Spring Security Context.
 */
@Aspect
@Component
public class WorkflowSecurityAspect {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSecurityAspect.class);
    private final ApplicationContext applicationContext;
    private final JwtTokenParser jwtTokenParser;

    public WorkflowSecurityAspect(ApplicationContext applicationContext, JwtTokenParser jwtTokenParser) {
        this.applicationContext = applicationContext;
        this.jwtTokenParser = jwtTokenParser;
    }

    /**
     * Intercepts methods annotated with @SecuredWorkflow and checks roles.
     *
     * @param joinPoint       intercepted join point
     * @param securedWorkflow annotation containing roles
     * @throws CoreException if authorization fails
     */
    @Before("@annotation(securedWorkflow)")
    public void authorize(JoinPoint joinPoint, SecuredWorkflow securedWorkflow) throws CoreException {
        String[] requiredRoles = securedWorkflow.value();
        if (requiredRoles == null || requiredRoles.length == 0) {
            return;
        }

        List<String> roles = Arrays.asList(requiredRoles);

        // 1. Try local JWT token verification if an Authorization header is present
        String authHeader = null;
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                authHeader = attributes.getRequest().getHeader("Authorization");
            }
        } catch (Exception e) {
            log.debug("[SecurityAspect] Could not retrieve HttpServletRequest from RequestContextHolder: {}", e.getMessage());
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = jwtTokenParser.parseToken(token);
                
                // Verify tenant alignment
                String tokenTenant = jwtTokenParser.getTenant(claims);
                String currentTenant = TenantContext.getCurrentTenant();
                if (currentTenant != null && tokenTenant != null && !currentTenant.equalsIgnoreCase(tokenTenant)) {
                    List<String> tokenRoles = jwtTokenParser.getRoles(claims);
                    boolean isPlatformSuperAdmin = ("platform".equalsIgnoreCase(tokenTenant) || "system".equalsIgnoreCase(tokenTenant))
                            && tokenRoles.stream().anyMatch(r -> r.equalsIgnoreCase("SUPER_ADMIN") || r.equalsIgnoreCase("ROLE_SUPER_ADMIN"));
                    
                    if (!isPlatformSuperAdmin) {
                        log.error("[SecurityAspect] Tenant mismatch. TenantContext: {}, Token: {}", currentTenant, tokenTenant);
                        throw new CoreException("Tenant access mismatch: request header and token do not align.", "SECURITY_UNAUTHORIZED");
                    }
                }

                // If TenantContext was not set but token has a tenant, set it.
                if (currentTenant == null && tokenTenant != null) {
                    TenantContext.setCurrentTenant(tokenTenant);
                }

                // Verify roles
                List<String> tokenRoles = jwtTokenParser.getRoles(claims);
                boolean roleMatch = false;
                for (String reqRole : requiredRoles) {
                    for (String tokenRole : tokenRoles) {
                        if (tokenRole.equalsIgnoreCase(reqRole) || 
                            tokenRole.equalsIgnoreCase("ROLE_" + reqRole) || 
                            ("ROLE_" + tokenRole).equalsIgnoreCase(reqRole)) {
                            roleMatch = true;
                            break;
                        }
                    }
                    if (roleMatch) {
                        break;
                    }
                }

                if (!roleMatch) {
                    log.error("[SecurityAspect] Access denied. Token roles {} do not match required roles {}", tokenRoles, roles);
                    throw new CoreException("Access denied. Token does not possess required roles.", "SECURITY_UNAUTHORIZED");
                }

                log.debug("[SecurityAspect] Local JWT verification passed");
                return; // Access approved locally
            } catch (CoreException ce) {
                throw ce;
            } catch (Exception e) {
                log.error("[SecurityAspect] JWT signature verification failed locally: {}", e.getMessage());
                throw new CoreException("Unauthorized: JWT signature or validation failure: " + e.getMessage(), "SECURITY_UNAUTHORIZED", e);
            }
        }

        // 2. Try delegating dynamically to the WorkflowSecurityService bean if present
        try {
            Class<?> serviceClass = Class.forName("com.core.workflow.security.WorkflowSecurityService");
            Object securityService = applicationContext.getBean(serviceClass);
            securityService.getClass()
                    .getMethod("checkPermission", String.class, List.class)
                    .invoke(securityService, joinPoint.getSignature().getName(), roles);
            
            log.debug("[SecurityAspect] Security validation passed via WorkflowSecurityService");
            return; // Successfully checked and approved
        } catch (ClassNotFoundException | org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            log.debug("[SecurityAspect] WorkflowSecurityService bean or class not found. Falling back to context lookup.");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof CoreException) {
                throw (CoreException) cause;
            }
            throw new CoreException("Security evaluation failed: " + cause.getMessage(), "SECURITY_ERROR", cause);
        } catch (Exception e) {
            log.error("[SecurityAspect] Reflection error invoking WorkflowSecurityService", e);
        }

        // 3. Fallback to direct Spring Security context inspection
        boolean authorized = checkSpringSecurityAuthorities(roles);

        if (!authorized) {
            log.error("[SecurityAspect] Access denied for method: {}. Required roles: {}", 
                    joinPoint.getSignature().toShortString(), roles);
            throw new CoreException("Access denied. Requester does not possess any of required roles: " + roles, "SECURITY_UNAUTHORIZED");
        }
    }

    private boolean checkSpringSecurityAuthorities(List<String> requiredRoles) {
        try {
            // Check if Spring Security class is present.
            Class<?> contextHolderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = contextHolderClass.getMethod("getContext").invoke(null);
            Object authentication = context.getClass().getMethod("getAuthentication").invoke(context);

            if (authentication == null) {
                log.warn("Spring Security context found but no active Authentication token available. Rejecting authorization.");
                return false;
            }

            java.util.Collection<?> authorities = (java.util.Collection<?>) authentication.getClass().getMethod("getAuthorities").invoke(authentication);
            if (authorities == null || authorities.isEmpty()) {
                return false;
            }

            for (Object authority : authorities) {
                String role = (String) authority.getClass().getMethod("getAuthority").invoke(authority);
                if (role != null) {
                    for (String reqRole : requiredRoles) {
                        if (role.equalsIgnoreCase(reqRole) || 
                            role.equalsIgnoreCase("ROLE_" + reqRole) || 
                            ("ROLE_" + role).equalsIgnoreCase(reqRole)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            log.warn("Spring Security is not present on the classpath. Aspect security checks are secure (fail-closed).");
            return false;
        } catch (Exception e) {
            log.error("Error evaluating Spring Security roles in Aspect", e);
            return false;
        }
    }
}

