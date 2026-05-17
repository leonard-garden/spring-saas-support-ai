# Architecture Research: React Admin Dashboard Integration

**Domain:** React SPA integrating with Spring Boot JWT backend
**Project:** spring-saas-support-ai (M1 Frontend — Admin Dashboard)
**Researched:** 2026-05-13

---

## Integration Architecture

### Serve Strategy: Separate Deploy (Recommended)

```
admin-ui/  (repo root, sibling to src/)
  → Render Static Site (free tier)
  → Calls Spring Boot API at https://api.onrender.com/api/v1

src/  (Spring Boot backend)
  → Render Web Service (free tier)
  → REST API only — no static file serving
```

**Why separate:** Simpler deploy pipeline. Static site = instant cold start vs Spring Boot's 30s warm-up. No classpath resources to manage. Can redeploy frontend independently.

**Why not embed in Spring Boot:** Would require copying `dist/` to `src/main/resources/static/` on every build. Couples frontend and backend deploys. Against "minimal, demo-only" philosophy.

---

## JWT Token Management (Client-Side)

### Storage Strategy

| Token | Storage | Rationale |
|-------|---------|-----------|
| Access token (15 min) | Memory (Zustand store) | Never persisted; lost on page refresh; re-acquired via refresh |
| Refresh token (7 days) | `localStorage` (demo) | httpOnly cookie is safer; for demo, localStorage acceptable with documented tradeoff |

### Token Flow

```
Page load
  → read refreshToken from localStorage
  → if exists: POST /auth/refresh → get new accessToken → store in memory
  → if missing: redirect to /login

API calls
  → axios request interceptor: attach accessToken from memory

401 response
  → axios response interceptor: attempt refresh → retry once
  → second 401: clear tokens, redirect to /login

Logout
  → POST /auth/logout (server-side revocation)
  → clear memory (accessToken) + localStorage (refreshToken)
  → redirect to /login
```

---

## New Components

### `src/lib/api.ts` — Axios Instance

```typescript
import axios from 'axios';
import { useAuthStore } from '@/stores/auth';

let refreshPromise: Promise<string> | null = null;

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(null, async (error) => {
  if (error.response?.status === 401 && !error.config._retry) {
    error.config._retry = true;
    if (!refreshPromise) {
      refreshPromise = api.post('/auth/refresh', {
        refreshToken: localStorage.getItem('refreshToken'),
      }).then(r => {
        refreshPromise = null;
        return r.data.data.accessToken;
      }).catch(e => {
        refreshPromise = null;
        throw e;
      });
    }
    try {
      const token = await refreshPromise;
      useAuthStore.getState().setAccessToken(token);
      error.config.headers.Authorization = `Bearer ${token}`;
      return api(error.config);
    } catch {
      useAuthStore.getState().clear();
      window.location.href = '/login';
    }
  }
  return Promise.reject(error);
});
```

### `src/stores/auth.ts` — Zustand Auth Store

```typescript
import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  user: { email: string; role: string; tenantId: string } | null;
  status: 'loading' | 'authenticated' | 'unauthenticated';
  setAccessToken: (token: string) => void;
  setUser: (user: AuthState['user']) => void;
  setStatus: (status: AuthState['status']) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  status: 'loading',
  setAccessToken: (token) => set({ accessToken: token }),
  setUser: (user) => set({ user }),
  setStatus: (status) => set({ status }),
  clear: () => {
    localStorage.removeItem('refreshToken');
    set({ accessToken: null, user: null, status: 'unauthenticated' });
  },
}));
```

### Protected Route Component

```typescript
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { status } = useAuthStore();
  if (status === 'loading') return <LoadingSpinner />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace />;
  return <>{children}</>;
}
```

---

## Build Order (Phases)

1. **Scaffold** — `npm create vite`, install deps, configure Tailwind + shadcn/ui, set up env vars
2. **Auth core** — axios instance + interceptors, Zustand store, `ProtectedRoute`
3. **Auth pages** — Login, Signup, ForgotPassword, ResetPassword
4. **App shell** — sidebar layout, navigation, user header
5. **Members page** — table, invite modal, remove confirm, role change
6. **KB stub** — empty state page
7. **Dashboard home** — stats cards (hardcoded), quick actions
8. **Deploy** — Render static site, env vars, SPA rewrite rule

---

## Render Deployment

```yaml
# render.yaml (in admin-ui/)
services:
  - type: web
    name: saas-support-admin-ui
    env: static
    buildCommand: npm install && npm run build
    staticPublishPath: ./dist
    routes:
      - type: rewrite
        source: /*
        destination: /index.html
    envVars:
      - key: VITE_API_URL
        value: https://your-backend.onrender.com/api/v1
```

---

## CORS Checklist (Spring Boot Side)

Verify these are set in `SecurityConfig.java` before writing any frontend auth code:

- [ ] `allowedOrigins` includes `http://localhost:5173` (dev) and Render frontend URL (prod)
- [ ] `allowedMethods` includes `GET, POST, PUT, DELETE, PATCH, OPTIONS`
- [ ] `allowedHeaders` includes `Authorization, Content-Type`
- [ ] `allowCredentials` set correctly (true if using cookies, false for Bearer tokens is fine)
- [ ] `HttpMethod.OPTIONS` requests are `permitAll()` in the security filter chain
