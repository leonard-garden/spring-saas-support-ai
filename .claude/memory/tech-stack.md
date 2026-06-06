---
name: Tech Stack
description: Full technology stack decisions for spring-saas-support-ai
type: project
---

# Tech Stack

| Layer | Technology |
|-------|-----------|
| Java | 21 (LTS, records, sealed interfaces, pattern matching) |
| Framework | Spring Boot 3.3.x |
| Build | Maven |
| Database | PostgreSQL 16 |
| Vector store | PgVector extension (same DB — no extra infra) |
| Migrations | Flyway |
| AI framework | Spring AI 1.1+ |
| Primary LLM | Anthropic Claude (claude-3-5-sonnet / claude-3-haiku) |
| Embeddings | OpenAI text-embedding-3-small |
| Auth library | JJWT (JWT) |
| Payment | Stripe Java SDK |
| Email | Resend (or SMTP fallback) |
| Async | Spring @Async + ThreadPoolTaskExecutor |
| Caching | Redis (only when needed for rate limiting) |
| Testing | JUnit 5 + Testcontainers (real DB, no H2) |
| API docs | springdoc-openapi (Swagger UI) |
| Container | Docker multi-stage build |
| CI/CD | GitHub Actions |
| Hosting | Render / Railway (free tier) |
| Admin frontend | Minimal React (Claude-generated, demo only) |
| Widget | Vanilla JS, no framework, <50KB, single file |
| Observability | Micrometer + Prometheus |

## Key dependencies in pom.xml

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- `spring-ai-starter-model-openai` (chat + embeddings via OpenAI-compatible API)
- `spring-ai-pgvector-store-spring-boot-starter`
- `postgresql`
- `flyway-core`
- `jjwt-api` + `jjwt-impl` + `jjwt-jackson`
- `stripe-java`
- `testcontainers` + `testcontainers-postgresql`
- `springdoc-openapi-starter-webmvc-ui`

## What was explicitly rejected (with reason)

- **Lombok** (data annotations: `@Data`, `@Builder`, `@RequiredArgsConstructor`) → Java 21 records are cleaner. `@Slf4j` is allowed as an exception for logging.
- **Kafka/RabbitMQ** → `@Async` sufficient until proven otherwise
- **Microservices** → monolith first, split if data proves need
- **ElasticSearch** → PgVector + PG full-text is enough
- **Multiple databases** → one Postgres for everything
- **H2 for tests** → Testcontainers gives real DB behavior
