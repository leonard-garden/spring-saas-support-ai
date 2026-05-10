# CLAUDE.md — spring-saas-support-ai

> Open-source AI Customer Support Platform for SMBs.
> White-label chatbot trained on your docs, embeddable in 5 minutes.
> Primary goal: portfolio/job hunting. Secondary: passive income.

@.claude/memory/project-context.md
@.claude/memory/architecture.md
@.claude/memory/tech-stack.md
@.claude/memory/constraints.md

---

## Package Structure

```
com.leonardtrinh.supportsaas
├── auth/           # JWT, signup, login, refresh, password reset
├── tenant/         # TenantContext, Hibernate filter, Business entity
├── member/         # Member, Invitation, role management
├── knowledgebase/  # KB CRUD
├── document/       # Upload, parse, chunk, embed pipeline
├── chatbot/        # Chatbot settings, Widget config
├── chat/           # Streaming SSE, conversation, message
├── billing/        # Stripe, Subscription, Plan, UsageRecord
├── admin/          # Super-admin endpoints
├── common/         # Base entities, API response envelope, exception handler
└── config/         # Security, async executor, OpenAPI, CORS
```

Each package follows slice architecture: `Controller → Service → Repository → Entity`.

---

## Coding Rules

### Java 21 specifics
- Use **records** for DTOs and value objects — never Lombok
- Use **sealed interfaces** for discriminated unions (e.g. document status results)
- Use **pattern matching** (`instanceof`, switch expressions) where idiomatic
- Use **virtual threads** (`Executors.newVirtualThreadPerTaskExecutor`) for async if Spring Boot 3.3+ supports it

### Immutability
- DTOs are records (inherently immutable)
- Never mutate entity state outside of service layer
- Builder pattern or `with`-style copy for updates

### Error handling
- Throw typed exceptions: `TenantNotFoundException`, `QuotaExceededException`, `DocumentProcessingException`
- `GlobalExceptionHandler` maps them to `ProblemDetail` (RFC 7807)
- Never return `null` — use `Optional<T>` or throw

### API response envelope
```java
record ApiResponse<T>(boolean success, T data, String error) {
    static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null); }
    static <T> ApiResponse<T> fail(String error) { return new ApiResponse<>(false, null, error); }
}
```

---

## Multi-Tenancy — CRITICAL SAFETY RULES

1. **Every business table MUST have `tenant_id` column** — enforced by Hibernate filter
2. **TenantContext is ThreadLocal** — ALWAYS clear in `finally` block
3. **Async methods MUST propagate tenant** via `TenantContextCopyingDecorator`
4. **Never bypass the Hibernate filter** — no raw JPQL without tenant check
5. **Write tenant isolation integration test before any other test**

```java
// ALWAYS this pattern in filters:
try {
    TenantContext.setTenantId(tenantId);
    filterChain.doFilter(request, response);
} finally {
    TenantContext.clear();  // NEVER skip this
}
```

---

## Testing Rules

- **Minimum 60% coverage** on service layer
- **100% coverage** on tenant isolation paths
- Use **Testcontainers** — no H2, no mocked DB for integration tests
- Test class naming: `{ClassName}Test` (unit), `{ClassName}IT` (integration)
- Tenant isolation test: `TenantIsolationIT` — must pass before any release

### Test structure
```java
@SpringBootTest
@Testcontainers
class SomeServiceIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    // ...
}
```

---

## Current Milestone

**Milestone 1: Multi-tenant Foundation**
Days 1–7. See `.claude/memory/project-context.md` for full checklist.

After Milestone 1 completes: tag `v0.1.0`, update README, deploy to Render.

---

## Commands

```bash
# Build
mvn clean package -DskipTests

# Test
mvn test
mvn verify  # includes integration tests

# Run locally
docker-compose up -d          # start postgres + adminer + mailhog
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Database
mvn flyway:migrate
mvn flyway:info

# Single test class
mvn test -Dtest=TenantIsolationIT
```

---

## What NOT to do

- ❌ No Lombok — use Java 21 records/modern syntax
- ❌ No Kafka/RabbitMQ — use `@Async` + `ThreadPoolTaskExecutor`
- ❌ No microservices — monolith first
- ❌ No ElasticSearch — PgVector + PG full-text is enough
- ❌ No multiple databases — one PostgreSQL for everything
- ❌ No H2 for tests — Testcontainers only
- ❌ No scope creep (mobile app, voice, multi-language UI, multiple LLMs)
- ❌ No bypassing Hibernate tenant filter
