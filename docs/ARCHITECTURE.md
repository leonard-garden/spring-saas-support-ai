# Architecture — spring-saas-support-ai

**Style:** Modular monolith  
**Stack:** Java 21 · Spring Boot 3.3.5 · PostgreSQL 16 + pgvector · Spring AI 1.0.0  
**Current milestone:** M3 — AI Chat + Embeddable Widget

---

## High-Level Overview

```
┌──────────────────────────────────────────────────────┐
│                  React Admin UI (:3000)               │
│              Vanilla JS Widget (<50 KB)               │
└────────────────────┬─────────────────────────────────┘
                     │ HTTPS / SSE
┌────────────────────▼─────────────────────────────────┐
│              Spring Boot API (:8081)                  │
│  auth/ · tenant/ · member/ · invitation/              │
│  knowledgebase/ · document/ · chat/ · chatbot/        │
│  billing/ · storage/ · email/ · common/ · config/     │
└──────┬──────────────┬──────────────┬─────────────────┘
       │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌───▼────────────────┐
│ PostgreSQL  │ │   MinIO    │ │  Anthropic Claude  │
│ + pgvector  │ │  (:9000)   │ │  OpenAI embeddings │
└─────────────┘ └────────────┘ └────────────────────┘
```

---

## Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.5 |
| Build | Maven | 3.x |
| Database | PostgreSQL | 16 |
| Vector store | pgvector | same DB instance |
| ORM | Spring Data JPA + Hibernate | via Spring Boot 3.3.5 |
| Migrations | Flyway | via Spring Boot 3.3.5 |
| AI framework | Spring AI | 1.0.0 |
| Primary LLM | Anthropic Claude | claude-3-5-sonnet / claude-3-haiku |
| Embeddings | OpenAI | text-embedding-3-small |
| Auth | JJWT | 0.12.6 |
| API Docs | springdoc-openapi | 2.6.0 |
| Tests | JUnit 5 + Testcontainers | 1.20.3 |
| Object storage | MinIO | latest |
| CI/CD | GitHub Actions | — |
| Hosting | Render free tier | — |

---

## Directory Map

```
src/main/java/com/leonardtrinh/supportsaas/
├── auth/           JWT auth flow, token refresh, password reset, email verification
├── tenant/         TenantContext (ThreadLocal), Hibernate filter, Business entity
├── member/         Member entity, role management (OWNER/ADMIN/MEMBER)
├── invitation/     Invite tokens, accept flow
├── knowledgebase/  KB CRUD
├── document/       Upload API, processing orchestration, retry
│   ├── ingestion/  IngestionRouter, ChunkTextSplitter, ContentHashFilter, VectorStorage
│   ├── chunk/      DocumentChunk entity + repository
│   └── search/     HybridSearchService, SearchController
├── storage/        MinioService (object storage)
├── email/          AsyncEmailSender interface + Spring Mail implementation
├── billing/        Plan, Subscription stubs (Stripe planned)
├── chat/           (M3 stub) Spring AI streaming SSE chat
├── chatbot/        (M3 stub) Embeddable widget backend
├── audit/          Structured audit log
├── common/         ApiResponse<T>, AppException, GlobalExceptionHandler, TenantEntity base
├── config/         SecurityConfig, AsyncConfig (thread pool + tenant decorator)
└── admin/          Super-admin cross-tenant endpoints

src/main/resources/
├── application.yml           shared config (JPA, actuator, JWT defaults)
├── application-dev.yml       local DB + Mailhog SMTP
├── application-prod.yml      Render PostgreSQL + prod SMTP
├── application-test.yml      Testcontainers overrides
└── db/migration/             Flyway V1–Vn SQL migrations
```

---

## Multi-Tenancy: Row-Level Isolation

Every business table has `business_id UUID`. Hibernate auto-applies
`WHERE business_id = :tenantId` via a named filter on all queries.

```
HTTP Request → JwtAuthFilter (sets TenantContext) →
Controller → Service → TenantFilterAspect (enables filter) →
Repository (SQL auto-filtered) → JwtAuthFilter finally (clears TenantContext)
```

**Critical invariant:** `TenantContext.clear()` must be in a `finally` block —
servlet threads are reused and a leaked `tenantId` causes cross-tenant data exposure.

**@Async threads:** Must use `@Async("processingExecutor")`. `AsyncConfig` wires
`TenantContextCopyingDecorator` which copies `tenantId` to the worker thread before
the task runs and clears it after.

See `.claude/memory/multi-tenancy.md` for the full flow diagram.

---

## Document Ingestion Pipeline

```
POST /api/v1/kb/documents/upload
  └─ DocumentServiceImpl.upload()
       saves PENDING → MinIO store → triggers @Async
  └─ DocumentProcessingServiceImpl.processAsync()
       @Async("processingExecutor") + @Transactional(REQUIRES_NEW)
       ├─ IngestionRouter.route()       — selects strategy by MIME
       ├─ ChunkTextSplitter.split()     — 500-token chunks, 100-token overlap
       ├─ ContentHashFilter.isNew()     — SHA-256 dedup per document
       └─ VectorStorage.store()
            ├─ sanitize()              — strips \x00 + C0 controls (PDFBox artefacts)
            ├─ embed()                 — OpenAI text-embedding-3-small
            └─ DocumentChunkRepository.insertChunk()
                 native SQL: CAST(? AS vector), to_tsvector
```

**Transaction safety:** If `processAsync` fails mid-way, `markFailed()` must run in a
separate `TransactionTemplate(REQUIRES_NEW)` — the outer transaction is rolling back
and cannot be used.

---

## Hybrid Search (RAG Retrieval)

```
HybridSearchServiceImpl.search(query, kbId)
  ├─ vectorSearch()     — cosine similarity top-10  (PgVector)
  ├─ fullTextSearch()   — tsvector @@ tsquery top-10 (PostgreSQL FTS)
  └─ Reciprocal Rank Fusion → top-5 chunks → LLM context
```

---

## Auth & JWT

```json
{
  "sub":       "user-uuid",
  "tenant_id": "tenant-uuid",
  "role":      "ADMIN",
  "email":     "...",
  "exp":       1234567890,
  "jti":       "token-uuid"
}
```

- Access token: 15 min (HS256)
- Refresh token: 7 days, stored as hash in DB (revocable)
- Password reset + email verification tokens are single-use, time-limited

---

## Async Processing

`ThreadPoolTaskExecutor` — core 4, max 16, queue 100.
`TenantContextCopyingDecorator` propagates `tenantId` to worker threads.

| Mode | Used for |
|------|---------|
| Async | document ingestion, email sending, audit log writing |
| Sync | all API responses, streaming chat (SSE), auth operations |

---

## Package Conventions

```
{package}/
├── {Entity}.java            — JPA entity (extends TenantEntity for business data)
├── {Entity}Repository.java  — Spring Data JPA + native queries for pgvector/tsvector
├── {Domain}Service.java     — interface
├── {Domain}ServiceImpl.java — implementation, @Transactional
├── {Domain}Controller.java  — REST controller, ApiResponse<T> envelope
└── dto/                     — Java records (request/response DTOs)
```

---

## Key Architectural Decisions

See `docs/adr/` for full records. Summary:

| Decision | Choice | Reason |
|----------|--------|--------|
| Multi-tenancy | Row-level (shared DB) | Cost-effective for MVP |
| ORM queries | native @Query for pgvector/tsvector | JPA can't express CAST(? AS vector) |
| Async | @Async + ThreadPoolTaskExecutor | No Kafka complexity needed |
| Auth | JJWT HS256 | Simple and sufficient for MVP |
| Vector store | PgVector (same DB) | No extra infrastructure |
| Search | Hybrid (vector + FTS + RRF) | Better recall than vector-only |
| No Lombok | Java 21 records | Cleaner, no annotation processing |
| No H2 | Testcontainers | Real DB behavior in tests |
| No virtual threads | Disabled | ThreadLocal TenantContext incompatible |

---

## Infrastructure (docker-compose)

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| postgres | pgvector/pgvector:pg16 | 5432 | Primary DB + vector store |
| adminer | adminer:latest | 8090 | DB admin UI |
| mailhog | mailhog/mailhog:latest | 1025/8025 | Dev email |
| minio | minio/minio:latest | 9000/9001 | Object storage |

---

## Test Strategy

- Unit tests (`*Test.java`): JUnit 5, `@ExtendWith(MockitoExtension.class)`, no Spring context
- Integration tests (`*IT.java`): `@SpringBootTest` + Testcontainers (`pgvector/pgvector:pg16`)
- `TenantIsolationIT` is the release gate — must pass 100%
- Coverage targets: 80% LOC overall, 100% service layer, 100% tenant isolation paths
- `ddl-auto: validate` always — never `create` or `create-drop`

---

## Planned (M3+)

- `chat/` — Spring AI streaming SSE chat endpoint
- `chatbot/` — Embeddable vanilla JS widget backend
- Billing: Stripe integration (currently stubs)
- SSO: planned post-M3
- Spotless (google-java-format): planned for pom.xml
