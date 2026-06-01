# ADR-0001: Initial Architecture Decisions

**Date:** 2026-05-28  
**Status:** Accepted  
**Milestone:** M1–M2 (foundation decisions, apply to all future work)

---

## Context

spring-saas-support-ai is a multi-tenant AI customer support SaaS built as a portfolio project
with two goals: demonstrate senior Java/Spring Boot + AI integration skills, and provide a path
to passive income. The architecture must be:

- Shippable in 7-day milestone cycles (hard deadlines)
- Deployable on free-tier hosting (Render)
- Demonstrable to recruiters without infrastructure complexity

---

## Decisions

### 1. Monolith over microservices

**Decision:** Single Spring Boot application for all domains.

**Rationale:** Microservices add operational complexity that delays the 7-day milestone cadence.
A modular monolith gives clean package boundaries (`auth/`, `tenant/`, `document/`, etc.)
with the option to extract services later if traffic data justifies it.

**Rejected:** Event-driven microservices — premature for MVP; adds Kafka/broker complexity.

---

### 2. Row-level multi-tenancy (shared DB, shared schema)

**Decision:** All tenant data in one schema with `business_id UUID` on every business table.
Hibernate `@FilterDef` auto-applies `WHERE business_id = :tenantId`.

**Rationale:** Schema-per-tenant and DB-per-tenant cost too much on free-tier Render.
Row-level is the industry standard at this scale and can handle millions of tenants.

**Rejected:** Schema-per-tenant — operational complexity, Flyway migrations across schemas.  
**Rejected:** DB-per-tenant — cost-prohibitive on free tier.

**Consequence:** `TenantContext` (ThreadLocal) is a critical path. Any leak causes cross-tenant
data exposure. `TenantIsolationIT` is a mandatory release gate.

---

### 3. @Async + ThreadPoolTaskExecutor over message queues

**Decision:** Document ingestion and email sending are processed via `@Async("processingExecutor")`
with a dedicated `ThreadPoolTaskExecutor`.

**Rationale:** Avoids Kafka/RabbitMQ infrastructure. Sufficient for MVP load (<100 concurrent
document uploads). Can be replaced with a queue if async queue depth becomes a problem.

**Rejected:** Kafka — too much infrastructure overhead for MVP.  
**Rejected:** RabbitMQ — same reason.

**Consequence:** All async methods MUST use `@Async("processingExecutor")` — the executor is
configured with `TenantContextCopyingDecorator`. Bare `@Async` loses tenant context.

---

### 4. PgVector in the same PostgreSQL instance

**Decision:** Vector embeddings stored in the same PostgreSQL database using the `pgvector`
extension. No separate vector database.

**Rationale:** Eliminates operational complexity and cost. pgvector with HNSW index handles
up to ~1M vectors efficiently — more than enough for MVP.

**Rejected:** Pinecone — cost + external dependency.  
**Rejected:** Weaviate / Qdrant — extra infrastructure to manage on free tier.

---

### 5. Hybrid search (vector + full-text + RRF)

**Decision:** RAG retrieval uses cosine similarity (pgvector) + PostgreSQL FTS in parallel,
fused with Reciprocal Rank Fusion.

**Rationale:** Vector-only search misses exact keyword matches. FTS-only misses semantic matches.
Hybrid with RRF consistently outperforms either alone with no additional infrastructure.

---

### 6. No Lombok

**Decision:** Java 21 records for DTOs; standard Java for entities and services.

**Rationale:** Java 21 records are cleaner than Lombok for DTOs. Lombok annotation processing
adds build complexity and IntelliJ compatibility issues. Records are immutable by default.

**Rejected:** Lombok — annotation processing overhead, IDE friction.

---

### 7. No H2 for tests

**Decision:** All integration tests use Testcontainers with `pgvector/pgvector:pg16`.

**Rationale:** H2 doesn't support pgvector or PostgreSQL-specific SQL constructs
(e.g. `CAST(? AS vector)`, `to_tsvector`). H2 tests pass while production breaks.

**Rejected:** H2 in-memory DB — diverges from production SQL dialect.

---

### 8. No virtual threads

**Decision:** `spring.threads.virtual.enabled=false`.

**Rationale:** Virtual threads use thread-local variables differently — they can be pinned
to carrier threads under certain conditions, causing ThreadLocal-based `TenantContext`
to behave unpredictably. Not safe until the tenant isolation model is redesigned.

**Consequence:** Never set `spring.threads.virtual.enabled=true` without a plan to replace
ThreadLocal with a scoped-value approach.

---

### 9. Native @Query for pgvector/tsvector operations

**Decision:** Use `@Modifying @Query(nativeQuery=true)` on `JpaRepository` for all
pgvector and tsvector SQL, instead of JdbcTemplate or a separate JDBC layer.

**Rationale:** Keeps the repository layer consistent (one abstraction: JpaRepository).
JdbcTemplate was explicitly banned to avoid a two-layer data access pattern.

**Rejected:** JdbcTemplate — banned project-wide.  
**Rejected:** Spring AI's VectorStore abstraction — insufficient control over RRF fusion logic.

---

## Consequences

- `TenantIsolationIT` is mandatory before every release
- All @Async must specify `"processingExecutor"`
- pgvector queries require native SQL in repositories
- Virtual threads must remain disabled
- Spotless (google-java-format) is planned but not yet configured

---

*Next ADR:* ADR-0002 will document M3 chat architecture decisions (Spring AI SSE streaming, widget embedding strategy).
