# Phase 5: Member Management - Research

**Researched:** 2026-05-15
**Domain:** React + TanStack Query + shadcn/ui — data table, modal, dialog, role-gating
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MBR-01 | User can view paginated list of members (name, email, role, status) | `GET /api/v1/members` returns `MemberResponse[]`; no server-side pagination — client-side pagination with slice |
| MBR-02 | User can invite a new member by email (ADMIN only) | `POST /api/v1/members/invite` with `{ email, role }` — 202 Accepted |
| MBR-03 | User can remove a member with a confirmation dialog (ADMIN only) | `DELETE /api/v1/members/{id}` — 204 No Content; OWNER-only on backend |
| MBR-04 | User can change a member's role (ADMIN only) | `PATCH /api/v1/members/{id}/role` with `{ role }` — OWNER-only on backend |
| MBR-05 | ADMIN-only actions are hidden from MEMBER role users | Role from `useAuthStore` — conditional render based on `user.role` |
| MBR-06 | Members page shows empty state when no members exist | Conditional render when `members.length === 0` |
</phase_requirements>

---

## Summary

Phase 5 builds the Members page on top of a fully wired backend (v0.1). The backend exposes four REST endpoints under `/api/v1/members`. The React app already has the `api` axios instance, `useAuthStore` with `user.role`, TanStack Query v5, react-hook-form, zod, and shadcn/ui installed. The missing pieces are: (1) shadcn components not yet added (`table`, `dialog`, `select`, `badge`, `dropdown-menu`), (2) a `memberApi.ts` service module, (3) the `MembersPage` itself which is currently a one-line stub, and (4) sub-components for the invite modal, remove confirm dialog, and role change dropdown.

**CRITICAL backend contract discovery:** `MemberResponse` does NOT have a `status` field. It has `id`, `email`, `role`, `createdAt`. STATE.md flags "Confirm `status` field exists on `MemberResponse` record" as a pending concern — the answer is: it does NOT exist. The `status` column required by MBR-01 must be derived from the invitation flow or omitted. Plan must resolve this: display the `createdAt` timestamp instead of a status badge, OR mark all listed members as "Active" (since the list endpoint only returns accepted members, not pending invitations).

**CRITICAL role gate discovery:** The backend uses three roles: `OWNER`, `ADMIN`, `MEMBER`. The frontend `types/auth.ts` currently declares `Role = "OWNER" | "ADMIN" | "AGENT"` — `AGENT` is wrong, it should be `MEMBER`. The plan must include a type fix. Backend `MemberController` uses `requireAdminOrOwner()` for GET (list/get) and `requireOwner()` for DELETE and PATCH role — meaning only OWNER can remove or change roles. The ADMIN can list but cannot mutate. Plan must surface this discrepancy: the UI requirement says "ADMIN only" but the backend requires OWNER. For M1, gate mutation actions to `OWNER` role (not ADMIN) to match backend behavior.

**Primary recommendation:** Build `memberApi.ts` first, then three plans: (1) members table + empty state + pagination, (2) invite modal with zod-validated form, (3) remove confirm dialog + role dropdown + OWNER-only visibility gating.

---

## Backend API Contract (HIGH confidence — read from source)

### Endpoints

| Method | Path | Auth Required | Role | Request Body | Response | Notes |
|--------|------|---------------|------|--------------|----------|-------|
| GET | `/api/v1/members` | Bearer | ADMIN or OWNER | none | `ApiResponse<MemberResponse[]>` | Returns all tenant members |
| GET | `/api/v1/members/{id}` | Bearer | ADMIN or OWNER | none | `ApiResponse<MemberResponse>` | Not needed for Phase 5 UI |
| DELETE | `/api/v1/members/{id}` | Bearer | OWNER only | none | 204 No Content (no body) | Cannot remove self |
| PATCH | `/api/v1/members/{id}/role` | Bearer | OWNER only | `{ role: Role }` | `ApiResponse<MemberResponse>` | Cannot assign OWNER role |
| POST | `/api/v1/members/invite` | Bearer | (any authenticated) | `{ email, role }` | `ApiResponse<InvitationResponse>` 202 | Conflict → 409 |

### MemberResponse shape (read from Java record)
```typescript
interface MemberResponse {
  id: string          // UUID
  email: string
  role: string        // "OWNER" | "ADMIN" | "MEMBER"
  createdAt: string   // ISO 8601 Instant — NO status field
}
```

### InvitationResponse shape
```typescript
interface InvitationResponse {
  id: string
  email: string
  role: string
  expiresAt: string
  createdAt: string
}
```

### InviteRequest shape
```typescript
interface InviteRequest {
  email: string   // @NotBlank @Email
  role: Role      // @NotNull — enum value
}
```

### UpdateRoleRequest shape
```typescript
interface UpdateRoleRequest {
  role: Role   // @NotNull
}
```

---

## Standard Stack

### Core (already installed — no new npm installs needed)
| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| @tanstack/react-query | ^5.100.10 | Server state, loading/error, cache invalidation | Use `useQuery` + `useMutation` |
| react-hook-form | ^7.75.0 | Invite modal form | Already used in auth pages |
| zod | ^3.25.76 | Invite form schema validation | Already used |
| zustand | ^5.0.13 | Read `user.role` for gating | `useAuthStore` already wired |
| axios (via `api`) | ^1.16.0 | HTTP calls | Use `api` from `@/lib/api` |
| lucide-react | ^1.14.0 | Icons (UserPlus, Trash2, ChevronDown) | Already installed |

### shadcn Components (need to be added via CLI)
| Component | Purpose | Install Command |
|-----------|---------|-----------------|
| table | Members data table | `npx shadcn@2.3.0 add table` |
| dialog | Invite modal + remove confirm | `npx shadcn@2.3.0 add dialog` |
| select | Role picker in invite form | `npx shadcn@2.3.0 add select` |
| badge | Role and status display | `npx shadcn@2.3.0 add badge` |
| dropdown-menu | Inline role change action | `npx shadcn@2.3.0 add dropdown-menu` |

Note: shadcn is installed as a devDependency (`shadcn@2.3.0`) per Phase 1 decision. Use `npx shadcn@2.3.0 add <component>` from the `frontend/` directory.

### Alternatives Not Needed
- TanStack Table: overkill for this page size — plain `<table>` with shadcn table primitives + JS `.slice()` for client-side pagination is sufficient given the small member count expected in M1.
- React Query pagination with server cursor: backend returns a flat array, no cursor or page params — client-side pagination only.

---

## Architecture Patterns

### Recommended File Structure for Phase 5
```
frontend/src/
├── lib/
│   └── memberApi.ts              # API calls: listMembers, inviteMember, removeMember, changeRole
├── types/
│   └── member.ts                 # MemberResponse, InviteRequest, UpdateRoleRequest types
├── pages/
│   └── MembersPage.tsx           # Replaces current stub — composes sub-components
├── components/
│   └── members/
│       ├── MembersTable.tsx      # Table with pagination, role badge, action column
│       ├── MembersEmptyState.tsx # Empty state illustration + text
│       ├── InviteModal.tsx       # Dialog with react-hook-form + zod
│       └── RemoveConfirmDialog.tsx  # AlertDialog: "Remove [name]?" confirm/cancel
└── components/ui/
    ├── table.tsx                 # shadcn add table
    ├── dialog.tsx                # shadcn add dialog
    ├── select.tsx                # shadcn add select
    ├── badge.tsx                 # shadcn add badge
    └── dropdown-menu.tsx         # shadcn add dropdown-menu
```

### Pattern 1: TanStack Query for Members List
```typescript
// memberApi.ts
import { api } from "@/lib/api"
import type { ApiResponse } from "@/types/auth"
import type { MemberResponse, InviteRequest, UpdateRoleRequest } from "@/types/member"

export async function listMembers(): Promise<MemberResponse[]> {
  const { data: envelope } = await api.get<ApiResponse<MemberResponse[]>>("/members")
  return envelope.data ?? []
}

// MembersPage.tsx
const { data: members = [], isLoading } = useQuery({
  queryKey: ["members"],
  queryFn: listMembers,
})
```

### Pattern 2: useMutation with Cache Invalidation
```typescript
const queryClient = useQueryClient()

const inviteMutation = useMutation({
  mutationFn: (req: InviteRequest) => inviteMember(req),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ["members"] })
    setInviteOpen(false)
  },
})

const removeMutation = useMutation({
  mutationFn: (id: string) => removeMember(id),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ["members"] })
    setRemoveTarget(null)
  },
})
```

### Pattern 3: Role Gating (matches backend OWNER-only for mutations)
```typescript
// Read from auth store — same token-decoded user available across all pages
const user = useAuthStore((s) => s.user)
const canMutate = user?.role === "OWNER"

// Conditional render — not conditional disable — per MBR-05
{canMutate && (
  <Button onClick={() => setInviteOpen(true)}>Invite Member</Button>
)}
```

### Pattern 4: Client-Side Pagination
```typescript
const PAGE_SIZE = 10
const [page, setPage] = useState(0)
const pageItems = members.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
const totalPages = Math.ceil(members.length / PAGE_SIZE)
```

### Pattern 5: Zod Schema for Invite Form
```typescript
const inviteSchema = z.object({
  email: z.string().email("Valid email required"),
  role: z.enum(["ADMIN", "MEMBER"]),  // Cannot invite OWNER
})
type InviteFormValues = z.infer<typeof inviteSchema>
```

### Anti-Patterns to Avoid
- **Don't use AGENT in the Role type**: Backend uses `OWNER | ADMIN | MEMBER`. Current `types/auth.ts` has `AGENT` — must be corrected to `MEMBER`.
- **Don't disable actions for non-OWNER, hide them**: The requirement says actions are "hidden" not "disabled" for non-admin users (MBR-05).
- **Don't call the members list endpoint from MEMBER-role users**: The backend will return 403. The page should either not render mutation controls or should not make the call if the user lacks the role. But for M1, the members page is accessible to all authenticated users — just mutation controls are hidden.
- **Don't assume `status` field exists**: It doesn't. Show `createdAt` as "Joined" date or show role as status indicator.
- **Don't use `api` base axios for refresh-sensitive paths**: All member API calls go through `@/lib/api` (not bare axios) so the refresh interceptor handles 401s automatically.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Modal/dialog | Custom div + backdrop | shadcn `Dialog` | Focus trap, ESC key, a11y, portal rendering |
| Confirm dialog | Custom alert box | shadcn `AlertDialog` (part of dialog component) | Accessible, matches existing UI style |
| Role select | Custom dropdown | shadcn `Select` | Keyboard nav, a11y, consistent styling |
| Status badge | Custom styled span | shadcn `Badge` | Consistent variant API (default/outline/destructive) |
| Form | Uncontrolled inputs | react-hook-form + zod | Already used in auth pages — consistency |
| Table markup | Raw `<table>` | shadcn `Table` primitives | Consistent borders, hover states, responsive styles |

---

## Critical Discoveries

### 1. `status` Field Does Not Exist on MemberResponse
STATE.md concern "Confirm `status` field exists on `MemberResponse` record" — confirmed: it does NOT exist. MemberResponse has only `id`, `email`, `role`, `createdAt`.

**Resolution for plan:** The "status" column in MBR-01 should be implemented as either:
- Option A (recommended): Show "Active" for all members (list endpoint only returns accepted members) + derive "Pending" from a separate invitation list if desired — but that requires a new endpoint not currently exposed.
- Option B: Replace "status" column with "Joined" (formatted `createdAt`).

Recommend Option A with hardcoded "Active" badge. The invite flow creates a pending invitation but the `/members` list only shows accepted members. This is correct behavior.

### 2. Role Enum Mismatch: `AGENT` vs `MEMBER`
- Backend Java enum: `OWNER`, `ADMIN`, `MEMBER`
- Frontend `types/auth.ts`: `type Role = "OWNER" | "ADMIN" | "AGENT"` — **AGENT is wrong**
- Fix required in `types/auth.ts` before or during Phase 5, Plan 1

### 3. Backend Requires OWNER (not ADMIN) for Mutations
- `DELETE /members/{id}` — `requireOwner()` on backend
- `PATCH /members/{id}/role` — `requireOwner()` on backend
- `POST /members/invite` — no role check on backend (any authenticated user can invite)

The ROADMAP says "ADMIN only" for MBR-02/03/04, but backend enforces OWNER for remove and role change. Frontend gating should match backend: show remove/role-change only for OWNER; show invite for OWNER and ADMIN (or all roles — backend won't reject it).

**Safe approach for M1:** Gate all mutation controls to `role === "OWNER"` to avoid 403s. Document this in plan.

### 4. InviteRequest requires `role` field
The invite API requires both `email` AND `role`. The invite form must include a role selector (ADMIN or MEMBER — not OWNER).

---

## Common Pitfalls

### Pitfall 1: Stale Member List After Mutation
**What goes wrong:** After invite/remove/role-change, the table still shows old data.
**Why it happens:** TanStack Query caches the `["members"]` query.
**How to avoid:** Always call `queryClient.invalidateQueries({ queryKey: ["members"] })` in every `onSuccess` handler.

### Pitfall 2: Role Check on Non-Existent `AGENT` Role
**What goes wrong:** `user.role === "MEMBER"` never matches if auth type says `"AGENT"`.
**How to avoid:** Fix `types/auth.ts` `Role` type first. Then check `user.role === "OWNER"`.

### Pitfall 3: DELETE Returns No Body — Don't Try to Parse It
**What goes wrong:** `api.delete()` returns 204 with empty body. Attempting to access `response.data.data` throws.
**How to avoid:**
```typescript
export async function removeMember(id: string): Promise<void> {
  await api.delete(`/members/${id}`)
  // return nothing — 204 has no body
}
```

### Pitfall 4: shadcn add Failing Due to npx Version Hook
**What goes wrong:** Phase 1 found that `npx shadcn@latest add` triggers a hook restriction.
**How to avoid:** Use pinned version: `npx shadcn@2.3.0 add <component>` from `frontend/` directory.

### Pitfall 5: Self-Removal
**What goes wrong:** The backend throws `SelfModificationException` if OWNER tries to remove themselves.
**How to avoid:** Hide the remove button on the row where `member.id === user.id`.

### Pitfall 6: Role Badge Showing Raw Enum String
**What goes wrong:** Badge shows "OWNER" "ADMIN" "MEMBER" — looks like debug output.
**How to avoid:** Map to display labels:
```typescript
const ROLE_LABEL: Record<string, string> = { OWNER: "Owner", ADMIN: "Admin", MEMBER: "Member" }
```

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 2.1.9 + @testing-library/react 16.3.2 |
| Config file | `frontend/vite.config.ts` (vitest config inline) |
| Quick run command | `cd frontend && npm test -- --run` |
| Full suite command | `cd frontend && npm test -- --run --coverage` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | File |
|--------|----------|-----------|------|
| MBR-01 | Table renders member rows with email, role, createdAt | unit | `MembersPage.test.tsx` |
| MBR-01 | Pagination shows next/prev controls when > 10 members | unit | `MembersPage.test.tsx` |
| MBR-02 | Invite button opens modal (OWNER user) | unit | `MembersPage.test.tsx` |
| MBR-02 | Invite form submits with email + role | unit | `InviteModal.test.tsx` |
| MBR-03 | Remove button opens confirm dialog | unit | `MembersPage.test.tsx` |
| MBR-03 | Confirm dialog calls removeMember API | unit | `RemoveConfirmDialog.test.tsx` |
| MBR-04 | Role dropdown calls changeRole API | unit | `MembersTable.test.tsx` |
| MBR-05 | MEMBER role user does not see invite/remove/role-change | unit | `MembersPage.test.tsx` |
| MBR-06 | Empty state renders when members array is empty | unit | `MembersPage.test.tsx` |

### Sampling Rate
- **Per task commit:** `cd frontend && npm test -- --run`
- **Per wave merge:** `cd frontend && npm test -- --run --coverage`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `frontend/src/pages/MembersPage.test.tsx` — covers MBR-01, MBR-02, MBR-03, MBR-05, MBR-06
- [ ] `frontend/src/components/members/InviteModal.test.tsx` — covers MBR-02 form submit
- [ ] `frontend/src/components/members/RemoveConfirmDialog.test.tsx` — covers MBR-03 confirm

---

## Code Examples

### memberApi.ts (full service module)
```typescript
// Source: backend contracts read directly from Java controllers
import { api } from "@/lib/api"
import type { ApiResponse } from "@/types/auth"
import type { MemberResponse, InviteRequest, InviteResponse, UpdateRoleRequest } from "@/types/member"

export async function listMembers(): Promise<MemberResponse[]> {
  const { data } = await api.get<ApiResponse<MemberResponse[]>>("/members")
  return data.data ?? []
}

export async function inviteMember(req: InviteRequest): Promise<InviteResponse> {
  const { data } = await api.post<ApiResponse<InviteResponse>>("/members/invite", req)
  return data.data!
}

export async function removeMember(id: string): Promise<void> {
  await api.delete(`/members/${id}`)
}

export async function changeRole(id: string, req: UpdateRoleRequest): Promise<MemberResponse> {
  const { data } = await api.patch<ApiResponse<MemberResponse>>(`/members/${id}/role`, req)
  return data.data!
}
```

### types/member.ts
```typescript
export interface MemberResponse {
  id: string
  email: string
  role: string        // "OWNER" | "ADMIN" | "MEMBER"
  createdAt: string   // ISO 8601
}

export interface InviteRequest {
  email: string
  role: "ADMIN" | "MEMBER"  // Cannot invite OWNER
}

export interface InviteResponse {
  id: string
  email: string
  role: string
  expiresAt: string
  createdAt: string
}

export interface UpdateRoleRequest {
  role: "ADMIN" | "MEMBER"  // Cannot assign OWNER via API
}
```

### Role type fix in types/auth.ts
```typescript
// Change: type Role = "OWNER" | "ADMIN" | "AGENT"
// To:
export type Role = "OWNER" | "ADMIN" | "MEMBER"
```

### MembersPage skeleton
```typescript
export function MembersPage() {
  const user = useAuthStore((s) => s.user)
  const canMutate = user?.role === "OWNER"
  const { data: members = [], isLoading } = useQuery({
    queryKey: ["members"],
    queryFn: listMembers,
  })
  // ...
  if (members.length === 0 && !isLoading) return <MembersEmptyState />
  return (
    <div className="space-y-4">
      {canMutate && <Button onClick={() => setInviteOpen(true)}>Invite Member</Button>}
      <MembersTable members={pageItems} canMutate={canMutate} onRemove={...} onRoleChange={...} />
      {/* pagination controls */}
      <InviteModal open={inviteOpen} onOpenChange={setInviteOpen} />
      <RemoveConfirmDialog target={removeTarget} onClose={() => setRemoveTarget(null)} />
    </div>
  )
}
```

---

## Open Questions

1. **Should ADMIN users see the members list at all?**
   - What we know: Backend `requireAdminOrOwner()` on GET — so ADMIN can list.
   - What's unclear: Should ADMIN see a read-only table with no action controls? MBR-05 says "MEMBER role users do not see controls" — implying ADMIN might see controls.
   - Recommendation: Show table to all authenticated users. Show mutation controls only to OWNER (to match backend). Document in plan.

2. **Should pending invitations appear in the members table?**
   - What we know: `GET /members` only returns accepted members. Pending invitations are tracked separately but no list-invitations endpoint is exposed.
   - What's unclear: MBR-01 mentions "status" column implying pending might show.
   - Recommendation: Hardcode "Active" for all rows in members list. Pending invitations are not surfaced in M1.

---

## Sources

### Primary (HIGH confidence)
- `/src/main/java/.../member/MemberController.java` — all endpoints, role guards, HTTP methods
- `/src/main/java/.../member/MemberResponse.java` — confirmed field set (no `status`)
- `/src/main/java/.../member/Role.java` — confirmed enum values: OWNER, ADMIN, MEMBER
- `/src/main/java/.../invitation/InvitationController.java` — invite endpoint path and response code
- `/src/main/java/.../invitation/InviteRequest.java` — invite request shape with role field
- `/src/main/java/.../invitation/InvitationResponse.java` — invite response shape
- `/src/main/java/.../member/UpdateRoleRequest.java` — role change body shape
- `frontend/src/store/authStore.ts` — auth state shape, how to read user.role
- `frontend/src/lib/api.ts` — axios instance, interceptors, base URL pattern
- `frontend/src/lib/authApi.ts` — existing API call pattern to replicate
- `frontend/package.json` — confirmed all dependencies available, shadcn@2.3.0

### Secondary (MEDIUM confidence)
- `.planning/STATE.md` — confirmed pending concern about `status` field (now resolved)
- `.planning/REQUIREMENTS.md` — MBR-01 through MBR-06 specifications
- `frontend/src/types/auth.ts` — confirmed Role type bug (`AGENT` should be `MEMBER`)

---

## Metadata

**Confidence breakdown:**
- Backend API contracts: HIGH — read directly from Java source files
- Frontend stack: HIGH — read from package.json and existing source files
- shadcn install commands: HIGH — matches Phase 1 decisions in STATE.md
- Role discrepancy (AGENT vs MEMBER): HIGH — confirmed in both Java enum and TS type
- `status` field absence: HIGH — confirmed by reading MemberResponse Java record

**Research date:** 2026-05-15
**Valid until:** 2026-06-15 (stable stack — no moving parts)
