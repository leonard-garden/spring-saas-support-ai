package com.leonardtrinh.supportsaas.tenant;

import com.leonardtrinh.supportsaas.common.AppException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TenantNotFoundException extends AppException {

    public TenantNotFoundException(UUID tenantId) {
        super(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found: id=" + tenantId);
    }
}
