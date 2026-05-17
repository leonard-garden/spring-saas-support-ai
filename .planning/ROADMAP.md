# Roadmap: spring-saas-support-ai

## Milestones

- ✅ **v0.1 — M1 Backend** - Pre-GSD (shipped 2026-05-13)
- 🚧 **v0.2 — M1 Frontend: Admin Dashboard** - Phases 1–7 (in progress)

## Phases

<details>
<summary>✅ v0.1 — M1 Backend: Multi-tenant Foundation — SHIPPED 2026-05-13</summary>

Delivered before GSD was adopted. No phase tracking. See MILESTONES.md for full delivery list.

</details>

### 🚧 v0.2 — M1 Frontend: Admin Dashboard (In Progress)

**Milestone Goal:** A working React admin dashboard that lets a business owner log in, view their team, and see the product — deployable to Render and demoable in 2 minutes.

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Scaffold + CORS Verification** - Vite project created, cross-origin API call confirmed working (completed 2026-05-13)
- [ ] **Phase 2: Auth Infrastructure** - Axios refresh-lock interceptor, Zustand tri-state store, ProtectedRoute guard
- [ ] **Phase 3: Auth Pages** - Login, signup, logout, forgot/reset password, email verification banner
- [ ] **Phase 4: App Shell + Dashboard Home** - Sidebar layout, user info, plan indicator, stat cards
- [ ] **Phase 5: Member Management** - Members table, invite modal, remove confirm, role change, status badge
- [ ] **Phase 6: KB Stub + Polish** - Static KB page, password show/hide, final polish
- [ ] **Phase 7: Deploy + Smoke Test** - Render static site live, SPA rewrite rule, full demo flow verified

## Phase Details

### Phase 1: Scaffold + CORS Verification
**Goal**: The React project exists with correct tooling and a cross-origin API call to the live backend succeeds — unblocking all frontend work
**Depends on**: Nothing (first phase)
**Requirements**: None directly — infrastructure prerequisite that unblocks all other phases
**Success Criteria** (what must be TRUE):
  1. `frontend/` directory exists at repo root with Vite + React 18 + TypeScript configured
  2. All stack libraries are installed (axios, react-router-dom, zustand, react-query, react-hook-form, zod, shadcn/ui)
  3. A manual `POST /api/v1/auth/login` call from the browser (or dev tools) returns 200 without CORS errors
  4. `npm run dev` starts without errors and renders the Vite default page or a placeholder
**Plans:** 2/2 plans complete

Plans:
- [x] 01-01: Scaffold Vite project and install all stack dependencies
- [x] 01-02: Verify CORS with a live cross-origin login probe

### Phase 2: Auth Infrastructure
**Goal**: Users can navigate the app safely — protected routes block unauthenticated access, authenticated users are redirected away from auth pages, and the token refresh mechanism handles concurrent requests without race conditions
**Depends on**: Phase 1
**Requirements**: AUTH-03, AUTH-04, AUTH-05
**Success Criteria** (what must be TRUE):
  1. Visiting any protected route while logged out redirects to `/login`
  2. Visiting `/login` or `/signup` while logged in redirects to `/dashboard`
  3. After a browser refresh, a valid session is restored automatically (no re-login required)
  4. Concurrent API calls on mount do not trigger multiple simultaneous refresh requests (refresh lock works)
**Plans**: TBD

Plans:
- [ ] 02-01: Implement axios instance with refresh-lock interceptor and token storage
- [ ] 02-02: Implement Zustand tri-state auth store and ProtectedRoute component

### Phase 3: Auth Pages
**Goal**: Users can create an account, log in, recover a forgotten password, and the app correctly surfaces the email verification state — completing the full auth user journey
**Depends on**: Phase 2
**Requirements**: AUTH-01, AUTH-02, AUTH-06, AUTH-07, AUTH-08, AUTH-09
**Success Criteria** (what must be TRUE):
  1. User can submit the login form with email/password and land on the dashboard
  2. User can submit the signup form with business name, email, and password and receive a confirmation
  3. User can log out and is returned to the login page with the session cleared server-side
  4. User can request a password reset email and follow the link to set a new password
  5. An unverified account sees a visible email verification banner on the dashboard
**Plans**: 3 plans

Plans:
- [ ] 03-01: Build Login and Signup pages with form validation
- [ ] 03-02: Build Logout, Forgot Password, and Reset Password flows
- [ ] 03-03: Add email verification banner component

### Phase 4: App Shell + Dashboard Home
**Goal**: Authenticated users land on a dashboard that shows their business context, current plan, and quick-action entry points — all inside a persistent sidebar layout
**Depends on**: Phase 3
**Requirements**: SHELL-01, SHELL-02, SHELL-03, DASH-01, DASH-02, DASH-03
**Success Criteria** (what must be TRUE):
  1. Every page inside the app shows the sidebar with Home, Members, and Knowledge Base navigation links
  2. The sidebar displays the logged-in user's name, role, and business name (from the auth token/store)
  3. The currently active page's nav link is visually highlighted in the sidebar
  4. The dashboard home shows the business name, current plan indicator, and two quick-action links (Invite member, Add KB)
  5. The dashboard shows stat cards for member count and KB count (hardcoded zero for M1)
**Plans**: TBD

Plans:
- [ ] 04-01: Build sidebar layout shell with navigation and user info
- [ ] 04-02: Build Dashboard Home page with stat cards and quick-action links

### Phase 5: Member Management
**Goal**: Admin users can fully manage their team from the dashboard — invite new members, change roles, and remove members — while non-admin users see a read-only view
**Depends on**: Phase 4
**Requirements**: MBR-01, MBR-02, MBR-03, MBR-04, MBR-05, MBR-06
**Success Criteria** (what must be TRUE):
  1. The members page shows a paginated table of members with name, email, role, and status columns
  2. An ADMIN user can open an invite modal, enter an email, and trigger the invite API call
  3. An ADMIN user can remove a member after confirming a confirmation dialog
  4. An ADMIN user can change a member's role via an inline action
  5. A MEMBER role user does not see invite, remove, or role-change controls
  6. The members page shows a clear empty state when the team has no members yet
**Plans**: TBD

Plans:
- [ ] 05-01: Build members table with pagination and status badge
- [ ] 05-02: Build invite modal and role change action
- [ ] 05-03: Build remove confirmation dialog and ADMIN-only visibility gates

### Phase 6: KB Stub + Polish
**Goal**: The knowledge base section is navigable and correctly signals M2 scope, while all auth forms have show/hide password and the overall UI is demo-ready
**Depends on**: Phase 5
**Requirements**: KB-01, KB-02
**Success Criteria** (what must be TRUE):
  1. The Knowledge Base page renders with an empty state — no backend call is made
  2. The "Add Knowledge Base" button is visible but disabled, with a tooltip explaining it is available in M2
  3. Password fields on login and signup forms have a show/hide toggle
**Plans**: TBD

Plans:
- [ ] 06-01: Build static KB empty-state page with disabled add button and tooltip
- [ ] 06-02: Add password show/hide toggle and final UI polish pass

### Phase 7: Deploy + Smoke Test
**Goal**: The admin dashboard is live on Render, SPA routing works on hard refresh, and the full 2-minute demo flow is verified end-to-end
**Depends on**: Phase 6
**Requirements**: None directly — delivery gate that verifies all prior requirements in production
**Success Criteria** (what must be TRUE):
  1. The frontend is deployed to Render as a static site and accessible at its public URL
  2. Hard-refreshing any route (e.g., `/dashboard`, `/members`) returns the app, not a 404
  3. The Render frontend URL is added to the backend's `allowedOrigins` and cross-origin calls succeed
  4. The full demo flow completes without errors: signup → login → dashboard → invite member → logout
**Plans:** 2 plans

Plans:
- [ ] 07-01: Configure render.yaml with SPA rewrite rule and deploy static site
- [ ] 07-02: Add Render URL to backend CORS config and run full smoke test

## Progress

**Execution Order:** 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Scaffold + CORS Verification | v0.2 | 2/2 | Complete   | 2026-05-13 |
| 2. Auth Infrastructure | v0.2 | 0/2 | Not started | - |
| 3. Auth Pages | v0.2 | 0/3 | Not started | - |
| 4. App Shell + Dashboard Home | v0.2 | 0/2 | Not started | - |
| 5. Member Management | v0.2 | 0/3 | Not started | - |
| 6. KB Stub + Polish | v0.2 | 0/2 | Not started | - |
| 7. Deploy + Smoke Test | v0.2 | 0/2 | Not started | - |
