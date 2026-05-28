# CODEMAPS — spring-saas-support-ai

Navigation guide for the codebase. Use this to orient quickly before diving into a feature area.

---

## Root Layout

```
spring-saas-support-ai/
├── src/main/java/          Backend Java source
├── src/main/resources/     Config files + Flyway migrations
├── src/test/java/          Unit + integration tests
├── frontend/               React admin UI (Vite + TypeScript)
├── docs/
│   ├── architecture.md     Full architecture reference
│   ├── adr/                Architecture Decision Records
│   └── CODEMAPS/           This file + per-domain maps
├── docker-compose.yml      Local infrastructure (postgres, minio, mailhog)
├── pom.xml                 Maven build + dependencies
└── CLAUDE.md               Claude Code harness (entry point for AI)
```

---

## Backend Package Map

Base package: `com.leonardtrinh.supportsaas`

```
auth/
├── AuthController          POST /api/v1/auth/{signup,login,refresh,logout,verify-email,forgot-password,reset-password}
├── AuthService / Impl      Business logic for all auth flows
├── JwtService              Token generation, validation, claims extraction
├── JwtAuthFilter           OncePerRequestFilter — sets TenantContext + SecurityContext
├── RefreshToken            Entity for refresh token storage (hashed)
└── dto/                    SignupRequest, LoginRequest, AuthResponse, ...

tenant/
├── TenantContext           ThreadLocal<UUID> — the isolation anchor
├── TenantFilterAspect      @Around all Repository calls — enables Hibernate filter
├── TenantContextCopyingDecorator  Wraps @Async tasks to copy tenantId to worker threads
└── Business                The tenant entity (does NOT extend TenantEntity)

member/
├── MemberController        GET/DELETE /api/v1/members, PUT /api/v1/members/{id}/role
├── MemberService / Impl
└── Member                  Entity (extends TenantEntity)

invitation/
├── InvitationController    POST /api/v1/invitations, POST /api/v1/invitations/accept
├── InvitationService / Impl
└── Invitation              Entity (extends TenantEntity)

knowledgebase/
├── KnowledgeBaseController CRUD /api/v1/kb
├── KnowledgeBaseService / Impl
└── KnowledgeBase           Entity (extends TenantEntity)

document/
├── DocumentController      POST /api/v1/kb/{kbId}/documents/upload, GET/DELETE endpoints
├── DocumentService / Impl  Upload, status polling, retry orchestration
├── DocumentProcessingServiceImpl  @Async("processingExecutor") — the ingestion pipeline
├── ingestion/
│   ├── IngestionRouter     Selects strategy by MIME type (PDF/TXT/MD)
│   ├── ChunkTextSplitter   500-token chunks, 100-token overlap
│   ├── ContentHashFilter   SHA-256 dedup
│   └── VectorStorage       sanitize → embed → insertChunk
├── chunk/
│   ├── DocumentChunk       Entity (extends TenantEntity) — stores text + embedding
│   └── DocumentChunkRepository  Native SQL: CAST(? AS vector), to_tsvector
└── search/
    ├── SearchController    POST /api/v1/kb/{kbId}/search
    └── HybridSearchServiceImpl  vectorSearch + fullTextSearch + RRF fusion

storage/
└── MinioService            upload/download/delete object storage operations

email/
└── AsyncEmailSender        Interface + no-op dev / Spring Mail prod implementations

billing/
├── Plan                    Enum: FREE, STARTER, PRO, BUSINESS
└── Subscription            Entity stub (extends TenantEntity) — Stripe integration planned

chat/           ← M3 STUB — not yet implemented
chatbot/        ← M3 STUB — not yet implemented

common/
├── ApiResponse<T>          record(boolean success, T data, String error) — all API responses use this
├── TenantEntity            @MappedSuperclass with business_id + Hibernate @FilterDef + @Filter
├── AppException            Base typed exception
├── GlobalExceptionHandler  @RestControllerAdvice → ProblemDetail (RFC 7807)
└── RequestIdFilter         Adds X-Request-ID to every response

config/
├── SecurityConfig          JWT filter chain, CORS, endpoint access rules
├── AsyncConfig             ThreadPoolTaskExecutor wired with TenantContextCopyingDecorator
└── OpenApiConfig           Swagger grouping + Bearer auth scheme
```

---

## Frontend Map (`frontend/src/`)

```
lib/
├── api.ts                  Axios instance with JWT refresh-lock interceptor
├── authApi.ts              login, signup, refresh, logout
├── memberApi.ts            list, remove, changeRole
├── kbApi.ts                CRUD for knowledge bases
└── documentApi.ts          upload, status polling, delete

store/
└── authStore.ts            Zustand: loading | authenticated | unauthenticated

hooks/
├── useAuthInit.ts          Bootstrap auth state on app load
├── useDocuments.ts         Document list + upload
├── useSearch.ts            Hybrid search
└── useStatusPoller.ts      Poll document processing status

pages/                      One file per route (Login, Signup, Dashboard, KB, Members, ...)
components/                 Organized by domain: auth/, kb/, members/, layout/, ui/ (shadcn)
types/                      TypeScript interfaces mirroring backend DTOs
```

---

## Database Migrations (`src/main/resources/db/migration/`)

Flyway sequential scripts: `V{n}__{description}.sql`

| Migration | Purpose |
|-----------|---------|
| V1 | businesses table |
| V2 | members table |
| V3 | invitations table |
| V4 | refresh_tokens table |
| V5 | knowledge_bases table |
| V6 | documents table |
| V7 | document_chunks table (pgvector column) |
| V8+ | incremental additions |

**Rule:** Never use `ddl-auto: create` or `create-drop`. Always `validate`.

---

## Key Flows (where to look for each use case)

| Use case | Entry point | Key files |
|----------|------------|-----------|
| User signup | `AuthController.signup()` | `AuthServiceImpl`, `AsyncEmailSender` |
| JWT validation | `JwtAuthFilter.doFilterInternal()` | `JwtService`, `TenantContext` |
| Upload document | `DocumentController.upload()` | `DocumentServiceImpl`, `MinioService`, `DocumentProcessingServiceImpl` |
| Document ingestion | `DocumentProcessingServiceImpl.processAsync()` | `IngestionRouter`, `VectorStorage`, `DocumentChunkRepository` |
| Hybrid search | `SearchController.search()` | `HybridSearchServiceImpl`, `DocumentChunkRepository` |
| Invite member | `InvitationController.invite()` | `InvitationServiceImpl`, `AsyncEmailSender` |
| Tenant isolation | every repository call | `TenantFilterAspect`, `TenantContext`, `TenantEntity` |

---

## Test Map

```
src/test/java/com/leonardtrinh/supportsaas/
├── auth/           AuthServiceTest, JwtServiceTest
├── tenant/         TenantIsolationIT  ← RELEASE GATE
├── document/       DocumentProcessingServiceTest, VectorStorageTest, HybridSearchIT
└── ...
```

Run all tests: `mvn verify`  
Run single class: `mvn test -Dtest=TenantIsolationIT`  
Required image: `pgvector/pgvector:pg16` (same as production)

---

## Configuration Files

| File | Purpose |
|------|---------|
| `application.yml` | JPA settings, actuator endpoints, JWT defaults |
| `application-dev.yml` | Local DB (localhost:5432), Mailhog SMTP |
| `application-prod.yml` | Render PostgreSQL URL, production SMTP |
| `application-test.yml` | Testcontainers datasource override |
| `docker-compose.yml` | Local infra: postgres, minio, mailhog, adminer |

Active profile set by `SPRING_PROFILES_ACTIVE` env var (hook sets `dev` by default).
