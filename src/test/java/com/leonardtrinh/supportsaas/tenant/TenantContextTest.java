package com.leonardtrinh.supportsaas.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("setTenantId stores value retrievable by getTenantId")
    void setTenantId_stores_and_getTenantId_retrieves() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.setTenantId(tenantId);

        assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("getTenantId returns null when no tenant set")
    void getTenantId_returnsNull_whenNotSet() {
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("clear removes the tenant from current thread")
    void clear_removesStoredTenant() {
        TenantContext.setTenantId(UUID.randomUUID());

        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("tenant is isolated per thread")
    void tenantId_isIsolatedPerThread() throws InterruptedException {
        UUID mainTenant = UUID.randomUUID();
        TenantContext.setTenantId(mainTenant);

        UUID[] childTenant = new UUID[1];
        Thread child = new Thread(() -> childTenant[0] = TenantContext.getTenantId());
        child.start();
        child.join();

        assertThat(childTenant[0]).isNull();
        assertThat(TenantContext.getTenantId()).isEqualTo(mainTenant);
    }
}
