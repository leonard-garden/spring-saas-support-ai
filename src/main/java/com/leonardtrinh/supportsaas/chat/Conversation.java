package com.leonardtrinh.supportsaas.chat;

import com.leonardtrinh.supportsaas.common.TenantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chatbot_id", nullable = false)
    private UUID chatbotId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getChatbotId() { return chatbotId; }
    public String getSessionId() { return sessionId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    @Override
    public void setBusinessId(UUID businessId) {
        super.setBusinessId(businessId);
    }
}
