# Research Summary: M1 Frontend — React Admin Dashboard

**Synthesized:** 2026-05-13
**Sources:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md
**Overall confidence:** HIGH

---

## Executive Summary

A React SPA admin dashboard consuming an existing Spring Boot REST API. Vite + React 18 + TypeScript + shadcn/ui + Tailwind is the de facto stack — all already mandated by the project's CLAUDE.md, eliminating stack risk. Recommended structure: `admin-ui/` at repo root, deployed as a Render static site independent from the Spring Boot service.

**Critical insight:** The 7-day hard cap demands ruthless scope control. The demo value lives in one 2-minute flow: login → dashboard home → member list. Everything outside that is a time trap. 8 identified time-wasting traps averaging 2–5 hours each (see PITFALLS.md). Knowledge base = static empty-state page only — no stub CRUD.

---

## Stack Additions

| Layer | Library | Version |
|-------|---------|---------|
| Build | Vite | 5.x |
| Routing | react-router-dom | 6.x |
| Components | shadcn/ui + lucide-react | latest |
| Server state | @tanstack/react-query | 5.x |
| HTTP client | axios | 1.x |
| Auth state | zustand | 4.x |
| Forms + validation | react-hook-form + zod + @hookform/resolvers | 7.x / 3.x |

Nothing else. No Next.js, no Redux, no Cypress, no Framer Motion.

---

## Feature Table Stakes

| Section | Must-Have Features |
|---------|-------------------|
| Auth | Login, signup, session persist on refresh, logout, protected routes, inline validation errors |
| Dashboard Home | Business name in header, user role display, sidebar nav, plan indicator, quick-action links |
| Members | Table with invite/remove/role-change, ADMIN-only gates, empty state, error toasts |
| KB | Static empty-state page, disabled "Add" button, nav link — nothing more |

**If time remains:** Forgot/reset password pages (backend exists, low effort), member status badge, email verification banner.

---

## Watch Out For

1. **CORS + OPTIONS** — test `POST /api/v1/auth/login` cross-origin on Day 1 before writing any auth code. PR #33 already fixed this but Render domain must still be added to `allowedOrigins`.
2. **Refresh race condition** — implement shared in-flight promise (refresh lock) in axios interceptor BEFORE building any page with multiple API calls on mount.
3. **Tri-state auth** — `'loading' | 'authenticated' | 'unauthenticated'` — never boolean. Build from the start.
4. **Refresh token storage** — access token in memory (Zustand), refresh token in localStorage (documented tradeoff for demo). Never localStorage for refresh token in production.
5. **SPA routing on Render** — add `/* → /index.html` rewrite rule in `render.yaml` or every hard refresh is a 404.

---

## Suggested Roadmap (7 Phases)

| # | Phase | Goal | Blocking? |
|---|-------|------|-----------|
| 1 | Scaffold + CORS Verification | Vite created, cross-origin login call confirmed | Yes — blocks all |
| 2 | Auth Core (Infrastructure) | axios instance + refresh lock + Zustand tri-state store | Yes — blocks pages |
| 3 | Auth Pages | Login, signup, session persist, logout with server revocation | Yes — blocks shell |
| 4 | App Shell + Dashboard Home | Sidebar layout, user info, plan indicator, hardcoded stats | No |
| 5 | Member Management | Table, invite modal, remove confirm, role change, status badge | No |
| 6 | KB Stub + Polish | Static KB page, forgot/reset password, show/hide password | No |
| 7 | Deploy + Smoke Test | Render static site live, full 2-min demo flow verified | Final gate |

---

## Spot-Checks Needed During Implementation

- **Phase 2:** Verify `/auth/refresh` request/response shape in `AuthController.java` before writing interceptor
- **Phase 5:** Confirm `status` field exists on `MemberResponse` record before building status badge
- **Phase 7:** Add Render frontend URL to `allowedOrigins` in `SecurityConfig.java` before deploy
