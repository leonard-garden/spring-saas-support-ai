package com.leonardtrinh.supportsaas.tenant;

import org.springframework.core.task.TaskDecorator;

import java.util.UUID;

public class TenantContextCopyingDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        // Capture tenant from the submitting thread before hand-off
        UUID tenantId = TenantContext.getTenantId();
        return () -> {
            try {
                TenantContext.setTenantId(tenantId);
                task.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
