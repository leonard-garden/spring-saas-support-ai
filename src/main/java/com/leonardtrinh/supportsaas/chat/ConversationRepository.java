package com.leonardtrinh.supportsaas.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByChatbotIdAndSessionId(UUID chatbotId, String sessionId);

    // Ordered by last activity DESC. The Hibernate tenant filter applies to root entity c;
    // explicit businessId join predicate makes tenant isolation on the LEFT JOIN explicit.
    @Query(value = """
            SELECT new com.leonardtrinh.supportsaas.chat.ConversationProjection(
                c.id, c.chatbotId, c.sessionId, c.createdAt,
                COUNT(m.id),
                MAX(m.createdAt)
            )
            FROM Conversation c
            LEFT JOIN ChatMessage m ON m.conversationId = c.id AND m.businessId = c.businessId
            GROUP BY c.id, c.chatbotId, c.sessionId, c.createdAt
            ORDER BY COALESCE(MAX(m.createdAt), c.createdAt) DESC
            """,
            countQuery = "SELECT COUNT(c.id) FROM Conversation c")
    Page<ConversationProjection> findAllWithMessageStats(Pageable pageable);
}
