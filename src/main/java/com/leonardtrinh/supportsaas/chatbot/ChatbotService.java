package com.leonardtrinh.supportsaas.chatbot;

import java.util.List;
import java.util.UUID;

public interface ChatbotService {

    ChatbotResponse create(CreateChatbotRequest request);

    List<ChatbotResponse> list();

    ChatbotResponse getById(UUID id);

    ChatbotResponse update(UUID id, UpdateChatbotRequest request);

    void delete(UUID id);

    EmbedResponse getEmbedSnippet(UUID id);

    /**
     * Finds an active chatbot by ID. Used by WidgetController to resolve tenant from chatbotId.
     * Throws ChatbotNotFoundException (404) if not found or is inactive.
     */
    Chatbot findActiveChatbot(UUID chatbotId);
}
