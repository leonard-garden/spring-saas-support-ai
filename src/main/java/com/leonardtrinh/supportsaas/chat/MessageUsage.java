package com.leonardtrinh.supportsaas.chat;

import jakarta.persistence.*;

/**
 * Tracks monthly message usage per tenant.
 * Does NOT extend TenantEntity — uses composite PK keyed by business_id directly.
 * Hibernate tenant filter is not needed; all queries explicitly filter by businessId.
 */
@Entity
@Table(name = "message_usage")
public class MessageUsage {

    @EmbeddedId
    private MessageUsageId id;

    @Column(name = "msg_count", nullable = false)
    private int msgCount = 0;

    public MessageUsage() {}

    public MessageUsage(MessageUsageId id) {
        this.id = id;
    }

    public MessageUsageId getId() { return id; }
    public int getMsgCount() { return msgCount; }

    public void setId(MessageUsageId id) { this.id = id; }
    public void setMsgCount(int msgCount) { this.msgCount = msgCount; }
}
