package com.leonardtrinh.supportsaas.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public interface ChatService {

    ConversationResponse createConversation(UUID chatbotId);

    Page<ConversationSummary> listConversations(Pageable pageable);

    List<ChatMessageResponse> getMessages(UUID conversationId);

    /**
     * Executes the full RAG + LLM streaming pipeline.
     * Returns a Flux of string tokens to be streamed as SSE events.
     * Persists USER message before streaming, ASSISTANT message after completion.
     * {@code assistantMessageIdRef} is populated with the saved ASSISTANT message UUID
     * once the stream completes — controllers use it to emit the {@code done} SSE event.
     */
    Flux<String> streamMessage(UUID conversationId, String query, AtomicReference<UUID> assistantMessageIdRef);

    /**
     * Finds an existing conversation for the widget session, or creates a new one.
     * Used exclusively by WidgetController — TenantContext must be set before calling.
     */
    Conversation findOrCreateWidgetConversation(UUID chatbotId, String sessionId);
}
