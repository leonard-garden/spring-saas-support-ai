# Architecture — spring-saas-support-ai

**Date:** 2026-05-10 | **Version:** 0.1.0-SNAPSHOT

## Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.5 |
| Build | Maven | 3.x |
| Database | PostgreSQL | 16 |
| ORM | Spring Data JPA + Hibernate | via Spring Boot 3.3.5 |
| Migrations | Flyway | via Spring Boot 3.3.5 |
| Auth | JJWT | 0.12.6 |
| API Docs | springdoc-openapi | 2.6.0 |
| Tests | JUnit 5 + Testcontainers | 1.20.3 |
| Container | Docker multi-stage build | — |
| CI/CD | GitHub Actions | — |
| Hosting | Render free tier | — |

## Directory Map

```
src/main/java/com/leonardtrinh/supportsaas/
├── auth/           JWT auth flow, token refresh, password reset, email verification
├── tenant/         TenantContext (ThreadLocal), Hibernate filter, Business entity
├── member/         Member entity, role management (OWNER/ADMIN/MEMBER)
├── invitation/     Invite tokens, accept flow
├── billing/        Plan, Subscription, quota enforcement
├── email/          AsyncEmailSender interface + Spring Mail implementation
├── audit/          Structured audit log (entity + repository)
├── common/         ApiResponse<T>, AppException, GlobalExceptionHandler, TenantEntity base
├── config/         SecurityConfig, AsyncConfig (thread pool + tenant decorator)
├── document/       (M2) document parsing, chunking, embedding pipeline
├── chat/           (M3) SSE streaming chat
├── chatbot/        (M3) chatbot settings + widget config
├── knowledgebase/  (M2) KB CRUD
└── admin/          Super-admin cross-tenant endpoints

src/main/resources/
├── application.yml           shared config (JPA, actuator, JWT defaults)
├── application-dev.yml       local DB + Mailhog SMTP
├── application-prod.yml      Render PostgreSQL + prod SMTP
├── application-test.yml      Testcontainers overrides
└── db/migration/             Flyway V1–V12 SQL migrations
```

## Entry Point

`SupportSaasApplication.java` — standard `@SpringBootApplication`.

## Data Layer

- **Multi-tenancy:** row-level isolation via Hibernate `@FilterDef` on `TenantEntity`.
  Every query auto-appends `WHERE business_id = :tenantId`.
- **Async propagation:** `TenantContextCopyingDecorator` wraps the `taskExecutor` thread pool
  so `@Async("taskExecutor")` methods inherit the calling thread's tenant ID.
- **Migrations:** sequential Flyway scripts — `V{n}__{description}.sql`.
  Never use `ddl-auto: create` — always `validate`.

## Test Strategy

- Unit tests (`*Test.java`): JUnit 5, no Spring context.
- Integration tests (`*IT.java`): `@SpringBootTest` + Testcontainers PostgreSQL.
- `TenantIsolationIT` is the release gate — must pass 100%.
