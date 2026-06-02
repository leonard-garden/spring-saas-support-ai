package com.leonardtrinh.supportsaas.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * Counts USER messages sent by a tenant since the given billing period start.
     * Used by {@link MessageUsageServiceImpl} to enforce monthly message quotas
     * using the Stripe billing window (currentPeriodStart) rather than calendar months.
     */
    long countByBusinessIdAndRoleAndCreatedAtGreaterThanEqual(
            UUID businessId, MessageRole role, Instant periodStart);
}
