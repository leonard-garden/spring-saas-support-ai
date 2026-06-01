package com.leonardtrinh.supportsaas.chatbot;

import com.leonardtrinh.supportsaas.common.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "chatbot_knowledge_bases")
public class ChatbotKnowledgeBase extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chatbot_id", nullable = false)
    private UUID chatbotId;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    public UUID getId() { return id; }
    public UUID getChatbotId() { return chatbotId; }
    public UUID getKbId() { return kbId; }

    public void setChatbotId(UUID chatbotId) { this.chatbotId = chatbotId; }
    public void setKbId(UUID kbId) { this.kbId = kbId; }

    @Override
    public void setBusinessId(UUID businessId) {
        super.setBusinessId(businessId);
    }
}
