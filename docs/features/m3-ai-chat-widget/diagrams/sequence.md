# Sequence Diagram — M3: AI Chat + Embeddable Widget

## Overview
Two flows: (1) authenticated dashboard chat with SSE streaming through the RAG pipeline; (2) public widget chat where tenant is resolved from `chatbotId` instead of JWT.

## Diagram

### Flow 1 — Authenticated Dashboard Chat (SSE)

```mermaid
sequenceDiagram
    participant Client as Dashboard Client
    participant JwtAuthFilter
    participant TC as TenantContext
    participant CC as ChatController
    participant CS as ChatService
    participant MU as MessageUsageService
    participant HSS as HybridSearchService
    participant VStore as VectorStorage
    participant FTS as FullTextStorage
    participant LLM as Claude API (Spring AI)
    participant MsgRepo as ChatMessageRepository
    participant DB as PostgreSQL

    Client->>JwtAuthFilter: POST /api/v1/chat/conversations/{id}/messages<br/>Authorization: Bearer {jwt}<br/>{"query": "How do I reset my password?"}
    JwtAuthFilter->>TC: setTenantId(claims.tenantId())
    JwtAuthFilter->>CC: filterChain.doFilter()

    CC->>CS: sendMessage(conversationId, query)

    CS->>MU: checkQuota(tenantId, yearMonth)
    MU->>DB: SELECT msg_count FROM message_usage WHERE business_id=? AND year_month=?
    DB-->>MU: {msg_count: 42}
    MU->>DB: SELECT msgs_per_month FROM plans p JOIN subscriptions s ON ...
    DB-->>MU: {msgs_per_month: 1000}
    alt quota exceeded
        MU-->>CS: throw QuotaExceededException
        CS-->>CC: QuotaExceededException
        CC-->>Client: HTTP 429 ProblemDetail
    end

    CS->>MsgRepo: save(USER message)
    MsgRepo->>DB: INSERT INTO chat_messages (role='USER', ...)
    DB-->>MsgRepo: saved

    Note over CS,FTS: Hybrid RAG — parallel vector + full-text search
    par Vector search
        CS->>HSS: search(query, topK=5)
        HSS->>VStore: search(query, businessId, 10)
        VStore->>DB: cosine similarity top-10 (pgvector)
        DB-->>VStore: [{chunk, score}, ...]
        VStore-->>HSS: vectorResults
    and Full-text search
        HSS->>FTS: search(query, businessId, 10)
        FTS->>DB: tsvector @@ tsquery top-10
        DB-->>FTS: [{chunk, score}, ...]
        FTS-->>HSS: ftsResults
    end
    HSS-->>CS: top-5 chunks (RRF merged)

    CS->>MsgRepo: findLast10ByConversationId(conversationId)
    MsgRepo->>DB: SELECT * FROM chat_messages WHERE conversation_id=? ORDER BY created_at DESC LIMIT 20
    DB-->>MsgRepo: history

    Note over CS,LLM: Build prompt: system + chunks + history + user query
    CS->>LLM: ChatClient.stream(prompt)
    LLM-->>CC: Flux<ChatResponse> (token stream)

    loop SSE token stream
        CC-->>Client: data: {"token": "..."}
    end

    Note over CC,MsgRepo: Stream complete — persist ASSISTANT message + increment quota
    CC->>MsgRepo: save(ASSISTANT message, fullContent)
    MsgRepo->>DB: INSERT INTO chat_messages (role='ASSISTANT', ...)

    CC->>MU: increment(tenantId, yearMonth)
    MU->>DB: INSERT INTO message_usage ... ON CONFLICT DO UPDATE msg_count + 1

    CC-->>Client: data: {"done": true, "messageId": "uuid"}

    Note over JwtAuthFilter,TC: Request complete
    JwtAuthFilter->>TC: clear() [finally block]
```

---

### Flow 2 — Public Widget Chat (no JWT)

```mermaid
sequenceDiagram
    participant Widget as Widget JS (browser)
    participant WC as WidgetController
    participant TC as TenantContext
    participant CBS as ChatbotService
    participant CS as ChatService
    participant HSS as HybridSearchService
    participant LLM as Claude API (Spring AI)
    participant MsgRepo as ChatMessageRepository
    participant DB as PostgreSQL

    Widget->>WC: POST /api/v1/widget/{chatbotId}/chat<br/>X-Session-Id: {sessionUUID}<br/>{"query": "What is your return policy?"}
    Note over WC: No JWT — bypasses JwtAuthFilter<br/>path is permitAll() in SecurityConfig

    WC->>CBS: findActiveChatbot(chatbotId)
    CBS->>DB: SELECT * FROM chatbots WHERE id=? AND is_active=true
    alt chatbot not found or inactive
        CBS-->>WC: throw ChatbotNotFoundException
        WC-->>Widget: HTTP 404 ProblemDetail
    end
    DB-->>CBS: Chatbot{businessId, kbId, ...}

    Note over WC,TC: Tenant resolved from DB — NOT from user input
    WC->>TC: setTenantId(chatbot.businessId()) [in try block]

    WC->>CS: findOrCreateWidgetConversation(chatbotId, sessionId)
    CS->>DB: SELECT * FROM conversations WHERE chatbot_id=? AND session_id=?
    alt no existing conversation
        CS->>DB: INSERT INTO conversations (chatbot_id, session_id, business_id, ...)
        DB-->>CS: new Conversation
    end
    DB-->>CS: Conversation

    Note over WC,LLM: Same RAG + streaming flow as authenticated chat
    WC->>CS: sendMessage(conversationId, query)
    CS->>HSS: search(query, topK=5)
    HSS->>DB: parallel vector + FTS queries
    DB-->>HSS: top-5 chunks (RRF)
    CS->>LLM: ChatClient.stream(prompt)

    loop SSE token stream
        WC-->>Widget: data: {"token": "..."}
    end

    WC->>MsgRepo: save(ASSISTANT message)
    WC-->>Widget: data: {"done": true, "messageId": "uuid"}

    Note over WC,TC: ALWAYS clear — even on exception
    WC->>TC: clear() [finally block]
```

## Key Notes
- `TenantContext.setTenantId()` is called from two different places: `JwtAuthFilter` (authenticated) and `WidgetController` (public). Both MUST clear in `finally`.
- SSE streaming runs on the HTTP thread — NOT `@Async`. Spring AI `ChatClient.stream()` returns a `Flux<ChatResponse>` that Spring MVC flushes as SSE events.
- `HybridSearchService.search()` internally uses `CompletableFuture` with `taskExecutor` to run vector and FTS in parallel, then merges with RRF.
- ASSISTANT message and quota increment happen AFTER the stream completes. If LLM call fails mid-stream, an `event: error` SSE is sent and nothing is persisted.
- Widget `session_id` links multiple requests to the same `Conversation`. New `sessionId` = new conversation.
