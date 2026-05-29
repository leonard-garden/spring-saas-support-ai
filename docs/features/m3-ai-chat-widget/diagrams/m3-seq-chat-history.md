# Sequence Diagram — Chat History View Flow

## Overview
Shows the admin flow for browsing past conversations from the dashboard: JWT auth + tenant setup, paginated sessions list, "Load more" pagination, and drill-down into a conversation's full message thread with tenant-ownership guard.

## Diagram

```mermaid
sequenceDiagram
    participant Admin as Admin Browser
    participant JwtFilter as JwtAuthFilter
    participant TC as TenantContext
    participant Controller as ConversationController
    participant Service as ConversationService
    participant ConvRepo as ConversationRepository
    participant MsgRepo as ChatMessageRepository
    participant DB as PostgreSQL

    Note over Admin,DB: Phase 1 — Load Conversations List (page 0)

    Admin->>JwtFilter: GET /api/v1/chat/conversations?page=0&size=20
    JwtFilter->>TC: setTenantId(claims.tenantId())
    JwtFilter->>Controller: filterChain.doFilter()
    Controller->>Service: listConversations(page=0, size=20)
    Service->>ConvRepo: findAll(pageable)
    ConvRepo->>DB: SELECT * FROM conversations WHERE business_id = ?\nORDER BY last_message_at DESC LIMIT 20 OFFSET 0
    DB-->>ConvRepo: List<Conversation>
    ConvRepo-->>Service: Page<Conversation>
    Service-->>Controller: Page<ConversationSummary>\n{id, chatbotId, chatbotName, messageCount, lastMessageAt, createdAt}
    Controller-->>Admin: 200 ApiResponse<PagedResult>\n{items: [...], total: 42, page: 0, limit: 20}
    Note over TC: clear() — finally block
    Note over Admin: Render paginated table\nvisitorId · lastMessageAt · messageCount

    Note over Admin,DB: Phase 2 — Load More (page 1)

    Admin->>JwtFilter: GET /api/v1/chat/conversations?page=1&size=20
    JwtFilter->>TC: setTenantId(claims.tenantId())
    JwtFilter->>Controller: filterChain.doFilter()
    Controller->>Service: listConversations(page=1, size=20)
    Service->>ConvRepo: findAll(pageable)
    ConvRepo->>DB: SELECT * FROM conversations WHERE business_id = ?\nORDER BY last_message_at DESC LIMIT 20 OFFSET 20
    DB-->>ConvRepo: List<Conversation>
    ConvRepo-->>Service: Page<Conversation>
    Service-->>Controller: Page<ConversationSummary>
    Controller-->>Admin: 200 ApiResponse<PagedResult>\n{items: [...], total: 42, page: 1, limit: 20}
    Note over TC: clear() — finally block
    Note over Admin: Append rows to table

    Note over Admin,DB: Phase 3 — Conversation Detail (message thread)

    Admin->>JwtFilter: GET /api/v1/chat/conversations/{id}/messages
    JwtFilter->>TC: setTenantId(claims.tenantId())
    JwtFilter->>Controller: filterChain.doFilter()
    Controller->>Service: getMessages(conversationId)
    Service->>ConvRepo: findById(conversationId)
    ConvRepo->>DB: SELECT * FROM conversations WHERE id = ? AND business_id = ?

    alt conversation not found or belongs to another tenant
        DB-->>ConvRepo: empty
        ConvRepo-->>Service: Optional.empty()
        Service-->>Controller: throw ConversationNotFoundException
        Controller-->>Admin: 404 ProblemDetail
    else conversation belongs to tenant
        DB-->>ConvRepo: Conversation
        ConvRepo-->>Service: Optional.of(conversation)
        Service->>MsgRepo: findByConversationId(conversationId)
        MsgRepo->>DB: SELECT * FROM chat_messages WHERE conversation_id = ?\nORDER BY created_at ASC
        DB-->>MsgRepo: List<ChatMessage>
        MsgRepo-->>Service: List<ChatMessage>
        Service-->>Controller: List<ChatMessageResponse>\n{id, role, content, createdAt}
        Controller-->>Admin: 200 ApiResponse<List>\n[{role:USER, content:...}, {role:ASSISTANT, content:...}, ...]
    end
    Note over TC: clear() — finally block
    Note over Admin: Render read-only thread\nUSER messages right-aligned\nASSISTANT messages left-aligned
```

## Key Notes
- `TenantFilterAspect` enables the Hibernate filter before every `ConversationRepository` call, so `findAll()` in Phase 1–2 automatically scopes to the current tenant's `business_id` — no explicit `WHERE` needed in service code.
- Phase 3 does a two-step guard: `findById` goes through the tenant filter (returns empty if cross-tenant), then `ConversationNotFoundException` is thrown before any message query runs — message data is never exposed even partially.
- `clear()` runs in the `JwtAuthFilter` `finally` block unconditionally — tenant context never leaks to the next request on the same thread.
- Pagination uses zero-based page index; `total: 42` lets React compute whether "Load more" should still be shown (`(page + 1) * size < total`).
- Message ordering is `created_at ASC` (chronological) — matches the read-only thread rendering where oldest messages appear at the top.
- No `@Async` boundary in this flow — both list and detail reads are synchronous, inline on the HTTP thread.
