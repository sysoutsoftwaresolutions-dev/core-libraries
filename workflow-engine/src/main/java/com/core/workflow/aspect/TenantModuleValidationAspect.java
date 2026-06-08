package com.core.workflow.aspect;

import com.core.common.context.TenantContext;
import com.core.common.exception.CoreException;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.model.StepDefinition;
import com.core.workflow.registry.TenantModuleRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aspect intercepting workflow and step executions to validate whether
 * the respective modules are enabled for the current tenant.
 */
@Aspect
@Component
public class TenantModuleValidationAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantModuleValidationAspect.class);
    private final TenantModuleRegistry tenantModuleRegistry;

    public TenantModuleValidationAspect(TenantModuleRegistry tenantModuleRegistry) {
        this.tenantModuleRegistry = tenantModuleRegistry;
    }

    /**
     * Intercepts workflow startup execution.
     */
    @Before("execution(* com.core.workflow.engine.StateMachineEngine.execute(..)) && args(definition, ..)")
    public void validateWorkflowModule(JoinPoint joinPoint, StateMachineDefinition definition) throws CoreException {
        String tenantId = TenantContext.getCurrentTenant();
        String module = definition.getModule();
        
        if (module != null && !tenantModuleRegistry.isModuleEnabled(tenantId, module)) {
            log.error("[TenantModuleAspect] Start blocked. Module [{}] is disabled for tenant [{}] during workflow [{}] execution", 
                    module, tenantId, definition.getId());
            throw new CoreException(
                    String.format("Execution blocked: module '%s' is not enabled for tenant '%s'", module, tenantId), 
                    "TENANT_MODULE_DISABLED"
            );
        }
    }

    /**
     * Intercepts individual step executions.
     */
    @Before("execution(* com.core.workflow.engine.StateMachineEngine.executeStepWithRetry(..)) && args(stepDef, ..)")
    public void validateStepModule(JoinPoint joinPoint, StepDefinition stepDef) throws CoreException {
        String tenantId = TenantContext.getCurrentTenant();
        String module = stepDef.getModule();
        
        if (module != null && !tenantModuleRegistry.isModuleEnabled(tenantId, module)) {
            log.error("[TenantModuleAspect] Step blocked. Module [{}] is disabled for tenant [{}] during step [{}] execution", 
                    module, tenantId, stepDef.getId());
            throw new CoreException(
                    String.format("Execution blocked: module '%s' is not enabled for tenant '%s'", module, tenantId), 
                    "TENANT_MODULE_DISABLED"
            );
        }
    }
}
