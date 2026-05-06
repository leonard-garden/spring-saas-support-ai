---
name: Project Context
description: Identity, goals, milestone status, and roadmap for spring-saas-support-ai
type: project
---

# Project Context

**Name:** spring-saas-support-ai
**Tagline:** Open-source AI customer support platform for SMBs — white-label chatbot, embeddable in 5 minutes.
**Repo:** `spring-saas-support-ai` (public, MIT)

## Why this project exists

Two goals in priority order:
1. **Job hunting (Primary):** Demonstrate senior Java backend + AI integration to recruiters
2. **Passive income (Bonus):** Monetize later via hosted SaaS, template sales, or consulting

**Why:** Position as "Senior Java/Spring Boot Developer with AI Integration expertise". Lower competition than pure Python AI roles.

## Current milestone

**Milestone 1: Multi-tenant Foundation** (Days 1–7)

Checklist:
- [ ] Spring Boot 3.3 + Java 21 project setup
- [ ] Docker compose: postgres + adminer + mailhog
- [ ] Business + Member entities + Flyway migrations
- [ ] TenantContext (ThreadLocal) + Hibernate filter
- [ ] JwtService with tenant_id claim
- [ ] Signup (creates Business + Owner), Login, Refresh, Logout
- [ ] Member invitation email flow + accept endpoint
- [ ] List/remove members, change role
- [ ] `TenantIsolationIT` integration test (CRITICAL)
- [ ] GlobalExceptionHandler + OpenAPI config
- [ ] Deploy to Render free tier
- [ ] README v0.1 + Release v0.1.0

**After M1:** Apply 5–10 jobs targeting "Multi-tenant Spring Boot", "SaaS architecture", "Spring Security"

## Roadmap summary

| Milestone | Theme | Target release |
|-----------|-------|----------------|
| M1 | Multi-tenant auth + member mgmt | Day 7 → v0.1.0 |
| M2 | Knowledge Base + RAG pipeline | Day 14 → v0.2.0 |
| M3 | AI Chat + embeddable widget | Day 21 → v0.3.0 |
| M4 | Billing + production hardening | Day 28 → v1.0.0 |

**Hard rule:** Ship on Day 7/14/21/28 no matter what. Skip features, never skip the deadline.
