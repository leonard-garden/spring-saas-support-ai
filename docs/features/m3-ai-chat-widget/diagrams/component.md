# Component Diagram — M3: AI Chat + Embeddable Widget

## Overview
System-level view of all components involved in M3, including external integrations (Anthropic Claude, OpenAI Embeddings, PostgreSQL/pgvector) and the two distinct client surfaces (dashboard frontend + embedded widget).

## Diagram

```mermaid
graph TB
    subgraph Clients
        Dashboard["Dashboard SPA\nReact + Zustand"]
        Widget["Embeddable Widget\nvanilla JS, under 50KB"]
        AnyWebsite["Any Website\nembeds widget.js"]
    end

    subgraph Backend["Spring Boot Backend :8081"]
        subgraph Security["Security Layer"]
            JwtFilter["JwtAuthFilter\nOncePerRequestFilter"]
            SecConfig["SecurityConfig\npermitAll: /api/v1/widget/**\npermitAll: /widget.js"]
        end

        subgraph ChatbotPkg["chatbot package"]
            ChatbotCtrl["ChatbotController\nPOST /api/v1/chatbots\nGET /api/v1/chatbots/:id/embed"]
            ChatbotSvc["ChatbotServiceImpl"]
        end

        subgraph ChatPkg["chat package"]
            ChatCtrl["ChatController\nPOST /api/v1/chat/conversations/:id/messages\nSSE"]
            WidgetCtrl["WidgetController\nPOST /api/v1/widget/:chatbotId/chat\nSSE, no JWT"]
            ChatSvc["ChatServiceImpl"]
        end

        subgraph BillingPkg["billing package"]
            MsgUsageSvc["MessageUsageServiceImpl\nquota check + atomic increment"]
        end

        subgraph RAGPipeline["RAG Pipeline - M2"]
            HybridSearch["HybridSearchService\nvector + FTS parallel"]
            VectorStore["VectorStorage\npgvector cosine"]
            FTSStore["FullTextStorage\ntsvector tsquery"]
        end

        subgraph AIPkg["AI Integration"]
            SpringAI["Spring AI ChatClient\nAnthropic"]
            EmbedModel["Spring AI EmbeddingModel\nOpenAI"]
        end

        TenantCtx["TenantContext\nThreadLocal"]
        StaticAsset["Static Asset Server\nGET /widget.js"]
    end

    subgraph PG["PostgreSQL :5432"]
        chatbots_tbl[("chatbots")]
        conversations_tbl[("conversations")]
        messages_tbl[("chat_messages")]
        usage_tbl[("message_usage")]
        chunks_tbl[("document_chunks\npgvector")]
    end

    subgraph ExtAPIs["External APIs"]
        ClaudeAPI["Anthropic Claude API\nclaude-3-5-sonnet / haiku\nstreaming"]
        OpenAIAPI["OpenAI Embeddings API\ntext-embedding-3-small"]
    end

    %% Client connections
    Dashboard -->|"JWT + REST/SSE"| JwtFilter
    AnyWebsite -->|"loads once"| StaticAsset
    StaticAsset -->|"serves"| Widget
    Widget -->|"POST widget chat\nX-Session-Id, no JWT"| SecConfig

    %% Security routing
    JwtFilter -->|"authenticated routes"| ChatCtrl
    JwtFilter -->|"authenticated routes"| ChatbotCtrl
    SecConfig -->|"public route permitAll"| WidgetCtrl

    %% Tenant context
    JwtFilter -->|"setTenantId from JWT"| TenantCtx
    WidgetCtrl -->|"setTenantId from chatbotId lookup"| TenantCtx

    %% Controller to Service
    ChatbotCtrl --> ChatbotSvc
    ChatCtrl --> ChatSvc
    WidgetCtrl --> ChatbotSvc
    WidgetCtrl --> ChatSvc

    %% Chat service dependencies
    ChatSvc --> MsgUsageSvc
    ChatSvc --> HybridSearch
    ChatSvc --> SpringAI

    %% RAG internals
    HybridSearch --> VectorStore
    HybridSearch --> FTSStore
    VectorStore --> chunks_tbl
    FTSStore --> chunks_tbl
    EmbedModel -->|"query embedding"| VectorStore

    %% DB writes
    ChatbotSvc --> chatbots_tbl
    ChatSvc --> conversations_tbl
    ChatSvc --> messages_tbl
    MsgUsageSvc --> usage_tbl

    %% External
    SpringAI -->|"SSE token stream"| ClaudeAPI
    EmbedModel --> OpenAIAPI

    %% Style
    classDef external fill:#f0f0f0,stroke:#999,stroke-dasharray:5
    classDef public fill:#fef3c7,stroke:#d97706
    classDef tenant fill:#dbeafe,stroke:#2563eb
    class ClaudeAPI,OpenAIAPI external
    class WidgetCtrl,StaticAsset,Widget,AnyWebsite public
    class TenantCtx tenant
```

## Key Notes
- **Two trust boundaries:** Authenticated clients (Dashboard) go through `JwtAuthFilter`; the public widget path is `permitAll` and resolves tenant from `chatbotId` DB lookup only.
- **SSE streaming:** Both `ChatController` and `WidgetController` return `SseEmitter`. The stream runs on the HTTP thread — no `@Async`. Spring AI `ChatClient.stream()` returns a `Flux<ChatResponse>` that the controller subscribes to and flushes token-by-token.
- **EmbeddingModel** is called at search time (inside `VectorStorage`) to embed the user query — not at message persist time.
- **`TenantContext`** is the shared mutable state between the security layer and the RAG pipeline. Both paths MUST call `TenantContext.clear()` in `finally`.
- **`widget.js`** is a static asset served by Spring Boot — no build pipeline, no React. It reads `data-chatbot-id` from the `<script>` tag and uses the browser `EventSource` API to consume the SSE stream.
