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
     * Finds a chatbot by ID for public widget use.
     * Throws ChatbotNotFoundException (404) if the chatbot does not exist.
     * Throws ChatbotInactiveException (403) if the chatbot exists but is inactive.
     */
    Chatbot findActiveChatbot(UUID chatbotId);
}
