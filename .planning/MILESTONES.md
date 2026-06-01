# Milestones

## v0.1 — M1 Backend: Multi-tenant Foundation

**Status:** Shipped
**Date:** 2026-05-13

**Delivered:**
- Spring Boot 3.3 + Java 21 project setup
- Docker compose: postgres + adminer + mailhog
- Business + Member entities + Flyway migrations (V1–V12)
- TenantContext (ThreadLocal) + Hibernate filter
- JwtService with tenant_id claim
- Signup (creates Business + Owner), Login, Refresh, Logout
- Member invitation email flow + accept endpoint
- List/remove members, change role
- GlobalExceptionHandler + OpenAPI config
- Audit logging, billing entities (Plan, Subscription stubs)
- TenantIsolationIT integration test

**Phase count:** No GSD phases tracked (pre-GSD)

---

## v0.2 — M2: Knowledge Base + RAG Pipeline

**Status:** Shipped
**Date:** 2026-05-28

**Delivered:**
- Knowledge Base CRUD (create, list, delete) with tenant isolation
- Document upload (PDF, TXT, MD) → MinIO object storage
- Async ingestion pipeline: extract → chunk (500 tok, 100 overlap) → embed (OpenAI text-embedding-3-small) → PgVector
- SHA-256 content deduplication — skips unchanged chunks on re-index
- Hybrid RAG search: cosine vector + PostgreSQL FTS fused with Reciprocal Rank Fusion → top-5 chunks
- `POST /{id}/retry` — re-queue FAILED documents without re-upload
- Admin dashboard KB UI: document table, upload modal, delete confirm, status badge, chunk count
- Search panel: `useSearch` hook, result cards, loading and empty states
- Bug fix: null byte sanitization before PG insert; `markFailed` runs in independent REQUIRES_NEW transaction
- MinIO bucket auto-init, `.env.example`, pgvector added to CI

**Phase count:** 4 GSD phases (Foundation, Ingestion, Search, Frontend)

**Tag:** v0.2.0 — https://github.com/leonard-garden/spring-saas-support-ai/releases/tag/v0.2.0

---

## v0.3 — M3: AI Chat + Embeddable Widget

**Status:** In progress
**Started:** 2026-05-28

See ROADMAP.md for phases.
