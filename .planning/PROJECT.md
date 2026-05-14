# spring-saas-support-ai

## What This Is

Open-source AI customer support platform for SMBs. A white-label chatbot that businesses can train on their own documentation and embed into their websites in under 5 minutes. Built with Spring Boot 3.3 + Java 21 on the backend.

Primary goal: portfolio/job hunting (demonstrate senior Java backend + AI integration). Secondary: passive income via hosted SaaS.

## Core Value

A business can sign up, invite their team, and deploy a trained AI chatbot on their site — all without writing a line of code.

## Requirements

### Validated

- ✓ Multi-tenant auth (signup creates Business + Owner, JWT with tenant_id) — M1 backend
- ✓ Login, refresh, logout, forgot/reset password, email verification — M1 backend
- ✓ Member management: list, get, remove, update role — M1 backend
- ✓ Member invitation email flow + accept endpoint — M1 backend
- ✓ TenantContext (ThreadLocal) + Hibernate filter — M1 backend
- ✓ GlobalExceptionHandler + OpenAPI/Swagger — M1 backend
- ✓ Audit logging, billing entities (Plan, Subscription stubs) — M1 backend

### Active

<!-- Current milestone scope. -->

- [ ] Admin dashboard React frontend: auth pages (login/signup)
- [ ] Admin dashboard: tenant overview / home page
- [ ] Admin dashboard: member management UI
- [ ] Admin dashboard: knowledge base list (stub, links to M2)

### Out of Scope

- Mobile app / native SDK — timeline, M4+ only
- Voice interface — not core to support chatbot value
- Multi-language UI — English only for MVP
- Advanced analytics dashboard — defer to M4
- Multiple LLM providers — Claude only until v1.0
- Self-hosted LLM — not target market

## Context

- Backend stack: Java 21, Spring Boot 3.3, PostgreSQL, Flyway, JJWT, Spring Security
- Frontend: React (minimal, Claude-generated, demo-only per CLAUDE.md)
- Available API endpoints: `/api/v1/auth/**`, `/api/v1/members/**`, `/api/v1/members/invite`, `/api/v1/invitations/accept`
- M2 will add Knowledge Base + RAG pipeline (Spring AI + PgVector)
- M3 will add AI Chat + embeddable widget
- M4 will add Billing (Stripe) + production hardening
- The knowledgebase and document packages exist as stubs only — no backend implementation yet

## Constraints

- **Tech stack**: React frontend, no framework-heavy choices — must be lightweight and demo-ready
- **Timeline**: 7-day hard cap per milestone — ship on time, skip features if needed
- **Scope**: Monolith backend, single PostgreSQL — no microservices
- **Quality**: 60%+ service layer coverage, 100% tenant isolation coverage
- **Deploy**: Render free tier

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Row-level multi-tenancy (shared schema) | Cost — schema-per-tenant too expensive for MVP | ✓ Good |
| Java 21 records instead of Lombok | Cleaner modern syntax | ✓ Good |
| JJWT for auth (no OAuth) | Email/password sufficient for v1 | — Pending |
| @Async + ThreadPoolTaskExecutor instead of Kafka | Sufficient for current scale | — Pending |
| Spring AI + PgVector instead of ElasticSearch | Same DB — no extra infra | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-13 after M1 backend completion — starting M1 frontend milestone*
