# Phase 1: Scaffold + CORS Verification - Context

**Gathered:** 2026-05-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Create the `frontend/` React project at repo root with all stack libraries installed, and verify a cross-origin API call to the running Spring Boot backend succeeds via a dedicated `/cors-test` route. Nothing user-facing is built — this is the infrastructure gate that unblocks all other phases.

</domain>

<decisions>
## Implementation Decisions

### Project location + naming
- **D-01:** React project lives at `frontend/` (repo root, sibling to `src/` and `pom.xml`)
- **D-02:** NOT `admin-ui/` — use `frontend/` as the directory name

### Package manager
- **D-03:** Use `npm` — no extra tooling, works everywhere, consistent with Claude-generated examples

### CORS verification approach
- **D-04:** Create a dedicated `/cors-test` route (a proper page) with a form that calls `POST /api/v1/auth/login`
- **D-05:** Page shows the response (success or CORS error) inline — gives a clear repeatable pass/fail in git
- **D-06:** This page can be removed or kept behind a dev-only guard in Phase 2

### Stack (locked from research)
- **D-07:** Build: Vite 5.x + React 18 + TypeScript 5.x (strict mode)
- **D-08:** Routing: react-router-dom 7.x (v7 retains identical BrowserRouter+Routes API; current stable as of 2026-05-13)
- **D-09:** Components: shadcn/ui (Radix + Tailwind) + lucide-react
- **D-10:** Server state: @tanstack/react-query 5.x
- **D-11:** HTTP client: axios 1.x
- **D-12:** Auth state: zustand 5.x (v5 is current stable; `create()` API fully backward-compatible with v4)
- **D-13:** Forms: react-hook-form 7.x + zod 3.x + @hookform/resolvers 3.x
- **D-14:** Styling: Tailwind CSS 3.x

### Claude's Discretion
- Exact shadcn/ui init configuration (default theme, CSS variables, etc.)
- Whether to init the git-ignored `.env.development` with a placeholder or instructions
- Vite config details (port, proxy if any)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### CORS configuration (backend)
- `src/main/java/com/leonardtrinh/supportsaas/config/SecurityConfig.java` — CORS config reads `app.cors.allowed-origins` property; `allowCredentials=true`; OPTIONS allowed via CORS filter
- `src/main/resources/application-dev.yml` — Dev CORS origins already include `http://localhost:3000,http://localhost:5173` — Vite default port is pre-configured ✓
- `src/main/resources/application.yml` — Base config: `app.cors.allowed-origins: http://localhost:3000` (production override needed in Phase 7)

### Stack research
- `.planning/research/STACK.md` — Full library list with versions, installation sequence, project structure
- `.planning/research/PITFALLS.md` — CORS pitfalls 1+2, refresh race condition, time-wasting traps

### Requirements
- `.planning/REQUIREMENTS.md` — Full v0.2 requirement list (Phase 1 has no direct requirements — infra gate)
- `.planning/ROADMAP.md` §Phase 1 — Success criteria: admin-ui exists, all libs installed, login call returns 200, npm run dev starts

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None yet — this is the first frontend phase; `frontend/` does not exist

### Established Patterns
- Backend uses `ApiResponse<T>` envelope: `{ success: boolean, data: T, error: string }` — the cors-test page should display this shape
- Auth endpoint: `POST /api/v1/auth/login` accepts `{ email, password }`, returns `ApiResponse<{ accessToken, refreshToken, ... }>`
- Server runs on port 8081 in dev (per `application.yml`: `server.port: ${PORT:8081}`)

### Integration Points
- `frontend/` connects to `http://localhost:8081/api/v1` in dev
- `.env.development` should set `VITE_API_URL=http://localhost:8081/api/v1`
- CORS: `allowCredentials=true` is set on backend — axios must NOT send credentials by default (Bearer token is stateless, credentials flag only needed for cookies)

</code_context>

<specifics>
## Specific Ideas

- User prefers `frontend/` as directory name (not `admin-ui/`)
- CORS test should be a proper `/cors-test` page (route), not just a DevTools console paste
- The test page gives a repeatable, in-git verification artifact

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within Phase 1 scope.

</deferred>

---

*Phase: 01-scaffold-cors-verification*
*Context gathered: 2026-05-13*
