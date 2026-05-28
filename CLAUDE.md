# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@.claude/memory/project-context.md
@.claude/memory/architecture.md
@.claude/memory/tech-stack.md
@.claude/memory/constraints.md
@.claude/memory/multi-tenancy.md

---

## Commands

```bash
# Backend
mvn clean package -DskipTests          # build JAR
mvn test                               # unit tests only
mvn verify                             # unit + integration tests (Testcontainers)
mvn test -Dtest=TenantIsolationIT      # single test class
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Infrastructure (required before running backend)
docker-compose up -d                   # postgres + pgvector + MinIO + adminer + mailhog

# Frontend (in frontend/)
npm run dev                            # dev server on :3000
npm test                               # vitest watch
npm run test:coverage                  # coverage report
npm run build                          # tsc + vite build

# Database
mvn flyway:info
mvn flyway:migrate
```

Backend runs on `:8081`. Swagger UI: `http://localhost:8081/swagger-ui.html`.

---

## Architecture

### Package layout

```
com.leonardtrinh.supportsaas
├── auth/           # JWT, signup, login, refresh, password reset, email verification
├── tenant/         # TenantContext (ThreadLocal), Hibernate filter, Business entity
├── member/         # Member entity, role management
├── invitation/     # Invitation flow, accept endpoint
├── knowledgebase/  # KB CRUD
├── document/       # Upload API, processing orchestration, retry
│   ├── ingestion/  # IngestionRouter, ChunkTextSplitter, ContentHashFilter, VectorStorage
│   ├── chunk/      # DocumentChunk entity + repository
│   └── search/     # HybridSearchService, SearchController
├── storage/        # MinioService (object storage)
├── email/          # AsyncEmailSender (no-op dev / Spring Mail prod)
├── billing/        # Plan, Subscription stubs
├── common/         # ApiResponse<T>, TenantEntity, GlobalExceptionHandler, AppException
└── config/         # SecurityConfig, AsyncConfig, OpenApiConfig, RequestIdFilter
```

Each package: `Controller → Service (interface + impl) → Repository → Entity`. DTOs are Java records.

### Frontend layout (`frontend/src/`)

```
lib/          # axios instance (api.ts), per-domain API modules (*Api.ts), tokenStorage
store/        # authStore (Zustand tri-state: loading | authenticated | unauthenticated)
hooks/        # useAuthInit, useDocuments, useSearch, useStatusPoller
pages/        # one file per route
components/   # auth/, dashboard/, kb/, layout/, members/, ui/ (shadcn)
types/        # TypeScript interfaces mirroring backend DTOs
```

`lib/api.ts` holds the axios instance with the refresh-lock interceptor — all auth token logic lives there, not in individual API modules.

### Document ingestion pipeline

```
POST /api/v1/kb/documents/upload
  → DocumentServiceImpl.upload()        # saves PENDING, stores to MinIO, triggers async
  → DocumentProcessingServiceImpl.processAsync()   @Async("processingExecutor")
                                                    @Transactional(REQUIRES_NEW)
      → IngestionRouter.route()         # selects strategy by MIME type (PDF/TXT/MD)
      → ChunkTextSplitter.split()       # 500-token chunks, 100-token overlap
      → ContentHashFilter.isNew()       # SHA-256 dedup per document
      → VectorStorage.store()           # sanitize → embed → insertChunk
          → DocumentChunkRepository.insertChunk()  # native SQL: CAST(? AS vector), to_tsvector
```

**Critical**: `processAsync` runs in its own `REQUIRES_NEW` transaction. If it fails mid-way (e.g. PostgreSQL rejects a chunk), the main transaction is aborted. `markFailed` must run via `requiresNewTx.execute(...)` (a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`) so the document is not left stuck in `PROCESSING`.

`VectorStorage.sanitize()` strips null bytes and C0 control characters (`\x00-\x08\x0B\x0C\x0E-\x1F`) — PDFBox can embed these in extracted text and PostgreSQL rejects `\x00` entirely.

### Hybrid search

`HybridSearchServiceImpl` runs two queries in parallel then fuses:
1. `chunkRepository.vectorSearch()` — cosine similarity top-10 (PgVector)
2. `chunkRepository.fullTextSearch()` — `tsvector @@ tsquery` top-10
3. Reciprocal Rank Fusion (RRF) → top-5 chunks returned

### Multi-tenancy

`JwtAuthFilter` sets `TenantContext.setTenantId()` and clears it in `finally`. `TenantFilterAspect` (AOP) enables the Hibernate filter before every repository call. All business entities extend `TenantEntity` which carries `businessId` and the filter definition.

`@Async` methods **must** use `@Async("processingExecutor")` (never bare `@Async`) — `AsyncConfig` wires `TenantContextCopyingDecorator` on that executor to propagate `tenantId` to worker threads.

Virtual threads are **disabled** (`spring.threads.virtual.enabled=false`) because `ThreadLocal`-based `TenantContext` is incompatible with virtual thread pinning semantics.

---

## Coding rules

- **No Lombok** — use Java 21 records for DTOs, modern syntax elsewhere
- **No H2** — integration tests use Testcontainers (`PostgreSQLContainer`)
- **No JdbcTemplate** — use `@Modifying @Query(nativeQuery=true)` on `JpaRepository` even for pgvector/tsvector casts
- **No Kafka/RabbitMQ** — `@Async` + `ThreadPoolTaskExecutor`
- **No microservices** — monolith only

Error handling: typed exceptions (`DocumentNotFoundException`, `QuotaExceededException`, …) → `GlobalExceptionHandler` maps to `ProblemDetail` (RFC 7807). Never return `null` — use `Optional<T>` or throw.

API envelope:
```java
record ApiResponse<T>(boolean success, T data, String error) {
    static <T> ApiResponse<T> ok(T data)       { return new ApiResponse<>(true, data, null); }
    static <T> ApiResponse<T> fail(String msg) { return new ApiResponse<>(false, null, msg); }
}
```

---

## Testing rules

- Unit tests: `{ClassName}Test`, `@ExtendWith(MockitoExtension.class)`
- Integration tests: `{ClassName}IT`, `@SpringBootTest @Testcontainers`
- `TenantIsolationIT` must pass before any release
- Minimum 60% coverage on service layer; 100% on tenant isolation paths
- The pgvector PostgreSQL image used in CI is `pgvector/pgvector:pg16` — use the same locally

---

## Current milestone

**M3 — AI Chat + Embeddable Widget** (`v0.3`). See GitHub milestone for open issues.
`chat/` and `chatbot/` packages are stubs — streaming SSE via Spring AI is the target implementation.
