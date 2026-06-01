package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.TenantEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chatbots")
public class Chatbot extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "welcome_message", nullable = false, length = 500)
    private String welcomeMessage;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor = "#3B82F6";

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UUID getId() { return id; }
    public UUID getKbId() { return kbId; }
    public String getName() { return name; }
    public String getWelcomeMessage() { return welcomeMessage; }
    public String getPrimaryColor() { return primaryColor; }
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setKbId(UUID kbId) { this.kbId = kbId; }
    public void setName(String name) { this.name = name; }
    public void setWelcomeMessage(String welcomeMessage) { this.welcomeMessage = welcomeMessage; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public void setActive(boolean active) { isActive = active; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public void setBusinessId(UUID businessId) {
        super.setBusinessId(businessId);
    }
}
