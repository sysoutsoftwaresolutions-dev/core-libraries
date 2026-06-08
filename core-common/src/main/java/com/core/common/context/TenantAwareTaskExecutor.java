package com.core.common.context;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Decorator for Spring's {@link AsyncTaskExecutor} that propagates the current
 * tenant context from the submitting thread to the executing thread.
 */
public class TenantAwareTaskExecutor implements AsyncTaskExecutor {

    private final AsyncTaskExecutor delegate;

    public TenantAwareTaskExecutor(AsyncTaskExecutor delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate executor must not be null");
        }
        this.delegate = delegate;
    }

    @Override
    public void execute(@NonNull Runnable task) {
        String tenantId = TenantContext.getCurrentTenant();
        delegate.execute(wrap(task, tenantId));
    }

    @Override
    public void execute(@NonNull Runnable task, long startTimeout) {
        String tenantId = TenantContext.getCurrentTenant();
        delegate.execute(wrap(task, tenantId), startTimeout);
    }

    @Override
    @NonNull
    public Future<?> submit(@NonNull Runnable task) {
        String tenantId = TenantContext.getCurrentTenant();
        return delegate.submit(wrap(task, tenantId));
    }

    @Override
    @NonNull
    public <T> Future<T> submit(@NonNull Callable<T> task) {
        String tenantId = TenantContext.getCurrentTenant();
        return delegate.submit(wrap(task, tenantId));
    }

    private Runnable wrap(Runnable task, String tenantId) {
        return () -> {
            if (tenantId != null) {
                TenantContext.setCurrentTenant(tenantId);
            } else {
                TenantContext.clear();
            }
            try {
                task.run();
            } finally {
                TenantContext.clear();
            }
        };
    }

    private <T> Callable<T> wrap(Callable<T> task, String tenantId) {
        return () -> {
            if (tenantId != null) {
                TenantContext.setCurrentTenant(tenantId);
            } else {
                TenantContext.clear();
            }
            try {
                return task.call();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
