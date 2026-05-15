# Phase 4: App Shell + Dashboard Home - Research

**Researched:** 2026-05-14
**Domain:** React layout patterns, react-router-dom nested routes, shadcn/ui, Zustand auth store
**Confidence:** HIGH

## Summary

Phase 4 adds a persistent sidebar shell around all authenticated pages and builds out the dashboard home with business context, plan indicator, and stat cards. The work is purely presentational — no new API calls beyond what Phase 3 already wires up. All user data (name, role, businessName) lives in the Zustand `authStore` populated by Phase 2/3. The plan indicator and stat counts are hardcoded for M1.

The key structural decision is how to share the sidebar across all protected pages. The correct React Router v7 pattern is a layout route: a parent `<Route>` renders the `AppShell` (sidebar + `<Outlet />`), and child routes render their page content into the outlet. This replaces the current flat protected route structure in `App.tsx`.

**Primary recommendation:** Use a React Router v7 layout route (`AppShell` with `<Outlet />`) wrapping all `/dashboard/*` protected routes. Source user data from `useAuthStore`. Use existing `Card` component for stat cards. No new shadcn components needed beyond what is already installed.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SHELL-01 | App has a persistent sidebar with Home, Members, Knowledge Base links | Layout route pattern with `<Outlet />` — sidebar renders once, all child pages share it |
| SHELL-02 | Sidebar shows logged-in user's name, role, and business name | `useAuthStore` already exposes `user.email`, `user.role`, `user.businessName` — read directly |
| SHELL-03 | Active nav item is highlighted in sidebar | `useMatch` / `NavLink` from react-router-dom v7 provides `isActive` prop |
| DASH-01 | Dashboard shows business name and plan indicator | `user.businessName` from store; plan is hardcoded "Free Trial" string for M1 |
| DASH-02 | Dashboard shows quick-action links (Invite member, Add KB) | Two `<Button asChild>` wrapping `<Link>` to `/members` and `/kb` — no modal needed for M1 |
| DASH-03 | Dashboard shows summary stat cards (member count, KB count — hardcoded/zero for M1) | Existing `Card`/`CardContent` components; values hardcoded to `0` |
</phase_requirements>

## Standard Stack

### Core (all already installed — no new installs needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| react-router-dom | ^7.15.0 | Layout routes, NavLink, Outlet, useMatch | Already in use; v7 layout route is the idiomatic nested-layout pattern |
| zustand | ^5.0.13 | Auth state (user name, role, businessName) | Already wired in `useAuthStore` from Phase 2 |
| lucide-react | ^1.14.0 | Sidebar icons (Home, Users, BookOpen, LogOut) | Already installed; used throughout existing components |
| shadcn/ui Card | installed | Stat card shells | Already present in `src/components/ui/card.tsx` |
| shadcn/ui Button | installed | Quick-action CTAs | Already present |
| Tailwind CSS | ^3.4.19 | Layout, spacing, active states | Already configured |

**Installation:** None required. All dependencies are present.

## Architecture Patterns

### Recommended File Structure

```
src/
├── components/
│   ├── layout/
│   │   ├── AppShell.tsx          # Layout component: sidebar + <Outlet />
│   │   ├── Sidebar.tsx           # Nav links, user info, logout button
│   │   └── SidebarNavLink.tsx    # Single nav item with active highlight
│   ├── dashboard/
│   │   └── StatCard.tsx          # Reusable stat card (label + value)
│   ├── auth/                     # (existing — unchanged)
│   └── ui/                       # (existing — unchanged)
├── pages/
│   ├── DashboardPage.tsx         # Rebuilt: business name, plan, quick actions, stat cards
│   ├── MembersPage.tsx           # Stub only in Phase 4 (built out in Phase 5)
│   └── KbPage.tsx                # Stub only in Phase 4 (built out in Phase 6)
└── App.tsx                       # Updated: layout route wrapping protected pages
```

### Pattern 1: Layout Route (React Router v7)

**What:** A parent `<Route>` whose element renders the shell and `<Outlet />`. Child routes render into the outlet without re-mounting the shell.

**When to use:** Any time multiple routes share a persistent frame (sidebar, header, footer).

**Example:**
```tsx
// App.tsx — updated route structure
<Route element={<ProtectedRoute><AppShell /></ProtectedRoute>}>
  <Route path="/dashboard" element={<DashboardPage />} />
  <Route path="/members" element={<MembersPage />} />
  <Route path="/kb" element={<KbPage />} />
</Route>
```

```tsx
// AppShell.tsx
import { Outlet } from "react-router-dom"
import { Sidebar } from "./Sidebar"

export function AppShell() {
  return (
    <div className="flex h-screen">
      <Sidebar />
      <main className="flex-1 overflow-y-auto p-6">
        <Outlet />
      </main>
    </div>
  )
}
```

### Pattern 2: NavLink Active Highlight (React Router v7)

**What:** `NavLink` from react-router-dom passes `isActive` to its `className` callback. Use it to apply highlight styles without manual `useMatch` calls.

**When to use:** Every sidebar nav item.

**Example:**
```tsx
// SidebarNavLink.tsx
import { NavLink } from "react-router-dom"
import type { ReactNode } from "react"

interface SidebarNavLinkProps {
  to: string
  icon: ReactNode
  label: string
}

export function SidebarNavLink({ to, icon, label }: SidebarNavLinkProps) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
          isActive
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
        }`
      }
    >
      {icon}
      {label}
    </NavLink>
  )
}
```

### Pattern 3: User Info from Zustand Store

**What:** Read `user` directly from `useAuthStore` in Sidebar. The store is always populated when `AppShell` renders (ProtectedRoute guarantees `status === "authenticated"`).

**When to use:** Sidebar user display, any component needing current user context.

**Example:**
```tsx
// Sidebar.tsx — user info section
const user = useAuthStore((s) => s.user)!
// user.email, user.role, user.businessName are all available
```

`AuthUser` shape (from `src/types/auth.ts`):
```ts
interface AuthUser {
  id: string
  email: string
  role: "OWNER" | "ADMIN" | "AGENT"
  businessId: string
  businessName: string
  emailVerified: boolean
}
```

### Pattern 4: Stat Card with Hardcoded Value

**What:** Reuse existing `Card`/`CardContent` for zero-state stat cards. No API call.

**Example:**
```tsx
// StatCard.tsx
interface StatCardProps {
  label: string
  value: number | string
}

export function StatCard({ label, value }: StatCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-3xl font-bold">{value}</p>
      </CardContent>
    </Card>
  )
}
```

### Anti-Patterns to Avoid

- **Putting sidebar in every page component:** Duplicates layout, causes remount on navigation. Use layout route instead.
- **Fetching `/auth/me` again in Sidebar:** Store is already hydrated by `useAuthInit`. Re-fetching adds latency and a loading state for data we already have.
- **Using `useMatch` for active state when NavLink exists:** `NavLink` handles active class internally — `useMatch` is redundant for simple nav items.
- **Wrapping each child route in its own ProtectedRoute:** The layout route wraps once; inner routes inherit protection automatically.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Active nav state | Manual `useLocation()` + string compare | `NavLink` className callback | Built-in, handles nested matches, no drift |
| Shared layout | Importing Sidebar in every page | Layout route `<Outlet />` | React Router's design; prevents remount flicker |
| Stat card shell | Custom div structure | Existing `Card`/`CardContent` | Already installed, consistent spacing/border |
| Quick-action buttons | Custom styled `<a>` tags | `<Button asChild><Link to="...">` | Uses existing Button variant + router Link |

**Key insight:** The entire shell/dashboard is composition of already-installed pieces. No new shadcn installs, no new libraries, no API calls beyond what Phase 3 already has.

## Common Pitfalls

### Pitfall 1: ProtectedRoute Wraps AppShell, Not Each Child Route
**What goes wrong:** Placing `<ProtectedRoute>` on each child route causes it to run the loading check multiple times and flashes the loading spinner on navigation between protected pages.
**Why it happens:** Developers copy the pattern from the flat route structure (Phase 2/3) without adapting it to layout routes.
**How to avoid:** Wrap only the layout route element: `<Route element={<ProtectedRoute><AppShell /></ProtectedRoute>}>`. Child routes get protection for free.
**Warning signs:** Spinner appears briefly when navigating from /dashboard to /members.

### Pitfall 2: LogoutButton Needs to Stay in Sidebar, Not DashboardPage
**What goes wrong:** `DashboardPage` currently contains `<LogoutButton />`. Moving it to the sidebar means DashboardPage no longer needs it, but forgetting to remove it creates two logout buttons.
**How to avoid:** In plan 04-01, move `LogoutButton` to `Sidebar`. In plan 04-02, rebuild `DashboardPage` without importing it.

### Pitfall 3: EmailVerificationBanner Placement
**What goes wrong:** `DashboardPage` currently renders `<EmailVerificationBanner />`. When the shell wraps all pages, the banner should remain on DashboardPage (not AppShell) — it is a dashboard-specific element, not a global one.
**How to avoid:** Keep `<EmailVerificationBanner />` in `DashboardPage.tsx` only.

### Pitfall 4: NavLink `end` Prop on `/dashboard`
**What goes wrong:** Without `end`, the `/dashboard` NavLink matches all child routes (e.g., `/dashboard/anything`) and stays permanently active.
**Why it happens:** React Router v7 `NavLink` uses prefix matching by default.
**How to avoid:** Add `end` prop to the Home/Dashboard nav link: `<NavLink to="/dashboard" end>`.

### Pitfall 5: Sidebar Width Pushes Main Content Off-Screen
**What goes wrong:** Fixed sidebar without `flex-1` on the main content area causes layout overflow.
**How to avoid:** Use `flex h-screen` on the shell container, fixed width on sidebar (e.g., `w-64`), and `flex-1 overflow-y-auto` on the main area.

## Code Examples

### Route Structure Update (App.tsx)
```tsx
// Replace flat protected routes with layout route
<Route element={<ProtectedRoute><AppShell /></ProtectedRoute>}>
  <Route path="/dashboard" element={<DashboardPage />} />
  <Route path="/members" element={<MembersPage />} />
  <Route path="/kb" element={<KbPage />} />
</Route>
// Keep non-app routes outside:
<Route path="/login" element={<GuestRoute><LoginPage /></GuestRoute>} />
<Route path="/signup" element={<GuestRoute><SignupPage /></GuestRoute>} />
<Route path="/forgot-password" element={<GuestRoute><ForgotPasswordPage /></GuestRoute>} />
<Route path="/reset-password" element={<GuestRoute><ResetPasswordPage /></GuestRoute>} />
<Route path="*" element={<Navigate to="/dashboard" replace />} />
```

### Lucide Icons for Navigation
```tsx
import { Home, Users, BookOpen, LogOut } from "lucide-react"
// Home → Dashboard
// Users → Members
// BookOpen → Knowledge Base
// LogOut → already used in LogoutButton
```

### Plan Indicator (Hardcoded for M1)
```tsx
// No API call needed — hardcode for M1
const PLAN_LABEL = "Free Trial"

// In DashboardPage:
<span className="inline-flex items-center rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
  {PLAN_LABEL}
</span>
```

### Quick-Action Links
```tsx
import { Button } from "@/components/ui/button"
import { Link } from "react-router-dom"

<Button asChild variant="outline">
  <Link to="/members">Invite Member</Link>
</Button>
<Button asChild variant="outline">
  <Link to="/kb">Add Knowledge Base</Link>
</Button>
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Manual location match for active nav | NavLink `isActive` callback | Zero manual routing logic |
| Render sidebar in every page | Layout route + Outlet | Single mount, no flicker between pages |

## Open Questions

1. **Members and KB stub pages for Phase 4**
   - What we know: Phase 5 builds Members, Phase 6 builds KB. Phase 4 just needs routes to exist so nav links resolve without 404.
   - What's unclear: How minimal should the stubs be?
   - Recommendation: `MembersPage` and `KbPage` return a single `<h1>` placeholder. Phase 5/6 rebuild them fully. This unblocks nav link verification in Phase 4.

2. **Sidebar on mobile**
   - What we know: REQUIREMENTS.md explicitly marks "Mobile-first responsive layout" as out of scope.
   - Recommendation: Desktop-only fixed sidebar, no hamburger/drawer needed for M1.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 2.1.9 + React Testing Library 16 |
| Config file | `frontend/vite.config.ts` (vitest config inline) |
| Quick run command | `cd frontend && npm test -- --run` |
| Full suite command | `cd frontend && npm test -- --run --coverage` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SHELL-01 | Sidebar renders with 3 nav links | unit | `npm test -- --run src/components/layout/Sidebar.test.tsx` | Wave 0 |
| SHELL-02 | Sidebar displays user name, role, businessName from store | unit | `npm test -- --run src/components/layout/Sidebar.test.tsx` | Wave 0 |
| SHELL-03 | Active nav link gets highlight class | unit | `npm test -- --run src/components/layout/Sidebar.test.tsx` | Wave 0 |
| DASH-01 | Dashboard shows businessName and plan indicator | unit | `npm test -- --run src/pages/DashboardPage.test.tsx` | Wave 0 |
| DASH-02 | Dashboard shows Invite Member and Add KB links | unit | `npm test -- --run src/pages/DashboardPage.test.tsx` | Wave 0 |
| DASH-03 | Dashboard shows stat cards with value 0 | unit | `npm test -- --run src/pages/DashboardPage.test.tsx` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd frontend && npm test -- --run`
- **Per wave merge:** `cd frontend && npm test -- --run --coverage`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/components/layout/Sidebar.test.tsx` — covers SHELL-01, SHELL-02, SHELL-03
- [ ] `src/pages/DashboardPage.test.tsx` — covers DASH-01, DASH-02, DASH-03

*(Existing test infrastructure: Vitest + RTL already configured and passing. No framework install needed.)*

## Sources

### Primary (HIGH confidence)
- Codebase direct inspection — `src/types/auth.ts`, `src/store/authStore.ts`, `src/App.tsx`, `src/components/ui/card.tsx`, `frontend/package.json` — confirmed exact versions, shapes, and installed components
- React Router v7 layout routes — pattern verified against react-router-dom v7 installed in project (`^7.15.0`)

### Secondary (MEDIUM confidence)
- NavLink `end` prop behavior — standard React Router v7 documented behavior, consistent with v6 (no breaking change in this API)
- lucide-react v1.14.0 icon names (`Home`, `Users`, `BookOpen`, `LogOut`) — verified via installed package version

### Tertiary (LOW confidence)
- None — all findings backed by direct codebase inspection or well-established library APIs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all packages confirmed installed and versioned from package.json
- Architecture: HIGH — layout route pattern is the canonical React Router v7 approach; confirmed via existing router usage in App.tsx
- Pitfalls: HIGH — derived from direct reading of existing code (LogoutButton in DashboardPage, EmailVerificationBanner placement, flat route structure that must change)

**Research date:** 2026-05-14
**Valid until:** 2026-06-14 (stable stack, no fast-moving dependencies)
