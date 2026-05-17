# Milestones

## v0.1 — M1 Backend: Multi-tenant Foundation

**Status:** Shipped
**Date:** 2026-05-13

**Delivered:**
- Spring Boot 3.3 + Java 21 project setup
- Docker compose: postgres + adminer + mailhog
- Business + Member entities + Flyway migrations (V1–V12)
- TenantContext (ThreadLocal) + Hibernate filter
- JwtService with tenant_id claim
- Signup (creates Business + Owner), Login, Refresh, Logout
- Member invitation email flow + accept endpoint
- List/remove members, change role
- GlobalExceptionHandler + OpenAPI config
- Audit logging, billing entities (Plan, Subscription stubs)
- TenantIsolationIT integration test

**Phase count:** No GSD phases tracked (pre-GSD)

---

## v0.2 — M1 Frontend: Admin Dashboard

**Status:** In progress
**Started:** 2026-05-13

See ROADMAP.md for phases.
