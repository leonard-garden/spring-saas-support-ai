# Stack Research: React Admin Dashboard

**Domain:** React SPA admin dashboard connecting to Spring Boot REST API
**Project:** spring-saas-support-ai (M1 Frontend — Admin Dashboard)
**Researched:** 2026-05-13
**Overall confidence:** HIGH

---

## Recommended Stack

| Layer | Library | Version | Rationale |
|-------|---------|---------|-----------|
| Build | Vite | 5.x | CRA deprecated; fastest HMR; trivial config |
| UI | React | 18.x | Stable, concurrent features |
| Types | TypeScript | 5.x | Strict mode — already in project |
| Routing | react-router-dom | 6.x | SPA mode, nested layouts, no SSR overhead |
| Components | shadcn/ui | latest | Copy-paste, Radix + Tailwind, zero dead code — already named in CLAUDE.md |
| Styling | Tailwind CSS | 3.x | Already in CLAUDE.md stack |
| Icons | lucide-react | latest | Ships with shadcn/ui toolchain |
| Server state | @tanstack/react-query | 5.x | Best REST integration DX; caching + loading states |
| HTTP client | axios | 1.x | Interceptors for JWT attach + 401 refresh-retry |
| Auth state | zustand | 4.x | ~1KB, no Provider, stores token + user |
| Forms | react-hook-form | 7.x | Uncontrolled inputs, minimal re-renders |
| Validation | zod | 3.x | TypeScript-first schema validation |
| RHF bridge | @hookform/resolvers | 3.x | Connects zod schema to useForm |

---

## Spring Boot Integration Points

**CORS** — Vite dev server runs on `localhost:5173`. Spring Boot `CorsConfigurationSource` must allow this origin explicitly. `setAllowCredentials(true)` required if cookies used.

**axios instance pattern** (`src/lib/api.ts`):
- Base URL from `VITE_API_URL` env var (`http://localhost:8080/api/v1` in dev)
- Request interceptor: reads `accessToken` from Zustand store, appends `Authorization: Bearer <token>`
- Response interceptor: on 401 + `!config._retry`, call `POST /auth/refresh`, update store, retry once. On second 401, clear store and redirect to `/login`

**Environment variables** (Vite uses `VITE_` prefix):
```
VITE_API_URL=http://localhost:8080/api/v1   # .env.development
VITE_API_URL=https://app.onrender.com/api/v1  # .env.production
```

---

## Recommended Project Structure

```
admin-ui/                          # lives at repo root alongside backend src/
├── src/
│   ├── components/                # shadcn/ui primitives + local composites
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── SignupPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── MembersPage.tsx
│   │   └── KnowledgeBasePage.tsx  # stub — links to M2 backend work
│   ├── stores/
│   │   └── auth.ts                # Zustand: accessToken, user, refresh()
│   ├── lib/
│   │   └── api.ts                 # axios instance with interceptors
│   ├── hooks/                     # useMembers(), useInvite() etc. (react-query)
│   └── App.tsx                    # Router + QueryClientProvider + layout
├── .env.development
├── .env.production
└── vite.config.ts
```

---

## Installation Sequence

```bash
# From project root
npm create vite@latest admin-ui -- --template react-ts
cd admin-ui

npm install react-router-dom axios @tanstack/react-query zustand
npm install react-hook-form @hookform/resolvers zod
npm install lucide-react
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p

# After Tailwind is configured:
npx shadcn@latest init
# Add components as needed:
# npx shadcn@latest add button input label card table badge dropdown-menu avatar
```

---

## What NOT to Add

| Library | Why Skip |
|---------|----------|
| Next.js | SSR unnecessary for auth-gated SPA; adds build/deploy complexity |
| Redux Toolkit | Zustand covers auth state with 1/5 the boilerplate |
| i18n (react-i18next) | Project constraint: English only |
| Cypress / Playwright | 7-day milestone; demo app; skip frontend E2E |
| Framer Motion | Animations not required; ~150KB bundle cost |
| Storybook | Not a design system; demo app |
| MSW | Spring Boot API is already running; no mock needed |

---

## Open Questions

- Does the Spring Boot CORS config already allow `localhost:5173`? If not, this is day-one blocker.
- Token storage: localStorage (simpler, XSS risk) vs httpOnly cookies (secure). For demo, localStorage acceptable. Harden in M4.
- Where does `admin-ui/` live — repo root sibling to `src/`, or separate repo? Monorepo-sibling is simpler for Render deployment.
