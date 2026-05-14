# Phase 1: Scaffold + CORS Verification - Research

**Researched:** 2026-05-13
**Domain:** Vite + React + TypeScript + shadcn/ui (Tailwind v3) project scaffolding
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: React project lives at `frontend/` (repo root, sibling to `src/` and `pom.xml`)
- D-02: NOT `admin-ui/` — use `frontend/` as the directory name
- D-03: Use `npm` — no extra tooling, works everywhere, consistent with Claude-generated examples
- D-04: Create a dedicated `/cors-test` route (a proper page) with a form that calls `POST /api/v1/auth/login`
- D-05: Page shows the response (success or CORS error) inline — gives a clear repeatable pass/fail in git
- D-06: This page can be removed or kept behind a dev-only guard in Phase 2
- D-07: Build: Vite 5.x + React 18 + TypeScript 5.x (strict mode)
- D-08: Routing: react-router-dom 6.x
- D-09: Components: shadcn/ui (Radix + Tailwind) + lucide-react
- D-10: Server state: @tanstack/react-query 5.x
- D-11: HTTP client: axios 1.x
- D-12: Auth state: zustand 4.x
- D-13: Forms: react-hook-form 7.x + zod 3.x + @hookform/resolvers 3.x
- D-14: Styling: Tailwind CSS 3.x

### Claude's Discretion
- Exact shadcn/ui init configuration (default theme, CSS variables, etc.)
- Whether to init the git-ignored `.env.development` with a placeholder or instructions
- Vite config details (port, proxy if any)

### Deferred Ideas (OUT OF SCOPE)
- None — discussion stayed within Phase 1 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| (none) | Phase 1 has no direct v0.2 requirements — it is an infrastructure gate that unblocks Phases 2–6 | CORS verified = all auth/UI phases can proceed |
</phase_requirements>

---

## Summary

Phase 1 scaffolds the `frontend/` React SPA and verifies that a cross-origin POST to the Spring Boot backend succeeds. It is the infrastructure gate for all subsequent phases — nothing else can be built until CORS is confirmed to work.

The critical technical finding is that **shadcn/ui and Tailwind v3 require a pinned CLI version**. The `shadcn@latest` CLI (v4.x as of 2026) generates Tailwind v4 config by default. For Tailwind v3 projects, use `npx shadcn@2.3.0 init`, which generates the correct `tailwind.config.js` + `postcss.config.js` pair, `@tailwind base/components/utilities` directives in CSS, and `tailwindcss-animate` dependency. Using `shadcn@latest` with Tailwind v3 produces broken or incompatible config.

The CORS verification pattern is a simple dedicated React page with a form that fires `axios.post` to the backend login endpoint and displays the raw response inline. This gives a repeatable, in-git pass/fail artifact. The backend CORS config already allows `http://localhost:5173` (port 5173 is the Vite default) so no backend changes are needed for this phase.

**Primary recommendation:** Run `npm create vite@latest frontend -- --template react-ts`, install all dependencies, then `npx shadcn@2.3.0 init` for Tailwind v3-compatible component setup.

---

## Standard Stack

### Core (verified npm versions, 2026-05-13)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| vite | 5.x (latest: 6.x exists — lock to 5.x per decision) | Build tool + dev server | Fastest HMR; CRA deprecated |
| react + react-dom | 18.x | UI runtime | Stable concurrent features; React 19 not yet required |
| typescript | 5.x | Static types | Strict mode already in project |
| react-router-dom | 7.15.0 | SPA routing | Nested layouts, no SSR overhead |
| @tanstack/react-query | 5.100.10 | Server state / data fetching | Best DX for REST; caching + loading states |
| axios | 1.16.0 | HTTP client | Interceptors for JWT attach + 401 refresh-retry |
| zustand | 5.0.13 | Auth state store | ~1KB, no Provider boilerplate |
| react-hook-form | 7.75.0 | Form management | Uncontrolled inputs, minimal re-renders |
| zod | 3.x (3.4.4+) | Schema validation | TypeScript-first; pairs with RHF |
| @hookform/resolvers | 3.x (3.x) | RHF + zod bridge | Connects zod schema to useForm |
| lucide-react | 0.x (latest: 1.14.0) | Icons | Ships with shadcn/ui toolchain |

### shadcn/ui + Tailwind v3 Specific

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| tailwindcss | 3.4.19 (latest v3) | Utility CSS | Use `tailwindcss@3` — do NOT install v4 |
| postcss | latest | CSS processing | Required peer dep for Tailwind v3 |
| autoprefixer | latest | Vendor prefixing | Required peer dep for Tailwind v3 |
| shadcn CLI | 2.3.0 | Component scaffolding | LOCKED — `npx shadcn@2.3.0 init` for v3 compat |
| tailwindcss-animate | latest | Animation utilities | Auto-added by shadcn init |
| class-variance-authority | 0.7.1 | Component variants | Auto-added by shadcn init |
| clsx | 2.1.1 | Class merging | Auto-added by shadcn init |
| tailwind-merge | 3.6.0 | Tailwind class dedup | Auto-added by shadcn init |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| shadcn@2.3.0 | shadcn@latest | latest generates Tailwind v4 config — incompatible with project decision D-14 |
| Tailwind v3 | Tailwind v4 | v4 drops tailwind.config.js, uses CSS-only config — non-trivial migration, not worth it for demo |
| zustand 4.x | zustand 5.x | 5.0.13 is current; API is backward-compatible; use 5.x |

### Installation

```bash
# From repo root — creates frontend/ directory
npm create vite@latest frontend -- --template react-ts
cd frontend

# Runtime deps
npm install react-router-dom axios @tanstack/react-query zustand
npm install react-hook-form @hookform/resolvers zod
npm install lucide-react

# Tailwind v3 (MUST pin to v3 — not v4)
npm install -D tailwindcss@3 postcss autoprefixer
npx tailwindcss init -p

# shadcn/ui (MUST use 2.3.0 for Tailwind v3 compatibility)
npm install -D @types/node
npx shadcn@2.3.0 init

# Add initial components
npx shadcn@2.3.0 add button input label card
```

---

## Architecture Patterns

### Recommended Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   └── ui/              # shadcn/ui generated components (auto-populated)
│   ├── pages/
│   │   └── CorsTestPage.tsx # Phase 1: CORS verification route
│   ├── lib/
│   │   └── api.ts           # axios instance (base URL from env var)
│   ├── App.tsx              # Router + QueryClientProvider
│   ├── main.tsx             # React root mount
│   └── index.css            # Tailwind directives
├── .env.development         # VITE_API_URL=http://localhost:8081/api/v1
├── .env.production          # VITE_API_URL=https://<render-domain>/api/v1
├── .gitignore               # Must include .env.development
├── components.json          # shadcn/ui config (generated by init)
├── tailwind.config.js       # Tailwind v3 config (generated by init)
├── postcss.config.js        # postcss + autoprefixer (generated by init)
├── tsconfig.json            # Path alias: "@/*" -> "./src/*"
├── tsconfig.app.json        # Strict TS + path alias (Vite splits tsconfig)
└── vite.config.ts           # React plugin + @/ alias resolver
```

### Pattern 1: Tailwind v3 vite.config.ts (NO @tailwindcss/vite plugin)

**What:** Tailwind v3 uses postcss, not the Vite plugin. The vite.config.ts only needs the React plugin and path alias.
**When to use:** Always — this is correct for Tailwind v3.

```typescript
// Source: https://github.com/shadcn/vite-template-v3/blob/main/vite.config.ts
import path from "path"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
})
```

Note: Do NOT add `tailwindcss from "@tailwindcss/vite"` — that is the Tailwind v4 pattern.

### Pattern 2: tsconfig.json path alias (Vite splits config across two files)

**What:** Vite scaffolds two tsconfig files. Both need the `@/` alias or TypeScript won't resolve it.
**When to use:** Always — single tsconfig will not work with Vite's setup.

```json
// tsconfig.json (root — add baseUrl/paths here)
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ],
  "compilerOptions": {
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  }
}
```

```json
// tsconfig.app.json (also needs baseUrl/paths)
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["src"]
}
```

### Pattern 3: Tailwind v3 index.css (classic directives, not @import)

```css
/* src/index.css — Tailwind v3 pattern */
@tailwind base;
@tailwind components;
@tailwind utilities;

/* shadcn/ui CSS variables added by `npx shadcn@2.3.0 init` below */
@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 20 14.3% 4.1%;
    /* ... rest generated by shadcn init ... */
    --radius: 0.5rem;
  }
}
```

Note: Tailwind v4 uses `@import "tailwindcss"` — never use that with v3.

### Pattern 4: tailwind.config.js for shadcn/ui (Tailwind v3)

```javascript
// Source: https://github.com/shadcn/vite-template-v3/blob/main/tailwind.config.js
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      colors: {
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        // ... rest generated by shadcn init
      },
    },
  },
  plugins: ["tailwindcss-animate"],
}
```

### Pattern 5: .env.development for Vite API URL

```bash
# frontend/.env.development
# Vite ONLY exposes vars prefixed with VITE_ to client code
VITE_API_URL=http://localhost:8081/api/v1
```

Access in code: `import.meta.env.VITE_API_URL`

Add to `.gitignore`:
```
.env.development
.env.production
```

Add `.env.development.example` (committed):
```bash
# Copy to .env.development and fill in values
VITE_API_URL=http://localhost:8081/api/v1
```

### Pattern 6: Minimal axios instance (Phase 1 — no interceptors yet)

```typescript
// src/lib/api.ts
import axios from "axios"

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  headers: { "Content-Type": "application/json" },
  // withCredentials: false — backend uses Bearer tokens, not cookies
})

export default api
```

Note: `withCredentials` must remain `false` unless using cookies. Backend has `allowCredentials=true` but that only matters when cookies cross origins. Bearer tokens in headers work fine without it.

### Pattern 7: CORS test page

**What:** A dedicated `/cors-test` route that POSTs to `POST /api/v1/auth/login` and displays the raw response. No auth state, no routing guards — just a raw cross-origin call.

```typescript
// src/pages/CorsTestPage.tsx
import { useState } from "react"
import axios from "axios"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

const API_URL = import.meta.env.VITE_API_URL

export function CorsTestPage() {
  const [email, setEmail] = useState("test@example.com")
  const [password, setPassword] = useState("password123")
  const [result, setResult] = useState<string | null>(null)
  const [status, setStatus] = useState<"idle" | "loading" | "ok" | "error">("idle")

  async function handleTest() {
    setStatus("loading")
    setResult(null)
    try {
      const response = await axios.post(
        `${API_URL}/auth/login`,
        { email, password },
        { headers: { "Content-Type": "application/json" } }
      )
      setStatus("ok")
      setResult(JSON.stringify(response.data, null, 2))
    } catch (err: unknown) {
      setStatus("error")
      if (axios.isAxiosError(err)) {
        setResult(
          err.response
            ? JSON.stringify({ status: err.response.status, data: err.response.data }, null, 2)
            : `Network error / CORS blocked: ${err.message}`
        )
      } else {
        setResult(String(err))
      }
    }
  }

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <CardTitle>CORS Verification — POST /api/v1/auth/login</CardTitle>
          <p className="text-sm text-muted-foreground">
            API: <code>{API_URL}</code>
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          <Input
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Button onClick={handleTest} disabled={status === "loading"} className="w-full">
            {status === "loading" ? "Testing..." : "Fire POST request"}
          </Button>
          {result && (
            <div
              className={`rounded p-3 text-sm font-mono whitespace-pre-wrap ${
                status === "ok"
                  ? "bg-green-50 text-green-800 border border-green-200"
                  : "bg-red-50 text-red-800 border border-red-200"
              }`}
            >
              {status === "ok" ? "PASS" : "FAIL"}: {result}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
```

### Pattern 8: App.tsx with /cors-test route

```typescript
// src/App.tsx
import { BrowserRouter, Routes, Route } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { CorsTestPage } from "@/pages/CorsTestPage"

const queryClient = new QueryClient()

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/cors-test" element={<CorsTestPage />} />
          {/* Phase 2+ routes added here */}
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
```

### Anti-Patterns to Avoid

- **Wrong shadcn CLI version:** `npx shadcn@latest init` (v4.x) generates `@import "tailwindcss"` in CSS and `@tailwindcss/vite` in vite.config — both wrong for Tailwind v3. The component code itself will still render but Tailwind classes will silently fail.
- **`@tailwindcss/vite` plugin in vite.config:** This is only for Tailwind v4. With Tailwind v3, postcss handles it — adding this plugin causes duplicate processing or errors.
- **Single tsconfig for path aliases:** Vite scaffolds both `tsconfig.json` and `tsconfig.app.json`. Only patching one causes TypeScript to fail resolving `@/` imports in different contexts.
- **`withCredentials: true` in axios:** Not needed for Bearer token auth. Setting it to true for a CORS test will add a `Cookie` header expectation and may cause CORS failures if the backend doesn't return `Access-Control-Allow-Credentials: true` (it does, but it's unnecessary complexity).
- **Hardcoding `http://localhost:8081` in source code:** Always use `import.meta.env.VITE_API_URL` — failing to do this on day one causes scattered find-replace before Render deploy.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Component library | Custom button/input/card components | shadcn/ui add button input card | 30+ edge cases per component (focus, disabled, a11y, keyboard nav) |
| Path alias resolution | Manual `../../` relative imports | tsconfig `@/*` + vite resolver | Breaks on file moves; IDE loses autocomplete |
| Class conditional merging | Custom cn() helper | `clsx` + `tailwind-merge` (auto-added by shadcn) | Tailwind class precedence requires dedup logic |
| CSS variable theming | Custom CSS vars | shadcn/ui CSS variable system | Theming, dark mode, and Radix primitives expect this exact shape |
| Form validation wiring | Manual onChange validation | react-hook-form + zod + @hookform/resolvers | Touched state, error display, submit guard, async validation |

**Key insight:** shadcn/ui components auto-install their own peer dependencies (`clsx`, `tailwind-merge`, `class-variance-authority`) — do not add these manually before running `npx shadcn@2.3.0 init`.

---

## Common Pitfalls

### Pitfall 1: shadcn@latest breaks Tailwind v3 config
**What goes wrong:** `npx shadcn@latest init` (CLI v4.x) generates `@import "tailwindcss"` in CSS and expects `@tailwindcss/vite` plugin. Running it against a Tailwind v3 install produces broken CSS output with no error message.
**Why it happens:** shadcn@latest defaults to Tailwind v4 since early 2025.
**How to avoid:** Always pin `npx shadcn@2.3.0 init` for Tailwind v3 projects.
**Warning signs:** CSS file shows `@import "tailwindcss"` instead of `@tailwind base/components/utilities` after init.

### Pitfall 2: Vite generates two tsconfig files — alias must be in both
**What goes wrong:** `@/` imports work in IDE but fail during `npm run build` or show red squiggles in one context.
**Why it happens:** Vite scaffolds `tsconfig.json` (references only) and `tsconfig.app.json` (actual compiler options). Each needs `baseUrl` and `paths`.
**How to avoid:** After adding alias to `tsconfig.json`, also add the same `compilerOptions.baseUrl` and `compilerOptions.paths` block to `tsconfig.app.json`.
**Warning signs:** TypeScript errors on `@/` imports only in certain files or only after restart.

### Pitfall 3: CORS blocked by Spring Security before CORS filter runs
**What goes wrong:** Browser OPTIONS preflight gets a 401/403, so the actual POST never fires. The CORS test page shows "Network error / CORS blocked".
**Why it happens:** Spring Security's `JwtAuthFilter` runs before the CORS filter. OPTIONS preflights carry no Authorization header by spec.
**How to avoid:** Verify `SecurityConfig.java` has `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`. This is already in the project (PR #33 fixed it) — just confirm it's still there before blaming the frontend.
**Warning signs:** Network tab shows 401 or 403 on the OPTIONS preflight request, not on the POST itself.

### Pitfall 4: VITE_ prefix required for env vars exposed to browser
**What goes wrong:** `process.env.API_URL` is undefined at runtime; `import.meta.env.API_URL` is also undefined.
**Why it happens:** Vite only exposes env vars prefixed with `VITE_` to the browser bundle. Others are server-only.
**How to avoid:** Name the var `VITE_API_URL` in `.env.development`. Access via `import.meta.env.VITE_API_URL`.
**Warning signs:** `import.meta.env.VITE_API_URL` is `undefined` in browser console.

### Pitfall 5: npm run dev fails — port 5173 already in use
**What goes wrong:** Vite fails to start or silently picks port 5174 instead of 5173. The CORS test then fails because `http://localhost:5174` is not in the backend's `allowedOrigins`.
**Why it happens:** Another process is using 5173.
**How to avoid:** Optionally set `server.port: 5173` and `server.strictPort: true` in vite.config.ts to get a clear error instead of a silent port shift. Backend already allows 5173; if Vite shifts to 5174, add it to `application-dev.yml`.
**Warning signs:** CORS test fails but the backend logs show the request origin as `http://localhost:5174`.

---

## Code Examples

### postcss.config.js (Tailwind v3 — generated by `npx tailwindcss init -p`)
```javascript
// Source: https://github.com/shadcn/vite-template-v3
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

### components.json (generated by `npx shadcn@2.3.0 init`)
```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "tailwind.config.js",
    "css": "src/index.css",
    "baseColor": "neutral",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

### lib/utils.ts (auto-generated by shadcn init)
```typescript
// Source: auto-generated by npx shadcn@2.3.0 init
import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
```

### Expected backend response shape for CORS test page
```typescript
// ApiResponse<T> envelope from Spring Boot backend
interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: string | null
}

// Successful login response shape
interface LoginData {
  accessToken: string
  refreshToken: string
  // ... other fields
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `tailwind.config.js` | CSS-only config (Tailwind v4) | Early 2025 | This project stays on v3 — use classic config |
| `@tailwind base/components/utilities` | `@import "tailwindcss"` (v4) | Early 2025 | Use old directives for v3 |
| `npx shadcn-ui@latest` | `npx shadcn@latest` | 2024 | Package renamed; shadcn@2.3.0 = last v3-compatible |
| Create React App | `npm create vite@latest` | 2023 | CRA deprecated; Vite is the standard |
| `tailwindcss-animate` | `tw-animate-css` (v4) | Early 2025 | shadcn@2.3.0 still uses tailwindcss-animate — correct for v3 |

**Deprecated/outdated:**
- `create-react-app`: deprecated, do not use
- `shadcn-ui` npm package name: renamed to `shadcn` in 2024
- `npx shadcn@latest init` for Tailwind v3 projects: generates v4 config — use `npx shadcn@2.3.0 init` instead

---

## Open Questions

1. **Zustand version: 4.x vs 5.x**
   - What we know: npm shows 5.0.13 is current; STACK.md says zustand 4.x
   - What's unclear: Are there breaking API changes between 4.x and 5.x?
   - Recommendation: Install `zustand@5` — the store creation API (`create()`) is backward-compatible. Phase 1 only installs the package, no store code yet, so this is safe.

2. **react-router-dom 6.x vs 7.x**
   - What we know: npm shows 7.15.0 is current; STACK.md says 6.x
   - What's unclear: Breaking changes in v7 API?
   - Recommendation: `react-router-dom@7` retains the same `BrowserRouter + Routes + Route` API used in this project. Install v7; the CorsTestPage pattern shown above works for both v6 and v7.

---

## Validation Architecture

No test framework is currently configured for the frontend. Phase 1 is infrastructure scaffolding — the pass/fail is manual: `npm run dev` starts without error, and the `/cors-test` page returns a green response from the backend.

### Phase Gate (Manual)
| Check | Pass Criteria |
|-------|---------------|
| `npm run dev` starts | No errors; Vite reports `Local: http://localhost:5173/` |
| `npm run build` passes | No TypeScript errors; dist/ directory created |
| `/cors-test` page loads | Browser renders the card with inputs |
| POST to backend | Response shows `{"success": true, "data": {...}}` in green box |

Wave 0 gaps: No automated test infrastructure planned for Phase 1. Manual verification is the gate.

---

## Sources

### Primary (HIGH confidence)
- `https://github.com/shadcn/vite-template-v3` — Official shadcn/ui Tailwind v3 template; used for vite.config.ts, tailwind.config.js, postcss.config.js, index.css, tsconfig files
- `https://v3.shadcn.com/docs/installation/vite` — Official shadcn/ui legacy Vite installation docs (Tailwind v3)
- npm registry — `npm view` for all verified package versions (2026-05-13)

### Secondary (MEDIUM confidence)
- `https://ui.shadcn.com/docs/installation/vite` — Current shadcn/ui docs (Tailwind v4 default); confirmed v3 users must use `shadcn@2.3.0`
- `https://ui.shadcn.com/docs/tailwind-v4` — Confirmed Tailwind v4 migration docs; validates that v3 projects must NOT follow this path

### Tertiary (LOW confidence)
- None — all critical claims verified against official sources or npm registry.

---

## Metadata

**Confidence breakdown:**
- Standard stack versions: HIGH — all verified via `npm view` on 2026-05-13
- shadcn@2.3.0 + Tailwind v3 sequence: HIGH — verified against official vite-template-v3 template files
- CORS test page pattern: HIGH — based on backend CORS config already in codebase + axios docs
- Architecture pitfalls: HIGH — Pitfalls 1-2 from official shadcn/ui changelog; Pitfall 3 already fixed in project (PR #33)

**Research date:** 2026-05-13
**Valid until:** 2026-08-13 (90 days — Tailwind and shadcn are fast-moving; re-verify CLI version before new projects)
