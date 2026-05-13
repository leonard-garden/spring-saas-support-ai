# Requirements: spring-saas-support-ai

**Defined:** 2026-05-13
**Core Value:** A business can sign up, invite their team, and deploy a trained AI chatbot — without writing code.

## v0.2 Requirements — M1 Frontend: Admin Dashboard

### Authentication

- [ ] **AUTH-01**: User can log in with email and password
- [ ] **AUTH-02**: User can sign up with business name, email, and password
- [ ] **AUTH-03**: Unauthenticated users are redirected to login page
- [ ] **AUTH-04**: Authenticated users are redirected away from login/signup
- [ ] **AUTH-05**: User session persists across browser refresh (refresh token flow)
- [ ] **AUTH-06**: User can log out (server-side token revocation + client clear)
- [ ] **AUTH-07**: User can request a password reset via email
- [ ] **AUTH-08**: User can reset password using the link from the email
- [ ] **AUTH-09**: Email verification banner shown if account is unverified

### Shell & Navigation

- [ ] **SHELL-01**: App has a persistent sidebar with Home, Members, Knowledge Base links
- [ ] **SHELL-02**: Sidebar shows logged-in user's name, role, and business name
- [ ] **SHELL-03**: Active nav item is highlighted in sidebar

### Dashboard Home

- [ ] **DASH-01**: Dashboard shows business name and plan indicator
- [ ] **DASH-02**: Dashboard shows quick-action links (Invite member, Add KB)
- [ ] **DASH-03**: Dashboard shows summary stat cards (member count, KB count — hardcoded/zero for M1)

### Member Management

- [ ] **MBR-01**: User can view paginated list of members (name, email, role, status)
- [ ] **MBR-02**: User can invite a new member by email (ADMIN only)
- [ ] **MBR-03**: User can remove a member with a confirmation dialog (ADMIN only)
- [ ] **MBR-04**: User can change a member's role (ADMIN only)
- [ ] **MBR-05**: ADMIN-only actions are hidden from MEMBER role users
- [ ] **MBR-06**: Members page shows empty state when no members exist

### Knowledge Base

- [ ] **KB-01**: Knowledge base page renders with empty state (no backend dependency)
- [ ] **KB-02**: "Add Knowledge Base" button is disabled with tooltip ("Available in M2")

## Future Requirements (v0.3+)

### Knowledge Base (M2)

- **KB-03**: User can create a knowledge base
- **KB-04**: User can upload documents to a knowledge base
- **KB-05**: User can delete a knowledge base

### Chat Widget (M3)

- **CHAT-01**: User can view a preview of the AI chatbot
- **CHAT-02**: User can copy the embeddable widget script tag

### Billing (M4)

- **BILL-01**: User can view current plan and usage
- **BILL-02**: User can upgrade/downgrade plan via Stripe

## Out of Scope

| Feature | Reason |
|---------|--------|
| OAuth / social login | Backend does not implement it — email/password only |
| Dark mode | Time cost without demo value |
| Mobile-first responsive layout | Admin dashboard = desktop-only is acceptable for demo |
| Cypress / Playwright E2E tests | 7-day cap; manual smoke test sufficient |
| Animations / transitions | No demo value; adds bundle cost |
| Analytics charts with real data | No data backend until M3 |
| Multi-language UI | English only per project constraints |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase TBD | Pending |
| AUTH-02 | Phase TBD | Pending |
| AUTH-03 | Phase TBD | Pending |
| AUTH-04 | Phase TBD | Pending |
| AUTH-05 | Phase TBD | Pending |
| AUTH-06 | Phase TBD | Pending |
| AUTH-07 | Phase TBD | Pending |
| AUTH-08 | Phase TBD | Pending |
| AUTH-09 | Phase TBD | Pending |
| SHELL-01 | Phase TBD | Pending |
| SHELL-02 | Phase TBD | Pending |
| SHELL-03 | Phase TBD | Pending |
| DASH-01 | Phase TBD | Pending |
| DASH-02 | Phase TBD | Pending |
| DASH-03 | Phase TBD | Pending |
| MBR-01 | Phase TBD | Pending |
| MBR-02 | Phase TBD | Pending |
| MBR-03 | Phase TBD | Pending |
| MBR-04 | Phase TBD | Pending |
| MBR-05 | Phase TBD | Pending |
| MBR-06 | Phase TBD | Pending |
| KB-01 | Phase TBD | Pending |
| KB-02 | Phase TBD | Pending |

**Coverage:**
- v0.2 requirements: 23 total
- Mapped to phases: 0 (pending roadmap)
- Unmapped: 23 ⚠️

---
*Requirements defined: 2026-05-13*
*Last updated: 2026-05-13 after initial definition*
