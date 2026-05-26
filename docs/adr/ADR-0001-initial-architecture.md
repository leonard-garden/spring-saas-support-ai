# ADR-0001: Initial Architecture

**Date:** 2026-05-26
**Status:** Accepted

## Context

Building an open-source AI customer support platform for SMBs. The primary goal is a portfolio project demonstrating Senior Java + AI Integration skills; the secondary goal is passive income via hosted SaaS. Time constraint: ship each milestone in 7 days. These constraints shape every architectural decision toward simplicity and speed over scalability.

## Options considered

### Multi-tenancy model
1. **Row-level isolation (shared DB, shared schema)** — `business_id` on every table, Hibernate filter
2. **Schema-per-tenant** — separate Postgres schema per business
3. **Database-per-tenant** — separate Postgres instance per business

### Async processing
1. **Spring `@Async` + ThreadPoolTaskExecutor** — built-in, no extra infra
2. **Kafka** — distributed event streaming
3. **RabbitMQ** — message broker

### Vector store
1. **PgVector (same Postgres DB)** — extension in existing DB
2. **Pinecone** — managed vector DB
3. **Weaviate** — self-hosted vector DB

### Architecture style
1. **Modular monolith** — domain packages, slice architecture per package
2. **Microservices** — separate services per domain
3. **Layered monolith** — traditional layer-by-layer separation

### ORM / Data access
1. **Spring Data JPA + Hibernate** — Hibernate `@Filter` for tenant isolation
2. **jOOQ** — type-safe SQL
3. **JdbcTemplate** — raw JDBC

## Decision

**Row-level isolation** — cheapest to run, standard for this scale, supports 14-day trial + free tier without per-tenant infra overhead.

**Spring `@Async`** — sufficient for document ingestion, email, and webhooks without introducing broker infrastructure.

**PgVector** — one DB to manage, zero extra hosting cost, sufficient for MVP RAG workloads.

**Modular monolith** — fastest to build, easiest to demo, domain packages map cleanly to job application talking points. Split into microservices only if data proves the need post-v1.

**Spring Data JPA + Hibernate** — Hibernate `@Filter` is the cleanest multi-tenant read isolation mechanism available in the stack; pairs naturally with Spring Data repositories.

## Consequences

**Positive:**
- Single deployment unit, single DB — trivial to host on Render/Railway free tier
- Full-text + vector search in one query engine (Postgres + PgVector)
- No broker cold-start in CI/CD
- Every milestone shippable in 7 days

**Negative / trade-offs:**
- Row-level isolation requires disciplined `TenantContext` management — one missed `clear()` = data leak
- Async processing is not durable — if the process crashes mid-ingestion, the task is lost (acceptable at MVP scale)
- Modular monolith cannot scale individual domains independently (addressed post-v1 if needed)
- PgVector performance degrades past ~1M vectors — acceptable until Pro tier scales up

**Constraints this creates:**
- `spring.threads.virtual.enabled=false` — virtual threads break `ThreadLocal` scoping for `TenantContext`
- All `@Async` methods MUST use `@Async("taskExecutor")` — bare `@Async` bypasses `TenantContextCopyingDecorator`
- `disableFilter("tenantFilter")` is ONLY allowed in `Admin*` repository classes
- No JdbcTemplate in production — use `@Modifying @Query(nativeQuery=true)` on JPA repositories even for pgvector/tsvector casts
- Flyway migrations are append-only — never modify an applied migration file
