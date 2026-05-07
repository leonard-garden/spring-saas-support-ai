package com.leonardtrinh.supportsaas.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

@MappedSuperclass
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
@Filter(name = "tenantFilter", condition = "business_id = :tenantId")
public abstract class TenantEntity {

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    public UUID getBusinessId() {
        return businessId;
    }

    protected void setBusinessId(UUID businessId) {
        this.businessId = businessId;
    }
}
