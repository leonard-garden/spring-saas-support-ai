# Domain Pitfalls: React Admin Dashboard + Spring Boot JWT

**Domain:** React SPA frontend connecting to Spring Boot JWT-authenticated backend
**Project:** spring-saas-support-ai (M1 Frontend — Admin Dashboard)
**Researched:** 2026-05-13
**Overall confidence:** HIGH

---

## Critical Pitfalls

### Pitfall 1: CORS Misconfiguration — Credentials Not Allowed

`allowedOrigins("*")` is incompatible with `allowCredentials = true`. Silent failure — error appears in browser console, not Spring logs.

**Prevention:** Explicit origin list + `allowedMethods` including OPTIONS.

**This project:** PR #33 fixed this. Regression check: verify Render frontend domain is in `allowedOrigins` before deploying.

---

### Pitfall 2: Spring Security Blocks OPTIONS Preflight

`JwtAuthFilter` runs before CORS filter. Browser preflights (OPTIONS) carry no Authorization header by spec → 401/403 returned → actual request never fires.

**Prevention:**
```java
.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
```

---

### Pitfall 3: Refresh Token Race Condition

Dashboard mounts and fires 3–4 parallel API calls. All get 401. Each independently calls `/auth/refresh`. With token rotation, first call succeeds, subsequent calls fail → random logouts.

**Prevention:** Shared in-flight promise (refresh lock) in axios interceptor — see `ARCHITECTURE.md` for implementation.

---

### Pitfall 4: Protected Routes Flash Authenticated Content

Boolean auth state (`true/false`) can't represent "still loading." Route guard fires before async token check resolves.

**Prevention:** Tri-state auth: `'loading' | 'authenticated' | 'unauthenticated'` — always show spinner on `loading`.

---

### Pitfall 5: Refresh Token in localStorage

7-day refresh token in localStorage = persistent session hijacking via XSS.

**For demo:** Store access token in memory (Zustand), refresh token in localStorage — documented tradeoff acceptable with 15-min access token TTL. Never store refresh token in localStorage in production.

---

## Moderate Pitfalls

### Pitfall 6: Hardcoded API Base URL

Use `VITE_API_BASE_URL` env var from day one. Do NOT hardcode `localhost:8080` anywhere.

### Pitfall 7: No Axios Interceptor

Auth headers copy-pasted across every fetch call. One axios instance + one interceptor is the correct pattern.

### Pitfall 8: Treating 401 and 403 the Same

- `401` → attempt refresh → redirect to login if refresh fails
- `403` → show toast "You don't have permission" → stay on page
- `429` → show "Rate limit reached, try again"

### Pitfall 9: Logout Does Not Revoke Server-Side Token

Always call `POST /api/v1/auth/logout` before clearing client state. Backend has revocation infrastructure — use it.

### Pitfall 10: SPA Routing Breaks on Hard Refresh (Render)

Add rewrite rule in `render.yaml`: `/* → /index.html`. Without this, direct URL access returns 404.

---

## Minor Pitfalls

- Missing `key` props in member list renders
- No loading/disabled state on form buttons (double-submit)

---

## Demo App Time-Wasting Traps

| Trap | Time Lost | Correct Call |
|------|-----------|--------------|
| Custom auth context from scratch | 4–6 hrs | `useState` + `useContext` in 20 lines or Zustand |
| Silent token refresh with full rotation | 3–5 hrs | One 401 interceptor with refresh lock is enough |
| TypeScript strict types for all API responses | 2–4 hrs | Type the 4 responses you use; `unknown` elsewhere |
| Dark mode | 2–4 hrs | Light mode only |
| Mobile responsive layout | 2–4 hrs | Admin dashboard = desktop-only is acceptable |
| Cypress / Playwright E2E | 3–6 hrs | Manual smoke test before demo |
| Redux Toolkit | 2–3 hrs | `useState` + Zustand for auth is correct |
| Animated page transitions | 1–2 hrs | Static renders look professional |

**Rule of thumb:** If a feature doesn't appear in the 2-minute demo flow (login → dashboard → member list), cut it from M1.

---

## Phase-Specific Warnings

| Phase | Pitfall | Mitigation |
|-------|---------|------------|
| First API call / CORS | Pitfalls 1 + 2 | Test `POST /api/v1/auth/login` cross-origin before writing auth code |
| Auth pages | Pitfall 5 (token storage) | Document localStorage tradeoff explicitly in README |
| Protected routes | Pitfall 4 (auth flash) | Tri-state auth from day one |
| Dashboard home | Pitfall 3 (refresh race) | Implement refresh lock before any page with multiple API calls |
| Member management | Pitfalls 8 + 9 | Differentiate 401 vs 403; wire server-side logout |
| Render deploy | Pitfalls 6 + 10 | `VITE_API_BASE_URL` + SPA rewrite rule |
