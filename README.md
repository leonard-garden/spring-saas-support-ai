# spring-saas-support-ai

> Open-source AI customer support platform for SMBs — white-label chatbot, embeddable in 5 minutes.

[![CI](https://github.com/leonard-garden/spring-saas-support-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/leonard-garden/spring-saas-support-ai/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Live demo:** https://spring-saas-support-ai.onrender.com  
**Swagger UI:** https://spring-saas-support-ai.onrender.com/swagger-ui.html

---

## What is this?

A multi-tenant SaaS backend where each business gets an isolated support workspace. Train a chatbot on your documentation, embed the widget on your site, let AI handle Tier-1 support.

This repository is Milestone 1 — the multi-tenant auth and member management foundation. RAG pipeline and chat widget ship in M2/M3.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (records, sealed interfaces, pattern matching) |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 + PgVector |
| Migrations | Flyway |
| Auth | JJWT — access (15 min) + refresh (7 days) tokens |
| Multi-tenancy | Row-level isolation via Hibernate filters |
| Async | Spring `@Async` + `ThreadPoolTaskExecutor` |
| API Docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5 + Testcontainers (real DB, no H2) |
| Container | Docker multi-stage build |
| CI | GitHub Actions |
| Hosting | Render (app) + Supabase (database) |

---

## Quick Start

```bash
# 1. Clone and start infrastructure
git clone https://github.com/leonard-garden/spring-saas-support-ai
cd spring-saas-support-ai
docker-compose up -d

# 2. Run the app
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

**Prerequisites:** Java 21, Maven 3.9+, Docker

---

## API Overview (Milestone 1)

### Auth — `/api/v1/auth`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/signup` | Register business + owner account | Public |
| POST | `/login` | Login, returns access + refresh tokens | Public |
| POST | `/refresh` | Rotate refresh token | Public |
| POST | `/logout` | Revoke refresh token | Bearer |
| POST | `/verify-email` | Confirm email address | Public |
| POST | `/forgot-password` | Send reset email | Public |
| POST | `/reset-password` | Set new password via token | Public |

### Members — `/api/v1/members`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/` | List members in tenant | Bearer |
| GET | `/me` | Current user profile | Bearer |
| GET | `/{id}` | Get member by ID | Bearer |
| POST | `/invite` | Send invitation email | Bearer (ADMIN) |
| POST | `/api/v1/invitations/accept` | Accept invitation | Public |
| PATCH | `/{id}/role` | Change member role | Bearer (ADMIN) |
| DELETE | `/{id}` | Remove member | Bearer (ADMIN) |

---

## Architecture Highlights

### Multi-tenancy: Row-level isolation

Every business table carries a `business_id` UUID. Hibernate automatically appends `WHERE business_id = :tenantId` to all queries — no manual filtering in service code.

```
HTTP Request → JwtAuthFilter → TenantContext.set(tenantId)
                                      ↓
                              Repository.findAll()
                                      ↓
              Hibernate: SELECT * FROM members WHERE business_id = 'abc'
                                      ↓
              JwtAuthFilter finally → TenantContext.clear()
```

### JWT structure

```json
{ "sub": "user-uuid", "tenant_id": "tenant-uuid", "role": "ADMIN", "exp": 1234567890, "jti": "token-uuid" }
```

### Error responses (RFC 7807 Problem Details)

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

## Running Tests

```bash
# Unit tests
mvn test

# All tests including TenantIsolationIT (requires Docker for Testcontainers)
mvn verify
```

`TenantIsolationIT` is the critical test — it verifies that tenant A cannot read tenant B's data under any condition. CI blocks merge if this test fails.

---

## Deployment

### Environment variables (Render dashboard)

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | 256-bit base64 random string |
| `DATABASE_URL` | Supabase JDBC URL |
| `DATABASE_USERNAME` | Supabase DB user |
| `DATABASE_PASSWORD` | Supabase DB password |
| `APP_BASE_URL` | Your Render app URL |

### Infrastructure

- **App:** Render free web service (Docker runtime)
- **Database:** Supabase free tier PostgreSQL (persistent, pgvector ready for M2)
- **CI/CD:** GitHub Actions — runs `mvn verify` on every push and PR

---

## Roadmap

| Milestone | Theme | Status |
|-----------|-------|--------|
| M1 | Multi-tenant auth + member management | ✅ v0.1.0 |
| M2 | Knowledge Base + RAG pipeline (PgVector) | Planned |
| M3 | AI Chat + embeddable JS widget | Planned |
| M4 | Billing (Stripe) + production hardening | Planned |

---

## Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Run tests: `mvn verify`
4. Open a PR targeting `develop`

Please ensure `TenantIsolationIT` passes before submitting.

---

## License

MIT — see [LICENSE](LICENSE)
