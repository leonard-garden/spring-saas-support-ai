package com.leonardtrinh.supportsaas.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MessageUsageId implements Serializable {

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "year_month")
    private String yearMonth;

    public MessageUsageId() {}

    public MessageUsageId(UUID businessId, String yearMonth) {
        this.businessId = businessId;
        this.yearMonth = yearMonth;
    }

    public UUID getBusinessId() { return businessId; }
    public String getYearMonth() { return yearMonth; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageUsageId that)) return false;
        return Objects.equals(businessId, that.businessId) &&
               Objects.equals(yearMonth, that.yearMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(businessId, yearMonth);
    }
}
