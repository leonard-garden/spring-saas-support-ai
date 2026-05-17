# Feature Landscape: React Admin Dashboard

**Domain:** React admin dashboard — SaaS AI support platform (demo/portfolio)
**Researched:** 2026-05-13
**Scope:** Auth flow UX, dashboard home, member management, knowledge base stub

---

## Section 1: Auth Flow

### Table Stakes

| Feature | Complexity | Backend Endpoint |
|---------|------------|-----------------|
| Login form (email + password) | Low | `POST /api/v1/auth/login` |
| Signup form (business name + email + password) | Low | `POST /api/v1/auth/signup` |
| Persist session across page refreshes | Low | `POST /api/v1/auth/refresh` |
| Logout button | Low | `POST /api/v1/auth/logout` |
| Redirect unauthenticated users to login | Low | n/a (frontend routing) |
| Redirect authenticated users away from login/signup | Low | n/a |
| Inline field validation errors | Low | 422 ProblemDetail |
| Forgot password page | Low | `POST /api/v1/auth/forgot-password` |
| Reset password page (token in URL) | Low | `POST /api/v1/auth/reset-password` |
| Email verification notice/banner | Low | JWT claim `emailVerified` |

### Differentiators

| Feature | Value | Complexity |
|---------|-------|------------|
| Show/hide password toggle | Eye icon — 10 lines, high perceived polish | Low |
| "Remember me" checkbox | UX affordance | Low |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| OAuth / social login | Backend does not implement it |
| Multi-step onboarding wizard | Over-engineering for demo |
| CAPTCHA | Adds friction, no bot threat on demo |
| Magic link login | Backend does not support it |

---

## Section 2: Dashboard Home

### Table Stakes

| Feature | Complexity | Source |
|---------|------------|--------|
| Tenant/business name in header | Low | JWT claim or `GET /api/v1/auth/me` |
| Logged-in user name + role | Low | JWT claims |
| Navigation sidebar (Home, Members, KB) | Low | n/a |
| Current plan indicator | Low | JWT claims or profile endpoint |
| Quick-action cards/links | Low | n/a |
| Responsive layout (desktop + tablet) | Medium | n/a |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| Stat summary cards (member count, KB count) | Makes dashboard feel alive | Hardcode/zero for M1 — wire in M3 |
| Trial countdown banner | Shows full SaaS funnel in demo | Derived from plan info |
| Empty-state guidance per section | Reduces confusion on first login | n/a |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Analytics charts/graphs | No data backend yet |
| Activity feed / audit log timeline | No audit-read endpoint |
| Notification bell with real events | No notification backend |
| Dark mode toggle | Time cost without demo value |

---

## Section 3: Member Management

### Table Stakes

| Feature | Complexity | Backend Endpoint |
|---------|------------|-----------------|
| Members table (name, email, role, joined, status) | Low | `GET /api/v1/members` |
| Pagination | Low | `?page=0&size=20` |
| Invite member → modal with email input | Low | `POST /api/v1/members/invite` |
| Remove member with confirmation dialog | Low | `DELETE /api/v1/members/{id}` |
| Change member role (dropdown) | Low | `PATCH /api/v1/members/{id}/role` |
| Role-based visibility (ADMIN-only actions) | Low | JWT role claim |
| Empty state | Low | n/a |
| Error toast on failed operations | Low | Map ProblemDetail |

### Differentiators

| Feature | Value | Notes |
|---------|-------|-------|
| Member status badge (Active / Pending) | Shows full invite flow visible in one screen | Confirm backend returns `status` field |
| Client-side search/filter by name or email | Signals polish | Filter fetched list in state — no backend search needed |
| Current user row highlighted | "You are here" affordance | Match email against JWT claim |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Bulk select + bulk delete | Adds UI complexity; no real need |
| Member detail/profile page | No backend profile detail endpoint |
| Permission matrix / fine-grained roles | Backend only has ADMIN / MEMBER |
| CSV export | No backend support |

---

## Section 4: Knowledge Base (Stub)

### Table Stakes

| Feature | Complexity | Notes |
|---------|------------|-------|
| KB list page that loads without error | Low | Static empty state — no backend dependency |
| Empty state with clear explanation | Low | "Knowledge bases let your chatbot answer questions. Coming soon." |
| "Add Knowledge Base" button (disabled + tooltip) | Low | Disabled with "Available in next update" |
| Navigation link in sidebar | Low | Link present, page renders |

### Differentiators

| Feature | Value |
|---------|-------|
| Placeholder card UI with icon | Looks designed vs bare empty text |
| "View roadmap" link | Signals SaaS product thinking |

### Anti-Features

| Anti-Feature | Why Avoid |
|--------------|-----------|
| Stub CRUD forms without backend | Misleading; breaks demo trust |
| Mock/fake KB data | Be honest: empty state |
| Document upload UI stub | Reserve entirely for M2 |

---

## MVP Prioritization

**Must ship:**
1. Login + signup + auth guard
2. Dashboard home with sidebar, user info, quick-action links
3. Member table with invite, remove, role change
4. KB stub page (static empty state)

**Ship if time remains:**
5. Forgot password + reset password (backend exists, low effort)
6. Member status badge
7. Email verification banner

**Defer:**
- Real data in stats cards (wire in M3)
- Trial countdown
- Client-side member search

---

## Backend Endpoint Reference

| Feature | Method | Endpoint | Auth |
|---------|--------|----------|------|
| Login | POST | `/api/v1/auth/login` | No |
| Signup | POST | `/api/v1/auth/signup` | No |
| Logout | POST | `/api/v1/auth/logout` | Yes |
| Refresh | POST | `/api/v1/auth/refresh` | Refresh token |
| Forgot password | POST | `/api/v1/auth/forgot-password` | No |
| Reset password | POST | `/api/v1/auth/reset-password` | No |
| Verify email | POST | `/api/v1/auth/verify-email` | No |
| Current user | GET | `/api/v1/auth/me` | Yes |
| List members | GET | `/api/v1/members` | Yes |
| Invite member | POST | `/api/v1/members/invite` | Yes (ADMIN) |
| Remove member | DELETE | `/api/v1/members/{id}` | Yes (ADMIN) |
| Update role | PATCH | `/api/v1/members/{id}/role` | Yes (ADMIN) |
| Accept invite | POST | `/api/v1/invitations/accept` | No |
| KB endpoints | — | Not implemented until M2 | — |
