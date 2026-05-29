# Sequence Diagram — Admin Widget Configuration Flow

## Overview
Shows the full admin flow for creating and updating a chatbot config from the dashboard: JWT auth setup, initial load, chatbot creation with KB validation, and config update with optional KB reassignment.

## Diagram

```mermaid
sequenceDiagram
    participant Admin as Admin Browser
    participant JwtFilter as JwtAuthFilter
    participant TC as TenantContext
    participant Controller as ChatbotController
    participant Service as ChatbotService
    participant KBRepo as KnowledgeBaseRepository
    participant CBRepo as ChatbotRepository
    participant DB as PostgreSQL

    Note over Admin,DB: Phase 1 — Load Dashboard

    Admin->>JwtFilter: GET /api/v1/chatbots
    JwtFilter->>TC: setTenantId(claims.tenantId())
    JwtFilter->>Controller: filterChain.doFilter()
    Controller->>Service: getAll()
    Service->>CBRepo: findAll()
    CBRepo->>DB: SELECT * FROM chatbots WHERE business_id = ?
    DB-->>CBRepo: []
    CBRepo-->>Service: empty list
    Service-->>Controller: []
    Controller-->>Admin: 200 ApiResponse<List> data=[]
    Note over TC: clear() — finally block
    Note over Admin: No chatbots found — render "Create Widget" CTA

    Note over Admin,DB: Phase 2 — Create Chatbot

    Admin->>JwtFilter: POST /api/v1/chatbots {name, welcomeMessage, primaryColor, kbId}
    JwtFilter->>TC: setTenantId(claims.tenantId())
    JwtFilter->>Controller: filterChain.doFilter()
    Controller->>Service: create(request)
    Service->>KBRepo: findById(kbId)
    KBRepo->>DB: SELECT * FROM knowledge_bases WHERE id = ? AND business_id = ?

    alt kbId not found or belongs to another tenant
        DB-->>KBRepo: empty
        KBRepo-->>Service: Optional.empty()
        Service-->>Controller: throw KnowledgeBaseNotFoundException
        Controller-->>Admin: 404 ProblemDetail
    else kbId valid
        DB-->>KBRepo: KnowledgeBase
        KBRepo-->>Service: Optional.of(kb)
        Service->>CBRepo: save(new Chatbot)
        CBRepo->>DB: INSERT INTO chatbots (id, business_id, kb_id, name, ...)
        DB-->>CBRepo: saved
        CBRepo-->>Service: Chatbot
        Service-->>Controller: ChatbotResponse (includes embedSnippet)
        Controller-->>Admin: 201 ApiResponse<ChatbotResponse>
    end
    Note over TC: clear() — finally block
    Note over Admin: Render Configure form with chatbot data + embed snippet

    Note over Admin,DB: Phase 3 — Update Config

    Admin->>JwtFilter: PUT /api/v1/chatbots/{id} {name, welcomeMessage, primaryColor, kbId}
    JwtFilter->>TC: setTenantId(claims.tenantId())
    JwtFilter->>Controller: filterChain.doFilter()
    Controller->>Service: update(id, request)
    Service->>CBRepo: findById(id)
    CBRepo->>DB: SELECT * FROM chatbots WHERE id = ? AND business_id = ?

    alt chatbot not found or belongs to another tenant
        DB-->>CBRepo: empty
        CBRepo-->>Service: Optional.empty()
        Service-->>Controller: throw ChatbotNotFoundException
        Controller-->>Admin: 404 ProblemDetail
    else chatbot found
        DB-->>CBRepo: Chatbot
        CBRepo-->>Service: Optional.of(chatbot)
        opt kbId changed
            Service->>KBRepo: findById(newKbId)
            KBRepo->>DB: SELECT * FROM knowledge_bases WHERE id = ? AND business_id = ?
            DB-->>KBRepo: KnowledgeBase
            KBRepo-->>Service: Optional.of(kb)
        end
        Service->>CBRepo: save(updatedChatbot)
        CBRepo->>DB: UPDATE chatbots SET name=?, primary_color=?, ... WHERE id = ?
        DB-->>CBRepo: updated
        CBRepo-->>Service: Chatbot
        Service-->>Controller: ChatbotResponse
        Controller-->>Admin: 200 ApiResponse<ChatbotResponse>
    end
    Note over TC: clear() — finally block
    Note over Admin: Show success toast
```

## Key Notes
- `JwtAuthFilter` sets `TenantContext` from the JWT `tenant_id` claim before every request; `clear()` runs unconditionally in the `finally` block.
- `ChatbotRepository.findById()` goes through `TenantFilterAspect` — the Hibernate filter auto-appends `WHERE business_id = ?`, so cross-tenant access is impossible without explicitly disabling the filter (Admin\* repos only).
- KB validation in Phase 2 and Phase 3 uses the same tenant-filtered `KnowledgeBaseRepository` — a MEMBER cannot reference another tenant's KB even if they know the UUID.
- The `embedSnippet` returned in Phase 2 is a pre-built `<script>` tag the admin can paste directly into any website; no further API calls needed to go live.
- Phase 3 `kbId` update is optional — only validated when present in the request body (partial update pattern via `PUT`).
