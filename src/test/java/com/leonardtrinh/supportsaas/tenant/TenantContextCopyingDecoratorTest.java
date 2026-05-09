package com.leonardtrinh.supportsaas.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextCopyingDecoratorTest {

    private final TenantContextCopyingDecorator decorator = new TenantContextCopyingDecorator();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("decorated task receives tenant from submitting thread")
    void decorate_propagatesTenantToChildThread() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        AtomicReference<UUID> capturedTenant = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> capturedTenant.set(TenantContext.getTenantId()));

        Thread child = new Thread(decorated);
        child.start();
        child.join();

        assertThat(capturedTenant.get()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("decorated task clears tenant after execution")
    void decorate_clearsTenantAfterTaskRuns() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        AtomicReference<UUID> tenantAfterRun = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            // task runs with tenant set
        });

        Thread child = new Thread(() -> {
            decorated.run();
            tenantAfterRun.set(TenantContext.getTenantId());
        });
        child.start();
        child.join();

        assertThat(tenantAfterRun.get()).isNull();
    }

    @Test
    @DisplayName("decorated task clears tenant even when task throws")
    void decorate_clearsTenantEvenOnException() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        AtomicReference<UUID> tenantAfterRun = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> {
            throw new RuntimeException("task failure");
        });

        Thread child = new Thread(() -> {
            try {
                decorated.run();
            } catch (RuntimeException ignored) {}
            tenantAfterRun.set(TenantContext.getTenantId());
        });
        child.start();
        child.join();

        assertThat(tenantAfterRun.get()).isNull();
    }

    @Test
    @DisplayName("decorated task with null tenant propagates null safely")
    void decorate_withNullTenant_propagatesNullSafely() throws InterruptedException {
        AtomicReference<UUID> capturedTenant = new AtomicReference<>(UUID.randomUUID());
        Runnable decorated = decorator.decorate(() -> capturedTenant.set(TenantContext.getTenantId()));

        Thread child = new Thread(decorated);
        child.start();
        child.join();

        assertThat(capturedTenant.get()).isNull();
    }
}
