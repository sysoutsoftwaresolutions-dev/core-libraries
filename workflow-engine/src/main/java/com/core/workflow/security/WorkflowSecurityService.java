package com.core.workflow.security;

import com.core.workflow.model.WorkflowSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to validate the security access for state machines.
 * If Spring Security is present on the classpath, it dynamically inspects
 * the active Authentication token's authorities.
 *
 * This bean is open and can be overridden in consumer applications.
 */
@Service
public class WorkflowSecurityService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSecurityService.class);

    /**
     * Checks if the current authentication context matches any of the required roles.
     * Throws {@link WorkflowSecurityException} if unauthorized.
     *
     * @param workflowId    the ID of the workflow to run.
     * @param requiredRoles the list of roles allowed to run the workflow.
     */
    public void checkPermission(String workflowId, List<String> requiredRoles) throws WorkflowSecurityException {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return; // No security constraint defined on the machine
        }

        boolean authorized = checkSpringSecurityAuthorities(requiredRoles);
        
        if (!authorized) {
            log.error("Workflow security check failed for workflow [{}]. Required roles: {}", workflowId, requiredRoles);
            throw new WorkflowSecurityException("User is not authorized to execute workflow: " + workflowId, requiredRoles);
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

            // Get authorities
            java.util.Collection<?> authorities = (java.util.Collection<?>) authentication.getClass().getMethod("getAuthorities").invoke(authentication);
            if (authorities == null || authorities.isEmpty()) {
                return false;
            }

            for (Object authority : authorities) {
                String role = (String) authority.getClass().getMethod("getAuthority").invoke(authority);
                if (role != null) {
                    // Normalize role checks (support both "ROLE_ADMIN" and "ADMIN")
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
            // Spring Security is not on the classpath. For our core library standalone implementation,
            // we will log a warning and fallback to permissive mode.
            log.warn("Spring Security is not present on the classpath. Allowing execution by default. " +
                     "To enforce role checking, ensure spring-security dependencies are active.");
            return true; 
        } catch (Exception e) {
            log.error("Error evaluating Spring Security roles", e);
            return false;
        }
    }
}
