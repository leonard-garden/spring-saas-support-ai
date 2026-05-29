# Software Requirements Specification — M3: AI Chat + Embeddable Widget

**Version:** 1.1
**Date:** 2026-05-29
**Status:** Draft
**Author:** Leonard Trinh

---

## 1. Introduction

### 1.1 Purpose
This document specifies the functional and non-functional requirements for Milestone 3 (v0.3.0) of spring-saas-support-ai. It targets the development team and AI agents implementing the AI Chat runtime and Embeddable Widget features.

### 1.2 Scope
M3 adds two capabilities on top of the M2 RAG pipeline:

1. **AI Chat API** — authenticated dashboard chat where Members/Admins send messages, trigger hybrid RAG retrieval, and receive streaming LLM responses via SSE.
2. **Embeddable Widget** — a public-facing chat endpoint keyed by `chatbotId` enabling any website to embed the chatbot with a single `<script>` tag. Includes chatbot configuration management for Admins.

This milestone does NOT include: billing Stripe integration, voice input, multi-language UI, analytics dashboard, or multi-LLM support.

### 1.3 Definitions & Acronyms
| Term | Definition |
|------|-----------|
| JWT  | JSON Web Token — used for authenticated endpoints |
| RAG  | Retrieval-Augmented Generation — hybrid search → LLM pipeline |
| RRF  | Reciprocal Rank Fusion — combines vector + full-text search rankings |
| SSE  | Server-Sent Events — HTTP streaming used for AI token delivery |
| KB   | Knowledge Base — collection of ingested documents |
| LLM  | Large Language Model — Anthropic Claude (claude-3-5-sonnet / claude-3-haiku) |
| chatbotId | UUID identifying a tenant's chatbot config; used as the public widget key |

### 1.4 References
- [CLAUDE.md](../../../CLAUDE.md)
- [Architecture](../../../.claude/memory/architecture.md)
- [Tech Stack](../../../.claude/memory/tech-stack.md)
- [Constraints](../../../.claude/memory/constraints.md)
- [Multi-Tenancy](../../../.claude/memory/multi-tenancy.md)

---

## 2. Overall Description

### 2.1 Product Perspective
M3 builds on M2's hybrid search pipeline (`HybridSearchService.search(query, topK)`) and the existing `KnowledgeBase`, `Plan`, and `Subscription` entities. It introduces:

- The `chat/` package: `Conversation`, `ChatMessage` entities + streaming chat service
- The `chatbot/` package: `Chatbot` configuration entity + widget embed API

The public widget endpoint (`/api/v1/widget/**`) is unauthenticated and rate-limited. All other endpoints require JWT.

### 2.2 User Classes & Characteristics
| User Class | Description | Access Level |
|-----------|-------------|-------------|
| Business Owner | Created account, configures chatbot, manages KB | ADMIN role (JWT) |
| Member | Invited by owner, uses internal dashboard chat | MEMBER role (JWT) |
| End User | Customer on any website using the embedded widget | PUBLIC (no JWT, identified by session cookie) |

### 2.3 Operating Environment
- Java 21, Spring Boot 3.3
- PostgreSQL 16 + pgvector extension
- Spring AI 1.1+ — `ChatClient` for streaming, `EmbeddingModel` (OpenAI text-embedding-3-small)
- Anthropic Claude: `claude-3-5-sonnet` for quality, `claude-3-haiku` for speed/cost
- Deployed on Render/Railway

### 2.4 Design Constraints
- No Lombok — use Java 21 records for DTOs
- No H2 — integration tests use Testcontainers (`pgvector/pgvector:pg16`)
- No JdbcTemplate — use `@Modifying @Query(nativeQuery=true)` on `JpaRepository`
- No bare `@Async` — always `@Async("processingExecutor")`
- Multi-tenancy: all business entities must extend `TenantEntity` with `business_id`
- `spring.threads.virtual.enabled=false` — virtual threads break ThreadLocal scoping
- SSE streaming is synchronous (not `@Async`) — handled inline in the controller thread via Spring AI's `Flux<ChatResponse>`
- Public widget endpoint bypasses `JwtAuthFilter` — `TenantContext` must be set from `chatbotId` lookup, not JWT
- `SecurityConfig` must add `/api/v1/widget/**` and `/widget.js` to the `permitAll()` block
- CORS `allowedHeaders` must include `X-Session-Id` (used by widget for session continuity)

### 2.5 Assumptions & Dependencies
- M2 is complete: `HybridSearchService`, `KnowledgeBase`, `DocumentChunk`, PgVector embeddings all operational
- `QuotaExceededException` exists in `billing/` and is mapped by `GlobalExceptionHandler`
- `Plan.msgsPerMonth` field is populated from `V8__seed_plans.sql`
- `Subscription` is queryable by `businessId` to check current plan
- Environment variables required: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` (embeddings), `SPRING_AI_ANTHROPIC_CHAT_MODEL`
- Widget JS is a single vanilla-JS file (`<50KB`), served as a static asset or CDN — NOT a React/framework build

---

## 3. Functional Requirements

| ID | Requirement | Priority | Acceptance Criteria |
|----|------------|----------|-------------------|
| FR-001 | Admin can create a Chatbot config linked to one KnowledgeBase | High | `POST /api/v1/chatbots` returns 201 with chatbot JSON including `chatbotId` |
| FR-002 | Admin can update Chatbot config (name, welcome message, color, KB, active flag) | High | `PUT /api/v1/chatbots/{id}` returns 200 with updated fields |
| FR-003 | Admin can delete a Chatbot config | Med | `DELETE /api/v1/chatbots/{id}` returns 204; subsequent widget calls return 404 |
| FR-004 | Admin can retrieve embed code snippet for a Chatbot | High | `GET /api/v1/chatbots/{id}/embed` returns a `<script>` tag string with the correct `chatbotId` |
| FR-005 | Authenticated user can create a new Conversation scoped to a Chatbot | High | `POST /api/v1/chat/conversations` returns 201 with `conversationId` |
| FR-006 | Authenticated user can send a message and receive a streaming SSE response | High | `POST /api/v1/chat/conversations/{id}/messages` streams `text/event-stream`; final event contains full assistant message |
| FR-007 | Chat service performs RAG: hybrid search → top-5 chunks → LLM prompt | High | Response content references knowledge from the linked KB documents |
| FR-008 | Full conversation history is sent to LLM as context (last N turns) | High | LLM can refer back to previous messages in the same conversation |
| FR-009 | Authenticated user can list their conversations | Med | `GET /api/v1/chat/conversations` returns paginated list scoped to tenant |
| FR-010 | Authenticated user can retrieve messages for a conversation | Med | `GET /api/v1/chat/conversations/{id}/messages` returns ordered message list |
| FR-011 | End user can send a message via the public widget endpoint | High | `POST /api/v1/widget/{chatbotId}/chat` returns SSE stream without JWT |
| FR-012 | Widget endpoint resolves tenant from `chatbotId` and sets TenantContext | High | Data returned is strictly isolated to the chatbot's owner tenant |
| FR-013 | Widget endpoint enforces session continuity via `X-Session-Id` header | Med | Messages in same session share conversation history |
| FR-014 | Message quota is enforced per tenant per calendar month | High | When `msgsUsedThisMonth >= plan.msgsPerMonth`, next message returns `QuotaExceededException` mapped to HTTP 429 |
| FR-015 | Message count increments atomically on each successful message (assistant reply stored) | High | DB count matches the number of completed exchanges after concurrent sends |
| FR-016 | Inactive chatbot rejects widget requests | Med | `POST /api/v1/widget/{chatbotId}/chat` returns 404 when `chatbot.isActive = false` |
| FR-017 | Rate limiting on public widget endpoint (per `chatbotId` + IP) | High | > 20 req/min from same IP returns HTTP 429 |
| FR-018 | All chat API endpoints documented in OpenAPI/Swagger | High | Swagger UI shows all endpoints with request/response schemas |
| FR-019 | ChatMessage entity persists both USER and ASSISTANT messages | High | After a completed exchange, DB has 2 rows: role=USER and role=ASSISTANT |
| FR-020 | Widget JS file is served and functional without backend build dependency | Med | `GET /widget.js` returns JS file; widget renders chat UI and sends messages |

---

## 4. Non-Functional Requirements

### 4.1 Performance
- First SSE token must arrive within 3 seconds of message receipt (excludes LLM cold start)
- RAG retrieval (hybrid search + RRF) must complete within 500ms
- Widget JS must be < 50KB unminified

### 4.2 Security
- Public widget endpoint has no JWT — tenant resolution MUST be from DB lookup of `chatbotId` only; never trust user-supplied `tenantId`
- `TenantContext` set from `chatbotId` lookup must be cleared in `finally` (same invariant as JWT path)
- Input validation: `query` max 2000 characters; reject blank queries with 400
- No internal error details (stack traces, SQL) in SSE error events — return generic message
- `chatbotId` is a UUID — not guessable by enumeration for low-sensitivity data, but rate limiting provides the primary public protection
- Prompt injection mitigation: user input is clearly delimited from system prompt in LLM call

### 4.3 Reliability
- If LLM call fails mid-stream: send SSE error event, do NOT persist incomplete assistant message
- If hybrid search returns zero chunks: LLM still responds with a graceful "I don't have information on that" — do NOT abort
- Conversation history truncation: if history exceeds token budget, drop oldest turns (keep system prompt + last 10 turns)

### 4.4 Scalability
- Message quota counter uses optimistic locking or DB-level atomic increment to avoid race conditions under concurrent load
- `processingExecutor` thread pool is NOT used for SSE streaming — streaming runs on the HTTP thread to maintain `OutputStream` reference
- Multi-tenant isolation: `Conversation` and `ChatMessage` extend `TenantEntity`; Hibernate filter auto-applies on all reads

---

## 5. Use Cases

### UC-001: Configure Chatbot
**Actor:** Business Owner (ADMIN)
**Preconditions:** At least one KnowledgeBase exists for the tenant
**Main Flow:**
1. Admin sends `POST /api/v1/chatbots` with `{name, welcomeMessage, primaryColor, kbId}`
2. System validates `kbId` belongs to the tenant
3. System creates `Chatbot` entity, sets `isActive = true`
4. System returns `chatbotId` and embed snippet
**Alternate Flows:**
- A1: `kbId` not found or belongs to another tenant → 404 KnowledgeBaseNotFoundException
**Postconditions:** Chatbot is persisted and widget endpoint is live at `/api/v1/widget/{chatbotId}/chat`

---

### UC-002: Dashboard Chat (Authenticated)
**Actor:** Member or Admin
**Preconditions:** JWT is valid; a Chatbot config exists
**Main Flow:**
1. User sends `POST /api/v1/chat/conversations` → receives `conversationId`
2. User sends `POST /api/v1/chat/conversations/{id}/messages` with `{query}`
3. System persists USER message
4. System calls `HybridSearchService.search(query, 5)` for the linked KB
5. System builds LLM prompt: system instructions + retrieved chunks + conversation history
6. System streams `ChatClient` response via SSE
7. On stream complete: system persists ASSISTANT message, increments quota counter
**Alternate Flows:**
- A1: Quota exceeded → stream immediately closed with SSE error event; HTTP 429 on REST call
- A2: LLM API error → SSE error event; assistant message NOT persisted
**Postconditions:** Both messages stored; quota incremented; conversation history updated

---

### UC-003: Widget Chat (Public)
**Actor:** End User (no JWT)
**Preconditions:** Website has embedded `widget.js` with valid `chatbotId`
**Main Flow:**
1. Widget sends `POST /api/v1/widget/{chatbotId}/chat` with `{query}` and `X-Session-Id` header
2. System looks up `Chatbot` by `chatbotId`; validates `isActive = true`
3. System sets `TenantContext` from chatbot's `businessId`
4. System checks/creates `Conversation` for `sessionId`
5. System performs RAG + LLM streaming (same as UC-002 steps 4–7)
6. SSE stream returned to widget; widget renders tokens progressively
**Alternate Flows:**
- A1: `chatbotId` not found → 404
- A2: Chatbot inactive → 404
- A3: Rate limit exceeded (>20/min same IP) → 429
- A4: Quota exceeded → SSE error event
**Postconditions:** Message persisted under tenant's conversation; quota incremented; `TenantContext` cleared

---

### UC-004: Get Embed Snippet
**Actor:** Business Owner (ADMIN)
**Preconditions:** Chatbot config exists
**Main Flow:**
1. Admin sends `GET /api/v1/chatbots/{id}/embed`
2. System returns HTML snippet:
   ```html
   <script src="https://{host}/widget.js" data-chatbot-id="{chatbotId}"></script>
   ```
**Postconditions:** Admin can paste snippet into any website

---

## 6. External Interface Requirements

### 6.1 API Interfaces

**Chatbot Config (ADMIN, JWT required):**
```
POST   /api/v1/chatbots                    Create chatbot config
GET    /api/v1/chatbots                    List chatbots for tenant
GET    /api/v1/chatbots/{id}               Get chatbot config
PUT    /api/v1/chatbots/{id}               Update chatbot config
DELETE /api/v1/chatbots/{id}               Delete chatbot config
GET    /api/v1/chatbots/{id}/embed         Get embed code snippet
```

**Authenticated Chat (JWT required):**
```
POST   /api/v1/chat/conversations                      Create conversation
GET    /api/v1/chat/conversations                      List conversations (paginated)
GET    /api/v1/chat/conversations/{id}/messages        Get message history
POST   /api/v1/chat/conversations/{id}/messages        Send message → SSE stream
```

**Public Widget (NO JWT — rate limited):**
```
POST   /api/v1/widget/{chatbotId}/chat     Send message → SSE stream
GET    /widget.js                          Serve embeddable widget JS file (<50KB, vanilla JS)
```

See [api-spec.md](api-spec.md) *(create with /api-spec)*

### 6.2 Database Interfaces

New entities / migrations required:

| Table | Key Columns | Notes |
|-------|------------|-------|
| `chatbots` | `id UUID PK`, `business_id UUID FK`, `kb_id UUID FK`, `name`, `welcome_message`, `primary_color`, `is_active`, `created_at` | Extends TenantEntity |
| `conversations` | `id UUID PK`, `business_id UUID FK`, `chatbot_id UUID FK`, `session_id VARCHAR`, `created_at` | Extends TenantEntity; `session_id` for widget continuity |
| `chat_messages` | `id UUID PK`, `business_id UUID FK`, `conversation_id UUID FK`, `role VARCHAR` (USER/ASSISTANT), `content TEXT`, `created_at` | Extends TenantEntity |
| `message_usage` | `business_id UUID PK`, `year_month CHAR(7)`, `msg_count INT` | Quota counter; upsert pattern for atomic increment |

See [db-schema.md](db-schema.md) *(create with /db-schema)*

---

## 7. Out of Scope
- Stripe billing integration (M4)
- Voice input or speech-to-text
- Multi-language widget UI (English only)
- Analytics dashboard (message count charts, satisfaction ratings)
- Multiple LLM providers — Claude only
- Self-hosted LLM support
- Mobile SDK (iOS/Android native)
- Conversation export or download
- Human handoff / live agent escalation
- CSAT / thumbs up-down feedback on messages (post-v1 consideration)
