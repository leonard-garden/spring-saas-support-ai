---
name: Architecture Decisions
description: Multi-tenancy strategy, JWT structure, RAG pipeline, async model for spring-saas-support-ai
type: project
---

# Architecture Decisions

## Multi-tenancy: Row-level isolation

**Approach:** Shared DB, shared schema. Every business table has `tenant_id` FK.

**Why not schema-per-tenant or DB-per-tenant:** Cost too high for MVP; row-level is standard for this scale.

**Implementation:**
- `TenantContext` — ThreadLocal holding current `UUID tenantId`
- Hibernate filter auto-applies `WHERE tenant_id = :tenantId` on all queries
- `JwtAuthFilter` sets context after JWT validation
- Context ALWAYS cleared in `finally` (prevent leak between requests)
- `TenantContextCopyingDecorator` propagates context to `@Async` threads

**Critical invariant:** Tenant isolation test `TenantIsolationIT` must pass 100%. This is non-negotiable.

## JWT structure

```json
{ "sub": "user-uuid", "tenant_id": "tenant-uuid", "role": "ADMIN", "email": "...", "exp": ..., "jti": "token-uuid" }
```

- Access token: 15 minutes
- Refresh token: 7 days, stored as hash in DB (allows revocation)
- Algorithm: HS256 (symmetric, simpler for MVP)

## RAG retrieval (Milestone 2+)

Hybrid approach:
1. Vector search: Top-10 by cosine similarity (PgVector)
2. Keyword search: PG full-text search
3. Reciprocal Rank Fusion to combine rankings
4. Final Top-5 chunks → LLM context

Chunking: paragraph-aware, max 1000 tokens, 100-token overlap, preserve document hierarchy.

## Async processing

Use `@Async` + `ThreadPoolTaskExecutor` (core=4, max=16, queue=100).

**Async:** document ingestion pipeline, email sending, webhook delivery, audit log writing.
**Sync:** API responses, streaming chat (SSE), auth operations.

`TenantContextCopyingDecorator` must be set on the executor to propagate tenant context.

## Streaming chat

Server-Sent Events (SSE) via Spring AI's streaming support. No WebSocket complexity needed.
Widget uses browser `EventSource` API.

## Plan tiers

| Plan | Price | KBs | Docs/KB | Msgs/mo | Members |
|------|-------|-----|---------|---------|---------|
| Free | $0 | 1 | 5 | 100 | 1 |
| Starter | $29 | 3 | 50 | 1,000 | 3 |
| Pro | $99 | 10 | 500 | 10,000 | 10 |
| Business | $299 | ∞ | ∞ | 100,000 | ∞ |

14-day Pro trial on signup, no credit card required. Auto-downgrade to Free after trial.
