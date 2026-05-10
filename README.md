<div align="center">
  <h1>spring-saas-support-ai</h1>
  <p><strong>Multi-tenant AI customer support backend — train a chatbot on your docs, embed it in 5 minutes.</strong></p>

  <a href="https://spring-saas-support-ai.onrender.com/swagger-ui.html">Live API</a> &nbsp;·&nbsp;
  <a href="https://spring-saas-support-ai.onrender.com/swagger-ui.html">Swagger UI</a> &nbsp;·&nbsp;
  <a href="#roadmap">Roadmap</a>

  <br/><br/>

  [![CI](https://github.com/leonard-garden/spring-saas-support-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/leonard-garden/spring-saas-support-ai/actions/workflows/ci.yml)
  ![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
  ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
  [![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen)](CONTRIBUTING.md)
</div>

---

## What is this?

A production-grade multi-tenant SaaS backend built with Java 21 + Spring Boot 3.3. Each business gets a fully isolated support workspace — upload your documentation, train an AI chatbot on it, and embed the widget on your site. Claude handles Tier-1 support automatically.

**Current milestone:** M1 — multi-tenant auth foundation (✅ v0.1.0 shipped). RAG pipeline (M2) and streaming chat widget (M3) are next.

---

## Why this architecture?

**Tenant isolation you can't accidentally bypass.**
Row-level isolation via Hibernate filters means every query automatically scopes to the current tenant. No `WHERE business_id = ?` sprinkled through 40 service methods — one `@Filter` on the base entity handles all of it. `TenantIsolationIT` blocks any merge that breaks this contract.

**JWT that carries its own security context.**
Access tokens embed `tenant_id` + `role`, so every request is self-describing. No extra DB round-trip to resolve "who is this user and which business do they belong to?" — the filter activates from the token claim directly.

**Async without losing tenant identity.**
`@Async` threads run on a separate ThreadLocal scope — a common source of silent data leaks in multi-tenant systems. `TenantContextCopyingDecorator` captures the tenant ID before the thread hand-off and propagates it automatically to every background task.

---

## Quick Start

**Prerequisites:** Java 21, Maven 3.9+, Docker

```bash
git clone https://github.com/leonard-garden/spring-saas-support-ai
cd spring-saas-support-ai
docker-compose up -d                                     # postgres + adminer + mailhog
mvn spring-boot:run -Dspring-boot.run.profiles=dev
open http://localhost:8080/swagger-ui.html
```

Or hit the live API directly:

```bash
# 1. Create a business + owner account
curl -s -X POST https://spring-saas-support-ai.onrender.com/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"businessName":"Acme Corp","email":"admin@acme.com","password":"secret123"}' | jq

# 2. Login → get access + refresh tokens
curl -s -X POST https://spring-saas-support-ai.onrender.com/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"secret123"}' | jq

# 3. Invite a team member (requires Bearer token from step 2)
curl -s -X POST https://spring-saas-support-ai.onrender.com/api/v1/members/invite \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"colleague@acme.com","role":"AGENT"}' | jq
```

---

## API Reference (Milestone 1)

Full interactive docs: **[Swagger UI →](https://spring-saas-support-ai.onrender.com/swagger-ui.html)**

### Auth — `/api/v1/auth`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/signup` | Register business + owner account | Public |
| POST | `/login` | Get access + refresh tokens | Public |
| POST | `/refresh` | Rotate refresh token | Public |
| POST | `/logout` | Revoke refresh token | Bearer |
| POST | `/verify-email` | Confirm email address | Public |
| POST | `/forgot-password` | Send password reset link | Public |
| POST | `/reset-password` | Set new password via token | Public |

### Members — `/api/v1/members`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/` | List all members in tenant | Bearer |
| GET | `/me` | Current user profile | Bearer |
| GET | `/{id}` | Get member by ID | Bearer |
| POST | `/invite` | Send invitation email | Bearer (ADMIN) |
| POST | `/api/v1/invitations/accept` | Accept invitation | Public |
| PATCH | `/{id}/role` | Change member role | Bearer (ADMIN) |
| DELETE | `/{id}` | Remove member from tenant | Bearer (ADMIN) |

**Response envelope (all endpoints):**
```json
{ "success": true, "data": { ... }, "error": null }
```

**Error format (RFC 7807 Problem Details):**
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Member not found",
  "instance": "/api/v1/members/123"
}
```

---

## How Multi-Tenancy Works

```
HTTP Request arrives
        │
        ▼  JwtAuthFilter
        ├─ validate JWT
        ├─ TenantContext.set(tenantId)     ← ThreadLocal
        └─ filterChain.doFilter(...)
                   │
                   ▼  TenantFilterAspect (AOP)
                   ├─ session.enableFilter("tenantFilter", tenantId)
                   └─ Repository.findAll() / findById() / save()
                              │
                              ▼  Hibernate generates:
                              SELECT * FROM members WHERE business_id = 'abc'
                              │
                              ▼  JwtAuthFilter finally block
                              TenantContext.clear()  ← NEVER skipped — prevents ThreadLocal leak
```

**JWT payload:**
```json
{
  "sub": "user-uuid",
  "tenant_id": "tenant-uuid",
  "role": "ADMIN",
  "exp": 1234567890,
  "jti": "token-uuid"
}
```

Tokens: access = 15 min, refresh = 7 days (stored as hash in DB — revocable).

---

## Running Tests

```bash
mvn test              # unit tests only
mvn verify            # full suite including integration tests (requires Docker)

mvn test -Dtest=TenantIsolationIT    # run the critical isolation test in isolation
```

`TenantIsolationIT` verifies that tenant A cannot read tenant B's data under any condition — concurrent requests, async operations, edge cases. CI blocks merge if it fails.

No H2. All integration tests run against a real PostgreSQL instance via Testcontainers.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 — records, sealed interfaces, pattern matching |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 + PgVector (ready for M2 RAG) |
| Migrations | Flyway |
| Auth | JJWT — 15 min access + 7 day refresh tokens |
| Multi-tenancy | Row-level isolation via Hibernate filters |
| AI (M2+) | Spring AI + Claude (claude-3-5-sonnet) + OpenAI text-embedding-3-small |
| Async | `@Async` + `ThreadPoolTaskExecutor` + `TenantContextCopyingDecorator` |
| Testing | JUnit 5 + Testcontainers — real DB, no H2 |
| API Docs | springdoc-openapi (Swagger UI) |
| Container | Docker multi-stage build |
| CI/CD | GitHub Actions → Render |
| Hosting | Render (app) + Supabase (database) |

**Deliberately excluded:** Lombok (Java 21 records), Kafka (Spring `@Async` is sufficient), H2 (Testcontainers gives real DB behavior), microservices (monolith first).

---

## Self-Hosting

### Environment variables

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | 256-bit base64 string — generate with `openssl rand -base64 32` |
| `DATABASE_URL` | JDBC connection string |
| `DATABASE_USERNAME` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `APP_BASE_URL` | Your app's public URL (used in email links) |

### Infrastructure

- **App:** Render free web service (Docker runtime) — see [render.yaml](render.yaml)
- **Database:** Supabase free tier PostgreSQL (persistent, pgvector pre-installed for M2)
- **CI/CD:** GitHub Actions runs `mvn verify` on every push and PR

---

## Roadmap

| Milestone | Theme | Status |
|-----------|-------|--------|
| M1 | Multi-tenant auth + member management | ✅ v0.1.0 |
| M2 | Knowledge Base + RAG pipeline (PgVector + hybrid search) | Planned |
| M3 | AI chat + embeddable JS widget (SSE streaming) | Planned |
| M4 | Stripe billing + production hardening | Planned |

---

## Contributing

1. Fork the repo
2. Create a branch: `git checkout -b feature/your-feature`
3. Run the full test suite: `mvn verify`
4. Open a PR targeting `develop`

`TenantIsolationIT` must pass before merge. See the test for the invariants you need to preserve.

---

## License

MIT — see [LICENSE](LICENSE)
