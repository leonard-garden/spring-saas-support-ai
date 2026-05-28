# Class Diagram — M3: AI Chat + Embeddable Widget

## Overview
Entity hierarchy, service interfaces, and controller dependencies for the `chat/` and `chatbot/` packages. Shows how M3 classes relate to existing M2 foundations (`TenantEntity`, `HybridSearchService`, `KnowledgeBase`).

## Diagram

```mermaid
classDiagram
    %% ─── Base / Shared ───────────────────────────────────────────
    class TenantEntity {
        <<MappedSuperclass>>
        #UUID businessId
        +getBusinessId() UUID
        #setBusinessId(UUID)
    }

    class KnowledgeBase {
        -UUID id
        -Instant createdAt
        -Instant updatedAt
    }
    TenantEntity <|-- KnowledgeBase

    %% ─── chatbot package ─────────────────────────────────────────
    class Chatbot {
        -UUID id
        -UUID kbId
        -String name
        -String welcomeMessage
        -String primaryColor
        -boolean isActive
        -Instant createdAt
        -Instant updatedAt
    }
    TenantEntity <|-- Chatbot
    KnowledgeBase "1" --> "0..*" Chatbot : kbId

    class ChatbotService {
        <<interface>>
        +create(CreateChatbotRequest) ChatbotResponse
        +list() List~ChatbotResponse~
        +getById(UUID) ChatbotResponse
        +update(UUID, UpdateChatbotRequest) ChatbotResponse
        +delete(UUID)
        +getEmbedSnippet(UUID) EmbedResponse
        +findActiveChatbot(UUID) Chatbot
    }

    class ChatbotServiceImpl {
        -ChatbotRepository chatbotRepository
        -KnowledgeBaseRepository kbRepository
    }
    ChatbotService <|.. ChatbotServiceImpl

    class ChatbotController {
        -ChatbotService chatbotService
        +create(CreateChatbotRequest) ApiResponse~ChatbotResponse~
        +list() ApiResponse~List~ChatbotResponse~~
        +getById(UUID) ApiResponse~ChatbotResponse~
        +update(UUID, UpdateChatbotRequest) ApiResponse~ChatbotResponse~
        +delete(UUID)
        +getEmbedSnippet(UUID) ApiResponse~EmbedResponse~
    }
    ChatbotController --> ChatbotService

    %% ─── chat package ────────────────────────────────────────────
    class Conversation {
        -UUID id
        -UUID chatbotId
        -String sessionId
        -Instant createdAt
    }
    TenantEntity <|-- Conversation
    Chatbot "1" --> "0..*" Conversation : chatbotId

    class ChatMessage {
        -UUID id
        -UUID conversationId
        -MessageRole role
        -String content
        -Instant createdAt
    }
    TenantEntity <|-- ChatMessage
    Conversation "1" --> "0..*" ChatMessage : conversationId

    class MessageRole {
        <<enumeration>>
        USER
        ASSISTANT
    }
    ChatMessage --> MessageRole

    class ChatService {
        <<interface>>
        +createConversation(UUID chatbotId) ConversationResponse
        +listConversations(Pageable) Page~ConversationSummary~
        +getMessages(UUID conversationId) List~ChatMessageResponse~
        +streamMessage(UUID conversationId, String query) Flux~String~
        +findOrCreateWidgetConversation(UUID chatbotId, String sessionId) Conversation
    }

    class ChatServiceImpl {
        -ConversationRepository conversationRepository
        -ChatMessageRepository messageRepository
        -ChatbotRepository chatbotRepository
        -HybridSearchService hybridSearchService
        -MessageUsageService messageUsageService
        -ChatClient chatClient
    }
    ChatService <|.. ChatServiceImpl

    class ChatController {
        -ChatService chatService
        +createConversation(CreateConversationRequest) ApiResponse~ConversationResponse~
        +listConversations(Pageable) ApiResponse~PageResponse~ConversationSummary~~
        +getMessages(UUID) ApiResponse~List~ChatMessageResponse~~
        +streamMessage(UUID, SendMessageRequest) SseEmitter
    }
    ChatController --> ChatService

    class WidgetController {
        -ChatbotService chatbotService
        -ChatService chatService
        -TenantContext tenantContext
        +chat(UUID chatbotId, String sessionId, SendMessageRequest) SseEmitter
    }
    WidgetController --> ChatbotService
    WidgetController --> ChatService

    %% ─── quota / billing ─────────────────────────────────────────
    class MessageUsage {
        -MessageUsageId id
        -int msgCount
    }

    class MessageUsageId {
        <<Embeddable>>
        -UUID businessId
        -String yearMonth
    }
    MessageUsage --> MessageUsageId

    class MessageUsageService {
        <<interface>>
        +checkQuota(UUID businessId, String yearMonth)
        +increment(UUID businessId, String yearMonth)
    }

    class MessageUsageServiceImpl {
        -MessageUsageRepository usageRepository
        -SubscriptionRepository subscriptionRepository
        -PlanRepository planRepository
    }
    MessageUsageService <|.. MessageUsageServiceImpl
    ChatServiceImpl --> MessageUsageService

    %% ─── M2 dependency ───────────────────────────────────────────
    class HybridSearchService {
        <<interface>>
        +search(String query, int topK) List~SearchResult~
    }
    ChatServiceImpl --> HybridSearchService

    %% ─── Spring AI ───────────────────────────────────────────────
    class ChatClient {
        <<Spring AI>>
        +prompt(Prompt) ChatClientRequestSpec
        +stream(Prompt) Flux~ChatResponse~
    }
    ChatServiceImpl --> ChatClient
```

## Key Notes
- `WidgetController` sets `TenantContext` manually from `ChatbotService.findActiveChatbot()` result, then clears it in `finally` — mirrors what `JwtAuthFilter` does for authenticated paths.
- `MessageUsage` does NOT extend `TenantEntity`. Its composite PK `(business_id, year_month)` makes Hibernate tenant filter unnecessary.
- `ChatServiceImpl` depends on `HybridSearchService` (M2) — M3 does not re-implement search.
- `ChatClient` is a Spring AI managed bean configured via `ANTHROPIC_API_KEY` in `application.yml`.
- All repository interfaces follow Spring Data JPA convention and are not shown in this diagram for brevity.
