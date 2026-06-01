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

A multi-tenant SaaS backend where each business gets an isolated support workspace. Upload your documentation, train a chatbot on it, embed the widget on your site — AI handles Tier-1 support.

- **Multi-tenant** — row-level isolation, each business sees only its own data
- **RAG pipeline** — hybrid vector + full-text search (PgVector + PostgreSQL FTS), Reciprocal Rank Fusion
- **Streaming AI chat** — SSE via Spring AI, Claude 3.5 Sonnet in production
- **Embeddable widget** — single `<script>` tag, vanilla JS, zero dependencies

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (records, sealed interfaces, pattern matching) |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 + PgVector |
| Migrations | Flyway |
| AI | Spring AI — Anthropic Claude (chat) + OpenAI (embeddings) |
| Auth | JJWT — access (15 min) + refresh (7 days) tokens |
| Multi-tenancy | Row-level isolation via Hibernate filters |
| Async | Spring `@Async` + `ThreadPoolTaskExecutor` |
| Object storage | MinIO (document files) |
| API Docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5 + Testcontainers (real DB, no H2) |
| Container | Docker multi-stage build |
| CI | GitHub Actions |
| Hosting | Render (app + frontend) |
| Frontend | React + Vite + shadcn/ui |

---

## Quick Start

```bash
# 1. Clone and start infrastructure
git clone https://github.com/leonard-garden/spring-saas-support-ai
cd spring-saas-support-ai
docker-compose up -d        # postgres + pgvector + MinIO + mailhog

# 2. Set required env vars
cp .env.example .env        # fill in OPENAI_API_KEY at minimum

# 3. Run the backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Run the frontend
cd frontend && npm install && npm run dev

# 5. Open the app
open http://localhost:3000
# Swagger UI: http://localhost:8081/swagger-ui.html
```

**Prerequisites:** Java 21, Maven 3.9+, Docker, Node 20+

---

## API Overview

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
| POST | `/invite` | Send invitation email | Bearer (ADMIN) |
| POST | `/api/v1/invitations/accept` | Accept invitation | Public |
| PATCH | `/{id}/role` | Change member role | Bearer (ADMIN) |
| DELETE | `/{id}` | Remove member | Bearer (ADMIN) |

### Knowledge Base — `/api/v1/kb`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/` | Get KB info + document counts | Bearer |
| GET | `/documents` | List documents | Bearer |
| POST | `/documents` | Upload document (PDF/TXT/MD) | Bearer |
| GET | `/documents/{id}` | Get document status | Bearer |
| DELETE | `/documents/{id}` | Delete document | Bearer (ADMIN) |
| POST | `/documents/search` | Hybrid search (vector + FTS) | Bearer |

### Chat — `/api/v1/chat`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/conversations` | List conversations (paginated) | Bearer |
| GET | `/conversations/{id}` | Get conversation with messages | Bearer |
| POST | `/conversations` | Create conversation | Bearer |
| POST | `/conversations/{id}/messages` | Stream AI response (SSE) | Bearer |

### Widget Admin — `/api/v1/chat/widget`

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/` | Get widget config | Bearer |
| POST | `/` | Create widget | Bearer (ADMIN) |
| PUT | `/{id}/config` | Update name/color/welcome message | Bearer (ADMIN) |
| PUT | `/{id}/knowledge-bases` | Assign KBs to widget | Bearer (ADMIN) |
| GET | `/{id}/embed` | Get embed snippet | Bearer |

### Public Widget — `/api/v1/widget` (no auth)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/{chatbotId}/config` | Get widget config for embed |
| POST | `/{chatbotId}/chat` | Stream AI chat response (SSE) |

---

## Embedding the Widget

Copy the embed snippet from the Chat Widget page in the dashboard, or construct it manually:

```html
<script
  src="https://spring-saas-support-ai.onrender.com/widget.js"
  data-widget-id="your-chatbot-uuid">
</script>
```

The widget renders a chat bubble in the bottom-right corner. No framework required.

---

## Architecture Highlights

### Multi-tenancy: Row-level isolation

Every business table carries a `business_id` UUID. Hibernate automatically appends `WHERE business_id = :tenantId` to all queries.

```
HTTP Request → JwtAuthFilter → TenantContext.set(tenantId)
                                      ↓
                              Repository.findAll()
                                      ↓
              Hibernate: SELECT * FROM members WHERE business_id = 'abc'
                                      ↓
              JwtAuthFilter finally → TenantContext.clear()
```

`@Async` methods use `TenantContextCopyingDecorator` to propagate tenant context across thread boundaries.

### RAG pipeline

```
Upload → MinIO storage → async chunking (500 tokens, 100 overlap)
       → SHA-256 dedup → OpenAI embeddings → PgVector store

Query  → parallel: vector search (top-10) + FTS (top-10)
       → Reciprocal Rank Fusion → top-5 chunks → Claude prompt
```

### Streaming chat (SSE)

Spring AI's reactive streaming sends tokens to the browser as they arrive. The public widget endpoint resolves tenant context from the chatbot record — never from user input.

---

## Running Tests

```bash
# Unit tests
mvn test

# All tests including integration tests (requires Docker for Testcontainers)
mvn verify
```

`TenantIsolationIT` is the critical test — verifies tenant A cannot read tenant B's data. CI blocks merge if this test fails.

---

## Deployment

### Environment variables (Render dashboard)

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | 256-bit base64 random string |
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | DB user |
| `DATABASE_PASSWORD` | DB password |
| `APP_BASE_URL` | Backend Render URL |
| `CORS_ALLOWED_ORIGINS` | Frontend Render URL |
| `OPENAI_API_KEY` | For embeddings (`text-embedding-3-small`) |
| `SPRING_AI_ANTHROPIC_API_KEY` | For chat (`claude-3-5-sonnet-20241022`) |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `MAIL_SENDER` | From address |

### Infrastructure

- **Backend:** Render web service (Docker runtime)
- **Frontend:** Render static site (Vite build)
- **Database:** PostgreSQL with PgVector extension
- **CI/CD:** GitHub Actions — runs `mvn verify` on every push and PR

---

## Roadmap

| Milestone | Theme | Status |
|-----------|-------|--------|
| M1 | Multi-tenant auth + member management | ✅ v0.1.0 |
| M2 | Knowledge Base + RAG pipeline (PgVector) | ✅ v0.2.0 |
| M3 | AI Chat + embeddable JS widget | ✅ v0.3.0 |
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
