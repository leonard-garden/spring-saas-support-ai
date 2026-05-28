# Component Diagram — M3: AI Chat + Embeddable Widget

## Overview
System-level view of all components involved in M3, including external integrations (Anthropic Claude, OpenAI Embeddings, PostgreSQL/pgvector) and the two distinct client surfaces (dashboard frontend + embedded widget).

## Diagram

```mermaid
graph TB
    subgraph Clients
        Dashboard["Dashboard SPA<br/>(React + Zustand)"]
        Widget["Embeddable Widget<br/>(vanilla JS, &lt;50KB)"]
        AnyWebsite["Any Website<br/>(embeds widget.js)"]
    end

    subgraph Spring Boot Backend [Spring Boot Backend :8081]
        subgraph Security Layer
            JwtFilter["JwtAuthFilter<br/>(OncePerRequestFilter)"]
            SecConfig["SecurityConfig<br/>permitAll: /api/v1/widget/**<br/>permitAll: /widget.js"]
        end

        subgraph chatbot package
            ChatbotCtrl["ChatbotController<br/>POST /api/v1/chatbots<br/>GET /api/v1/chatbots/{id}/embed"]
            ChatbotSvc["ChatbotServiceImpl"]
        end

        subgraph chat package
            ChatCtrl["ChatController<br/>POST /api/v1/chat/conversations/{id}/messages<br/>(SSE)"]
            WidgetCtrl["WidgetController<br/>POST /api/v1/widget/{chatbotId}/chat<br/>(SSE, no JWT)"]
            ChatSvc["ChatServiceImpl"]
        end

        subgraph billing package
            MsgUsageSvc["MessageUsageServiceImpl<br/>(quota check + atomic increment)"]
        end

        subgraph RAG Pipeline [RAG Pipeline (M2)]
            HybridSearch["HybridSearchService<br/>(vector + FTS parallel)"]
            VectorStore["VectorStorage<br/>(pgvector cosine)"]
            FTSStore["FullTextStorage<br/>(tsvector tsquery)"]
        end

        subgraph AI Integration
            SpringAI["Spring AI ChatClient<br/>(Anthropic)"]
            EmbedModel["Spring AI EmbeddingModel<br/>(OpenAI)"]
        end

        TenantCtx["TenantContext<br/>(ThreadLocal)"]
        StaticAsset["Static Asset Server<br/>GET /widget.js"]
    end

    subgraph PostgreSQL [:5432]
        chatbots_tbl[("chatbots")]
        conversations_tbl[("conversations")]
        messages_tbl[("chat_messages")]
        usage_tbl[("message_usage")]
        chunks_tbl[("document_chunks<br/>(pgvector)")]
    end

    subgraph External APIs
        ClaudeAPI["Anthropic Claude API<br/>claude-3-5-sonnet / haiku<br/>(streaming)"]
        OpenAIAPI["OpenAI Embeddings API<br/>text-embedding-3-small"]
    end

    %% Client connections
    Dashboard -->|"JWT + REST/SSE"| JwtFilter
    AnyWebsite -->|"loads once"| StaticAsset
    StaticAsset -->|"serves"| Widget
    Widget -->|"POST /api/v1/widget/{chatbotId}/chat<br/>X-Session-Id header, no JWT"| SecConfig

    %% Security routing
    JwtFilter -->|"authenticated routes"| ChatCtrl
    JwtFilter -->|"authenticated routes"| ChatbotCtrl
    SecConfig -->|"public route (permitAll)"| WidgetCtrl

    %% Tenant context
    JwtFilter -->|"setTenantId from JWT"| TenantCtx
    WidgetCtrl -->|"setTenantId from chatbotId DB lookup"| TenantCtx

    %% Controller → Service
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
    EmbedModel -->|"query embedding at search time"| VectorStore

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
