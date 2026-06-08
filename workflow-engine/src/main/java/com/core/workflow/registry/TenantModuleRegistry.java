package com.core.workflow.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry mapping tenant IDs to their enabled/disabled feature modules.
 * Used for dynamic SaaS module authorization at execution time.
 */
@Component
public class TenantModuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantModuleRegistry.class);

    // Maps tenantId -> Set of enabled modules (lowercased)
    private final Map<String, Set<String>> tenantModules = new ConcurrentHashMap<>();

    /**
     * Enables a module for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @param module   the module name (e.g. "admission", "library")
     */
    public void enableModule(String tenantId, String module) {
        if (tenantId != null && module != null) {
            tenantModules.computeIfAbsent(tenantId.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(module.toLowerCase());
            log.info("[TenantModuleRegistry] Enabled module [{}] for tenant [{}]", module, tenantId);
        }
    }

    /**
     * Disables a module for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @param module   the module name
     */
    public void disableModule(String tenantId, String module) {
        if (tenantId != null && module != null) {
            Set<String> modules = tenantModules.get(tenantId.toLowerCase());
            if (modules != null) {
                modules.remove(module.toLowerCase());
                log.info("[TenantModuleRegistry] Disabled module [{}] for tenant [{}]", module, tenantId);
            }
        }
    }

    /**
     * Checks if a module is enabled for a tenant.
     * If no configuration is set for the tenant, all modules are enabled by default
     * to ensure backward compatibility and smooth testing out of the box.
     *
     * @param tenantId the tenant ID
     * @param module   the module name to check
     * @return true if enabled, false otherwise
     */
    public boolean isModuleEnabled(String tenantId, String module) {
        if (tenantId == null || tenantId.isBlank() || "default".equalsIgnoreCase(tenantId)) {
            return true; // Default tenant has access to all modules
        }
        if (module == null || module.isBlank()) {
            return true; // No module specified means globally enabled
        }
        
        Set<String> modules = tenantModules.get(tenantId.toLowerCase());
        // If the tenant registry is entirely unconfigured, fall back to permissive (enabled)
        if (modules == null) {
            return true;
        }
        return modules.contains(module.toLowerCase());
    }

    /**
     * Clears module configurations for a specific tenant.
     */
    public void clear(String tenantId) {
        if (tenantId != null) {
            tenantModules.remove(tenantId.toLowerCase());
        }
    }

    /**
     * Clears all configurations.
     */
    public void clearAll() {
        tenantModules.clear();
    }
}
