# Phase 2: Auth Infrastructure - Research

**Researched:** 2026-05-14
**Domain:** React auth state management — axios interceptors, Zustand store, React Router protected routes
**Confidence:** HIGH

---

## Summary

Phase 2 builds the invisible plumbing that every subsequent phase depends on: a token-aware axios instance, a tri-state auth store, and route guards. The backend contract is fully known — all request/response shapes are confirmed by reading the Java source directly. No guesswork required.

The core challenge is the **refresh race condition**: when a tab mounts with an expired access token, multiple concurrent API calls will each try to refresh simultaneously. Without a lock, the backend issues multiple new refresh tokens and invalidates the old one mid-flight, causing cascading 401s and logout. The standard solution is a promise-based refresh lock — one in-flight refresh, all other callers queue on the same promise.

The second challenge is **session hydration on refresh**: the access token lives in Zustand (memory), which resets on page reload. The refresh token in localStorage survives. On app mount, before rendering any route, the app must silently call `/auth/refresh` and set auth state. Until that call resolves, routes must show a loading state — not redirect to login.

**Primary recommendation:** Implement the refresh lock in `api.ts` (axios interceptor), the tri-state in a Zustand store (`useAuthStore`), and the route guards as thin React Router wrapper components that read from that store.

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-03 | Unauthenticated users are redirected to login page | ProtectedRoute component wrapping React Router — reads `status` from Zustand store, redirects when `'unauthenticated'` |
| AUTH-04 | Authenticated users are redirected away from login/signup | GuestRoute component — redirects when `'authenticated'` |
| AUTH-05 | User session persists across browser refresh (refresh token flow) | `useAuthInit` hook called in App.tsx — calls `/auth/refresh` on mount before route render, uses localStorage for refresh token |
</phase_requirements>

---

## Standard Stack

### Core (already installed — no new installs needed)

| Library | Installed Version | Purpose | Why Standard |
|---------|------------------|---------|--------------|
| axios | ^1.16.0 | HTTP client + interceptors | Already used in `api.ts`; interceptors are the correct hook point for token injection and 401 handling |
| zustand | ^5.0.13 | Auth state store | Already in stack; v5 uses `create` with direct TypeScript inference, no need for `immer` middleware for this simple shape |
| react-router-dom | ^7.15.0 | Route guards via `<Navigate>` | v7 API matches v6 for `Navigate` and `useLocation` — existing patterns apply |
| @tanstack/react-query | ^5.100.10 | Available if needed for `useQuery` on `/auth/me` | Already installed; not required for Phase 2 (Zustand handles auth state directly) |

**Installation:** No new packages required. All dependencies are installed.

---

## Backend API Contract (verified from Java source)

### POST /api/v1/auth/refresh
**Request body:**
```json
{ "refreshToken": "string" }
```
**Response — `ApiResponse<AuthResponse>`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "businessId": "uuid",
    "memberId": "uuid"
  },
  "error": null
}
```
**Error:** HTTP 400 with `{ "success": false, "data": null, "error": "..." }` when token is invalid/expired.

### GET /api/v1/auth/me
**Request:** `Authorization: Bearer <accessToken>` header
**Response — `ApiResponse<MeResponse>`:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "email": "string",
    "role": "OWNER | ADMIN | AGENT",
    "businessId": "uuid",
    "businessName": "string",
    "emailVerified": true
  },
  "error": null
}
```

### Storage contract (from STATE.md decision):
- **Access token:** in-memory (Zustand) — cleared on page reload
- **Refresh token:** `localStorage` — survives page reload, enables session restoration

---

## Architecture Patterns

### Recommended File Structure
```
frontend/src/
├── lib/
│   └── api.ts              # axios instance + interceptors (extend existing file)
├── store/
│   └── authStore.ts        # Zustand tri-state auth store
├── hooks/
│   └── useAuthInit.ts      # session hydration on mount
└── components/
    └── auth/
        ├── ProtectedRoute.tsx   # blocks unauthenticated
        └── GuestRoute.tsx       # blocks authenticated
```

### Pattern 1: Zustand Tri-State Auth Store

**What:** Three-value `status` field — `'loading' | 'authenticated' | 'unauthenticated'` — prevents the flash-of-wrong-route during hydration.

**When to use:** Always. Binary authenticated/not is insufficient because on page reload there is a moment where the state is genuinely unknown (waiting for refresh call).

```typescript
// store/authStore.ts
import { create } from 'zustand'

interface AuthUser {
  id: string
  email: string
  role: 'OWNER' | 'ADMIN' | 'AGENT'
  businessId: string
  businessName: string
  emailVerified: boolean
}

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthState {
  status: AuthStatus
  user: AuthUser | null
  accessToken: string | null
  setAuth: (token: string, user: AuthUser) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'loading',       // starts loading — hydration happens before route render
  user: null,
  accessToken: null,
  setAuth: (token, user) => set({ status: 'authenticated', accessToken: token, user }),
  clearAuth: () => set({ status: 'unauthenticated', accessToken: null, user: null }),
}))
```

### Pattern 2: Refresh Lock in Axios Interceptor

**What:** A module-level promise that is set when a refresh is in flight. Concurrent 401 responses queue on this promise instead of each calling `/auth/refresh`.

**When to use:** In the axios response interceptor. Critical for any app that fires multiple API calls on mount.

```typescript
// lib/api.ts — additions to the existing file

const REFRESH_TOKEN_KEY = 'refreshToken'

// Module-level lock — one refresh in flight at a time
let refreshPromise: Promise<string> | null = null

async function doRefresh(): Promise<string> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY)
  if (!refreshToken) throw new Error('No refresh token')

  const res = await axios.post<ApiResponse<AuthResponse>>(
    `${import.meta.env.VITE_API_URL}/api/v1/auth/refresh`,
    { refreshToken }
  )
  // Use raw axios.post here (not the api instance) to avoid interceptor loop
  const { accessToken, refreshToken: newRefreshToken } = res.data.data
  localStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken)
  return accessToken
}

// Response interceptor
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error)
    }
    originalRequest._retry = true

    try {
      if (!refreshPromise) {
        refreshPromise = doRefresh().finally(() => { refreshPromise = null })
      }
      const newAccessToken = await refreshPromise
      // Update store
      useAuthStore.getState().setAuth(newAccessToken, useAuthStore.getState().user!)
      originalRequest.headers['Authorization'] = `Bearer ${newAccessToken}`
      return api(originalRequest)
    } catch {
      useAuthStore.getState().clearAuth()
      return Promise.reject(error)
    }
  }
)

// Request interceptor — inject access token
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})
```

**Critical detail:** Use a plain `axios.post` call (not the `api` instance) inside `doRefresh` to avoid the interceptor calling itself in an infinite loop on refresh failure.

### Pattern 3: Session Hydration Hook

**What:** Called once in `App.tsx` before any routes render. Reads localStorage refresh token, calls `/auth/refresh`, sets Zustand state. If no token or refresh fails, sets `'unauthenticated'`.

```typescript
// hooks/useAuthInit.ts
import { useEffect } from 'react'
import axios from 'axios'
import { useAuthStore } from '@/store/authStore'

export function useAuthInit() {
  const { setAuth, clearAuth } = useAuthStore()

  useEffect(() => {
    const refreshToken = localStorage.getItem('refreshToken')
    if (!refreshToken) {
      clearAuth()
      return
    }

    axios
      .post(`${import.meta.env.VITE_API_URL}/api/v1/auth/refresh`, { refreshToken })
      .then((res) => {
        const { accessToken, refreshToken: newToken } = res.data.data
        localStorage.setItem('refreshToken', newToken)
        // Fetch /me to populate user fields
        return axios
          .get(`${import.meta.env.VITE_API_URL}/api/v1/auth/me`, {
            headers: { Authorization: `Bearer ${accessToken}` },
          })
          .then((meRes) => setAuth(accessToken, meRes.data.data))
      })
      .catch(() => {
        localStorage.removeItem('refreshToken')
        clearAuth()
      })
  }, [])  // eslint-disable-line react-hooks/exhaustive-deps
}
```

### Pattern 4: Route Guards

**What:** Thin wrapper components that read `status` and either render children, redirect, or show a loader.

```typescript
// components/auth/ProtectedRoute.tsx
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { status } = useAuthStore()
  const location = useLocation()

  if (status === 'loading') return <div>Loading...</div>   // or a spinner
  if (status === 'unauthenticated') {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

// components/auth/GuestRoute.tsx
export function GuestRoute({ children }: { children: React.ReactNode }) {
  const { status } = useAuthStore()

  if (status === 'loading') return <div>Loading...</div>
  if (status === 'authenticated') {
    return <Navigate to="/dashboard" replace />
  }
  return <>{children}</>
}
```

### Pattern 5: App.tsx wiring

**What:** Call `useAuthInit` at the top of the App component. Update the wildcard route. Wrap future routes correctly.

```typescript
// App.tsx (updated shape)
export function App() {
  useAuthInit()   // fires once, resolves 'loading' state

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          {/* Guest-only routes (Phase 3) */}
          <Route path="/login" element={<GuestRoute><LoginPage /></GuestRoute>} />
          <Route path="/signup" element={<GuestRoute><SignupPage /></GuestRoute>} />

          {/* Protected routes (Phase 3+) */}
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />

          {/* Wildcard — replace the Phase 1 cors-test redirect */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
```

Note: Phase 2 does not build the actual page components (Login, Dashboard) — those are Phase 3. Phase 2 adds the infrastructure stubs so guards work; placeholders are sufficient.

### Anti-Patterns to Avoid

- **Calling the `api` instance inside `doRefresh`:** Causes the response interceptor to fire on refresh failure → infinite loop → stack overflow.
- **Binary auth state (boolean `isAuthenticated`):** Causes a redirect flash on page reload before hydration completes. The `'loading'` state prevents this.
- **Storing the access token in localStorage:** Documented decision in STATE.md: access token is memory-only (Zustand). localStorage is only for the refresh token.
- **Multiple `useEffect` hydration calls:** `useAuthInit` must run exactly once at app root. Do not call it in individual route components.
- **Reading `accessToken` from Zustand inside interceptors via a hook:** Hooks cannot be called outside React components. Use `useAuthStore.getState().accessToken` (Zustand's imperative getter) instead.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token injection on every request | Manual `Authorization` header in each `api.*` call | axios request interceptor | Single place to maintain; automatically applies to all calls |
| Session restoration on reload | Custom `sessionStorage` + event listeners | `localStorage` + `useAuthInit` hook on mount | Simple, reliable, no framework needed |
| Concurrent refresh deduplication | Debounce, setTimeout, flags | Promise-based lock (`refreshPromise`) | Promises already queue correctly; no timing guesswork |
| Route protection | `if (!user) window.location.href = '/login'` | React Router `<Navigate>` in wrapper component | Preserves history, works with SPA routing, composable |

---

## Common Pitfalls

### Pitfall 1: Interceptor Loop on Refresh Failure
**What goes wrong:** The 401 interceptor calls the `api` instance to do the refresh. If the refresh endpoint returns 400/401, the interceptor fires again on that response, causing infinite recursion.
**Why it happens:** The axios instance is used for its own refresh call.
**How to avoid:** Use a raw `axios.post` (imported directly from `axios`) inside `doRefresh` — not the `api` instance.
**Warning signs:** Browser console shows rapid repeated network calls to `/auth/refresh`.

### Pitfall 2: Loading Flash / Wrong Redirect
**What goes wrong:** On page reload, `status` starts as `'unauthenticated'` (or boolean `false`), so `ProtectedRoute` immediately redirects to `/login` before the refresh call can complete.
**Why it happens:** Binary auth state has no way to represent "we don't know yet".
**How to avoid:** Initialize Zustand `status` as `'loading'`. Route guards render a spinner when `status === 'loading'`. Only redirect after the status is resolved.
**Warning signs:** Brief redirect to `/login` then back to dashboard on every page refresh.

### Pitfall 3: Zustand Accessor in Interceptor
**What goes wrong:** `const { accessToken } = useAuthStore()` throws "Invalid hook call" when called outside a React component.
**Why it happens:** Zustand hooks use React's hook rules. Interceptors are plain functions, not components.
**How to avoid:** Use `useAuthStore.getState().accessToken` in interceptors — this is Zustand's imperative API for outside-React access.
**Warning signs:** "Invalid hook call" error in the browser console on first API request.

### Pitfall 4: Stale Closure on `setAuth` after Refresh
**What goes wrong:** After the refresh succeeds and a new access token is stored, the retried original request still sends the old (expired) token.
**Why it happens:** `originalRequest.headers['Authorization']` was set before the refresh, not after.
**How to avoid:** Explicitly reassign `originalRequest.headers['Authorization'] = \`Bearer \${newAccessToken}\`` after `refreshPromise` resolves, before calling `api(originalRequest)`.
**Warning signs:** Retried requests after refresh still get 401.

### Pitfall 5: react-router-dom v7 vs v6 Naming
**What goes wrong:** v7 was released late 2024 and has minor API differences. Most guides are written for v6.
**Why it happens:** The installed version is `^7.15.0`.
**How to avoid:** `<Navigate>`, `useLocation`, `<Routes>`, `<Route>` all work identically to v6 for these use cases. No changes needed. The breaking changes in v7 affect data router features (loaders/actions) which this phase does not use.
**Warning signs:** None for this phase's scope.

---

## Validation Architecture

nyquist_validation is not explicitly set to false — section included.

This is a frontend phase with no test framework installed. Per the requirements, Cypress/Playwright E2E tests are out of scope (REQUIREMENTS.md: "7-day cap; manual smoke test sufficient"). No automated test commands are applicable.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-03 | Unauthenticated visit to `/dashboard` redirects to `/login` | manual smoke | n/a — no test framework installed | n/a |
| AUTH-04 | Authenticated visit to `/login` redirects to `/dashboard` | manual smoke | n/a | n/a |
| AUTH-05 | Page refresh with valid localStorage refresh token restores session | manual smoke | n/a | n/a |
| (implicit) | Concurrent 401s trigger exactly one refresh request | manual — DevTools Network tab | n/a | n/a |

### Wave 0 Gaps
No automated test framework is installed or planned for this milestone. All verification is manual smoke testing per requirements scope. No Wave 0 setup needed.

**Manual verification steps (per success criteria):**
1. Open app logged out → visit `/dashboard` → confirm redirect to `/login`
2. Log in → visit `/login` → confirm redirect to `/dashboard`
3. Log in → hard refresh browser → confirm still on protected page (no re-login)
4. In DevTools Network, throttle to Slow 3G, trigger two API calls simultaneously with an expired token → confirm exactly one `/auth/refresh` request appears

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Redux for auth state | Zustand `create` | 2021–2022 | No boilerplate, no Provider needed for store access |
| `localStorage` for access token | In-memory (Zustand) + `localStorage` for refresh only | Current best practice | Mitigates XSS token theft for access tokens |
| `withCredentials: true` + httpOnly cookies | Bearer token in memory + refresh in localStorage | Project decision (demo tradeoff documented in STATE.md) | Simpler CORS setup; acceptable for portfolio project |
| Zustand v4 `immer` middleware for updates | Zustand v5 direct `set` | v5.0 (2024) | Less code; `set` merges by default |
| React Router v5 `<Redirect>` | React Router v6/v7 `<Navigate>` | v6 (2021) | Declarative, composable, history-aware |

---

## Open Questions

1. **Placeholder page components for Phase 2**
   - What we know: Phase 2 installs guards; Phase 3 builds actual Login/Signup pages
   - What's unclear: Should Phase 2 create minimal placeholder `<div>` components, or should guards reference routes that don't exist yet?
   - Recommendation: Create minimal stubs (`LoginPage`, `DashboardPage`) as empty components so the router compiles and guards can be manually tested in Phase 2 without waiting for Phase 3.

2. **`/auth/me` call during hydration**
   - What we know: `AuthResponse` from `/auth/refresh` only contains `accessToken`, `refreshToken`, `businessId`, `memberId` — not the full user profile needed by the store
   - What's unclear: Whether to call `/auth/me` after every refresh to get `email`, `role`, `businessName`, `emailVerified`
   - Recommendation: Yes — always call `/auth/me` after a successful refresh during hydration to populate the full user object. This adds one extra call on page load but avoids decoding the JWT on the frontend or storing stale user data in localStorage.

---

## Sources

### Primary (HIGH confidence)
- Java source — `AuthController.java`, `AuthResponse.java`, `RefreshRequest.java`, `MeResponse.java`, `JwtClaims.java` — exact API contract confirmed by reading source
- `frontend/package.json` — confirmed library versions: zustand 5.0.13, axios 1.16.0, react-router-dom 7.15.0
- `frontend/src/lib/api.ts` — existing axios instance structure (withCredentials: false, baseURL from VITE_API_URL)
- `.planning/STATE.md` — locked decisions: access token in memory, refresh token in localStorage, documented demo tradeoff

### Secondary (MEDIUM confidence)
- Zustand v5 docs pattern — `create<State>` with TypeScript, `getState()` for imperative access outside React
- Axios interceptor pattern — response interceptor with `_retry` flag and module-level promise lock is standard community pattern, cross-verified across multiple sources

### Tertiary (LOW confidence — not needed, all findings verified from primary sources)
- None

---

## Metadata

**Confidence breakdown:**
- Backend API contract: HIGH — read directly from Java source files
- Standard stack: HIGH — read directly from package.json
- Architecture patterns: HIGH — derived from locked decisions in STATE.md + standard axios/Zustand idioms verified against current versions
- Pitfalls: HIGH — each pitfall is a logical consequence of the implementation, not speculation

**Research date:** 2026-05-14
**Valid until:** 2026-06-14 (stable libraries; refresh if axios or react-router-dom major version changes)
