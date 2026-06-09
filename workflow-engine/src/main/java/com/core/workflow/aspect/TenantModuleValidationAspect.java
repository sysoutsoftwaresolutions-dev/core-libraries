package com.core.workflow.aspect;

import com.core.common.context.TenantContext;
import com.core.common.exception.CoreException;
import com.core.workflow.context.StepExecutionContext;
import com.core.workflow.model.StateMachineDefinition;
import com.core.workflow.model.StepDefinition;
import com.core.workflow.registry.TenantModuleRegistry;
import com.core.workflow.registry.WorkflowRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final WorkflowRegistry workflowRegistry;

    @Value("${core.workflow.registry-required:false}")
    private boolean registryRequired;

    public TenantModuleValidationAspect(TenantModuleRegistry tenantModuleRegistry, WorkflowRegistry workflowRegistry) {
        this.tenantModuleRegistry = tenantModuleRegistry;
        this.workflowRegistry = workflowRegistry;
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
    @Before("execution(* com.core.workflow.executor.StepExecutor.execute(..)) && args(context)")
    public void validateStepModule(JoinPoint joinPoint, StepExecutionContext context) throws CoreException {
        String tenantId = TenantContext.getCurrentTenant();
        String machineId = context.getMachineId();
        String stepId = context.getCurrentStepId();

        StateMachineDefinition definition = workflowRegistry.get(machineId);
        if (definition == null) {
            if (registryRequired) {
                log.error("[TenantModuleAspect] Execution blocked: StateMachineDefinition [{}] is not registered in WorkflowRegistry", machineId);
                throw new CoreException(
                        String.format("Execution blocked: StateMachineDefinition '%s' is not registered in WorkflowRegistry", machineId),
                        "WORKFLOW_NOT_REGISTERED"
                );
            }
            log.warn("[TenantModuleAspect] StateMachineDefinition [{}] is not registered in WorkflowRegistry. Skipping step module validation because registry-required=false", machineId);
            return;
        }

        StepDefinition stepDef = definition.getStep(stepId);
        if (stepDef != null) {
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
}
