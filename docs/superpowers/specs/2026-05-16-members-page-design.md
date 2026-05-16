# Members Page — UI Spec

**Date:** 2026-05-16
**Status:** Approved
**Scope:** Members page redesign — layout, components, interactions
**Design system:** Warm Slate (see `2026-05-16-design-system.md`)

---

## 1. Page Structure

```
Page background: stone-50 (#fafaf9)
Page padding: p-6 (24px)

┌─────────────────────────────────────────────┐
│ Members                    [+ Invite Member] │  ← topbar
│─────────────────────────────────────────────│
│ ⚠ Starter plan · 2 of 3 seats used  Upgrade │  ← plan bar (amber-100)
│─────────────────────────────────────────────│
│ Members (2)   │   Pending (1)                │  ← tabs
│───────────────┴─────────────────────────────│
│  [member cards]                              │  ← tab content
│─────────────────────────────────────────────│
│ 1–2 of 2 members        [← Prev] [1] [Next→]│  ← pagination
└─────────────────────────────────────────────┘
```

---

## 2. Topbar

```
Layout:    flex justify-between items-end
Title:     "Members" — Fraunces 26px/700, text-stone-950
Button:    "+ Invite Member" — primary button (amber-500, stone-900 text)
           size: md (h-9 px-4)
```

---

## 3. Plan Bar

Shown always. Updates based on active subscription.

```
bg:         amber-100 (#fef3c7)
border:     1px solid #fde68a
border-radius: radius-sm (6px)
padding:    py-2 px-3
margin-bottom: mb-4

Left text:  "{plan name} plan · {used} of {max} seats used"
            DM Sans 13px, text-amber-800
Right link: "Upgrade →"
            DM Sans 13px font-semibold, text-amber-800, cursor-pointer
            → navigates to /settings/billing
```

**Variant — at limit (used === max):**
```
Left text:  "{plan name} plan · Seat limit reached"
border:     1px solid #fca5a5 (red-300)
bg:         #fef2f2 (red-50)
text:       text-red-700
```

---

## 4. Tabs

```
Tab bar:    flex, border-b border-stone-200, mb-3
Tab item:   px-3 py-2, text-sm font-medium, cursor-pointer

States:
  default:  text-stone-500
  active:   text-stone-950 font-semibold, border-b-2 border-amber-500, mb-[-1px]

Tab count badge:
  active tab:   bg-amber-100 text-amber-800, rounded-full px-2 py-0.5, text-[10px] font-bold
  inactive tab: bg-stone-100 text-stone-500
  pending tab (any state): bg-violet-100 text-violet-700
```

Two tabs:
- **Members** — shows active/accepted members
- **Pending** — shows sent invitations not yet accepted

---

## 5. Members Tab

### Member Card

```
bg:           white
border:       1px solid border-border (#e7e5e4)
border-radius: radius-md (8px)
padding:      py-2.5 px-3.5
layout:       flex items-center gap-2.5
margin-bottom: mb-1.5

Avatar:
  size:     28×28px, rounded-full
  Owner:    bg-amber-100 text-amber-800
  Member:   bg-stone-200 text-stone-600
  initials: first letter of email (uppercase)

Info block (flex-1):
  Email:    text-[11px] font-semibold text-stone-900, truncate
  Date:     "Joined {date}" — text-[9px] text-stone-400, mt-0.5

Right block:
  Role badge + Status badge + ⋮ button
  gap: gap-1.5, items-center
```

### Badges

| Badge | bg | text |
|---|---|---|
| Owner | amber-100 | amber-800 |
| Member | stone-100 | stone-500 |
| Active | green-100 | green-700 |

### ⋮ Action Menu (DropdownMenu)

Trigger: ghost icon button, color stone-300 → stone-500 on hover.

**For Member role:**
```
[Actions header — section label]
  Change Role    → opens Change Role sub-menu / inline select
─────────────────
  Remove Member  → text-red-600, hover:bg-red-50
                   triggers confirm dialog before action
```

**For Owner row (current user):**
```
  Change Role    → disabled, opacity-50, cursor-not-allowed
─────────────────
  Remove Member  → disabled, opacity-50, cursor-not-allowed
```
Tooltip on hover: "Owner cannot be removed or reassigned."

### Change Role

Inline: clicking "Change Role" opens a small popover with two options:
```
○ Member   — standard access
○ Admin    — full access except billing
```
Confirm button "Save" — primary sm. Cancel closes without saving.

---

## 6. Pending Tab

### Pending Card

```
bg:     white
border: 1px solid #e7e5e4
border-radius: 8px
padding: py-2.5 px-3.5
layout: flex items-center gap-2.5
margin-bottom: mb-1.5

Avatar:
  bg: violet-100, text-violet-600
  letter: "?"

Info block:
  Email:  text-[11px] font-semibold text-stone-900, truncate
  Sent:   "Sent {date} · expires in {N} days" — text-[9px] text-stone-400

Actions (right):
  Resend button: bg-amber-100 border-amber-200 text-amber-800, text-[10px] font-semibold
  Revoke button: bg-white border-stone-200 text-stone-500, text-[10px] font-semibold
```

**Resend** — calls `POST /invitations/{id}/resend`, resets expiry to 7 days, shows success toast.  
**Revoke** — calls `DELETE /invitations/{id}`, removes card from list, shows success toast.

---

## 7. Pagination

Applied to both tabs independently. Each tab tracks its own page state.

```
Layout:  flex justify-between items-center
         pt-3 mt-3 border-t border-stone-100

Left:    "{start}–{end} of {total} members" — text-[10px] text-stone-400

Right:   Prev button + page number buttons + Next button
         Page size: 10 per page

Button styles:
  default: border border-stone-200 bg-white text-stone-600, rounded-md px-2 py-1, text-[10px]
  active:  bg-amber-500 border-amber-500 text-stone-900 font-semibold
  disabled: text-stone-300 bg-stone-50 cursor-default
```

Show page number buttons only when total pages > 1.  
Show at most 5 page buttons; truncate with "…" if more.

---

## 8. Empty States

### Members tab — no members (only owner)

```
Icon:    👥 (32px)
Title:   "No team members yet" — Fraunces 15px/700, text-stone-900
Body:    "Invite your team to collaborate." — DM Sans 13px, text-stone-400
CTA:     "+ Invite Member" — primary button md
```

### Pending tab — no pending invites

```
Icon:    ✉️ (32px)
Title:   "No pending invitations" — Fraunces 15px/700
Body:    "Invitations you send will appear here." — DM Sans 13px, text-stone-400
No CTA  (use the top-right button instead)
```

---

## 9. Invite Member Modal

Triggered by "+ Invite Member" button. Centered dialog, max-w-md.

```
Header:
  Title:    "Invite a Member" — Fraunces 18px/700, text-stone-950
  Subtitle: "They'll receive an email with an invite link."
             DM Sans 13px, text-stone-400
  Close:    ✕ icon button top-right, ghost

Form:
  Email field:
    label:       "Email address" — form label (11px uppercase)
    input:       type=email, placeholder "colleague@company.com"
    validation:  required, valid email format

  Role select:
    label:       "Role"
    options:     Member (default), Admin
    select:      standard DS select style

Footer:
  Cancel — secondary button
  Send Invite → — primary button
  Layout: justify-end gap-2
```

**On submit:**
- Calls `POST /invitations` with `{ email, role }`
- On success: close modal, switch to Pending tab, show toast "Invite sent to {email}"
- On error (email already member): inline field error "This email is already a member"
- On error (seat limit): inline error "Seat limit reached. Upgrade your plan."

---

## 10. Toasts

All actions trigger a toast (top-right, 3s auto-dismiss):

| Action | Toast |
|---|---|
| Invite sent | "Invite sent to {email}" — neutral |
| Invite resent | "Invite resent to {email}" — neutral |
| Invite revoked | "Invitation revoked" — neutral |
| Member removed | "{email} has been removed" — neutral |
| Role changed | "Role updated to {role}" — neutral |
| Error | Red destructive toast with message |

---

## 11. API Mapping

All paths prefixed with `/api/v1`.

| UI Action | Endpoint | Status |
|---|---|---|
| Load members | `GET /members?page={n}&size=10` | ✅ exists |
| Invite member | `POST /members/invite` `{ email, role }` | ✅ exists |
| Change role | `PATCH /members/{id}/role` `{ role }` | ✅ exists |
| Remove member | `DELETE /members/{id}` | ✅ exists |
| Load pending | `GET /invitations?status=PENDING&page={n}&size=10` | ❌ needs adding |
| Resend invite | `POST /invitations/{id}/resend` | ❌ needs adding |
| Revoke invite | `DELETE /invitations/{id}` | ❌ needs adding |

**Backend work required:** Add 3 endpoints to `InvitationController` before Pending tab can be wired up. Frontend can build the tab UI first with mock data, then wire when backend is ready.

---

## 12. What NOT to do

- Không hardcode màu — dùng design system tokens
- Không dùng table element — dùng card list (flex rows)
- Không show ⋮ actions cho Owner row (disable, không ẩn)
- Không tự navigate sang trang khác khi đổi tab — tab switch là client-side
- Không pagination nếu total ≤ 10 (ẩn pagination bar)
