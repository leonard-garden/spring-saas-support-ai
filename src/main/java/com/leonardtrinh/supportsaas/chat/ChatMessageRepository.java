package com.leonardtrinh.supportsaas.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    List<ChatMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
