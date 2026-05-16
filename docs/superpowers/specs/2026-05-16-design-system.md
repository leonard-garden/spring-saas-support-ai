# Design System Spec — Support AI

**Date:** 2026-05-16
**Status:** Approved
**Scope:** Frontend design system — colors, typography, components, layout patterns

---

## Direction

**Warm Slate** — dark brown sidebar, amber accent, warm neutral background.

Không phải "AI purple". Tính cách: earthy, professional, approachable. Phù hợp cho B2B SaaS portfolio nhắm vào SMB market. Cảm giác gần Basecamp modern hơn là Vercel.

---

## 1. Color Tokens

### Palette gốc

```
Stone (base neutrals)
  --stone-950:  #1c1917   primary text
  --stone-900:  #292524   sidebar, dark elements
  --stone-700:  #44403c   secondary text
  --stone-500:  #78716c   muted / placeholder
  --stone-300:  #d6d3d1   input border
  --stone-200:  #e7e5e4   card border, divider
  --stone-100:  #f5f5f4   hover background
  --stone-50:   #fafaf9   page background

Amber (primary accent)
  --amber-500:  #f59e0b   CTA button, active state
  --amber-400:  #fbbf24   hover
  --amber-100:  #fef3c7   badge background, highlight
  --amber-800:  #92400e   badge text on amber-100

Base
  --white:      #ffffff   card, form background
```

### Semantic tokens — shadcn CSS variables

Override toàn bộ `:root` trong `index.css`:

```css
:root {
  /* Backgrounds */
  --background:              0 0% 98%;        /* #fafaf9 */
  --card:                    0 0% 100%;
  --popover:                 0 0% 100%;
  --sidebar:                 20 6% 15%;       /* #292524 */

  /* Text */
  --foreground:              20 6% 10%;       /* #1c1917 */
  --card-foreground:         20 6% 10%;
  --popover-foreground:      20 6% 10%;
  --muted-foreground:        25 5% 45%;       /* #78716c */

  /* Borders */
  --border:                  20 6% 90%;       /* #e7e5e4 */
  --input:                   20 5% 83%;       /* #d6d3d1 */

  /* Primary = Amber */
  --primary:                 38 92% 50%;      /* #f59e0b */
  --primary-foreground:      20 6% 15%;       /* #292524 */

  /* Secondary / Muted / Accent */
  --secondary:               20 6% 96%;       /* #f5f5f4 */
  --secondary-foreground:    20 6% 25%;
  --muted:                   20 6% 96%;
  --accent:                  20 6% 96%;
  --accent-foreground:       20 6% 25%;

  /* Destructive */
  --destructive:             0 84% 60%;
  --destructive-foreground:  0 0% 98%;

  /* Ring & Radius */
  --ring:                    38 92% 50%;      /* amber focus ring */
  --radius:                  0.375rem;        /* 6px — crisp */
}
```

**Rule:** Không dùng màu hardcode trong component. Chỉ dùng semantic token (`bg-primary`, `text-muted-foreground`, `border-border`, v.v.). Đổi theme → chỉ sửa `:root`.

---

## 2. Typography

### Fonts

```
Display / Heading: Fraunces
  variable font, opsz 9–144
  weights: 400, 600, 700
  dùng cho: page title, stat number, logo wordmark, auth tagline

Body / UI: DM Sans
  weights: 400, 500, 600
  dùng cho: tất cả còn lại — label, button, input, table, nav
```

Google Fonts import:

```css
@import url("https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,600;9..144,700&family=DM+Sans:wght@400;500;600&display=swap");
```

### Tailwind config

```js
fontFamily: {
  sans: ['DM Sans', 'sans-serif'],
  display: ['Fraunces', 'serif'],
}
```

### Type scale

| Token | Size | Weight | Font | Dùng cho |
|-------|------|--------|------|----------|
| display | 32px | 700 | Fraunces | Auth tagline, landing |
| h1 | 26px | 700 | Fraunces | Page title |
| h2 | 20px | 600 | Fraunces | Section heading |
| h3 | 16px | 600 | Fraunces | Card title, modal title |
| stat | 32px | 700 | Fraunces | StatCard number |
| body | 14px | 400 | DM Sans | Paragraph, description |
| body-sm | 13px | 400 | DM Sans | Form input, table cell |
| small | 12px | 400 | DM Sans | Helper text, meta |
| label | 11px | 600 | DM Sans uppercase | Form label, section tag |
| caption | 10px | 600 | DM Sans uppercase | Badge, chip |

Class convention: `font-display` cho Fraunces, `font-sans` (default) cho DM Sans.

---

## 3. Spacing · Border Radius · Shadows

### Spacing conventions

```
Page padding:     p-6 (24px)
Sidebar padding:  px-3 py-4
Card padding:     p-4 (16px)
Section gap:      space-y-6
Form field gap:   space-y-4
Inline gap:       gap-2 / gap-3
```

Dùng Tailwind scale chuẩn, không custom spacing tokens.

### Border Radius

```css
--radius-xs: 4px    /* badge, chip, tag */
--radius-sm: 6px    /* button, input, dropdown — đây là --radius */
--radius-md: 8px    /* card, table */
--radius-lg: 12px   /* modal, large card */
```

shadcn `--radius` = `0.375rem` (6px). Các component lớn hơn dùng `rounded-xl` (12px).

### Shadows

```css
--shadow-xs:    0 1px 2px rgba(0,0,0,0.05)          /* input focus */
--shadow-sm:    0 1px 4px rgba(0,0,0,0.08)          /* card (không dùng mặc định) */
--shadow-md:    0 4px 12px rgba(0,0,0,0.10)         /* dropdown, popover */
--shadow-lg:    0 8px 24px rgba(0,0,0,0.12)         /* modal dialog */
--shadow-amber: 0 2px 8px rgba(245,158,11,0.20)     /* amber button hover */
```

**Rule:** Card dùng border, không dùng shadow (crisp style). Chỉ dropdown/modal/popover mới dùng shadow.

---

## 4. Component Inventory

### Button

| Variant | Background | Text | Border | Hover |
|---------|-----------|------|--------|-------|
| primary | amber-500 | stone-900 | none | amber-400 + shadow-amber |
| secondary | white | stone-700 | border-input | bg-stone-100 |
| ghost | transparent | stone-500 | none | bg-stone-100 |

Sizes: `sm` (h-8, px-3, 12px) / `md` (h-9, px-4, 13px) / `lg` (h-10, px-5, 14px).
Disabled: opacity-50, cursor-not-allowed. Loading: spinner + disabled state.

### Input / Textarea

```
border:      1.5px solid border-input (#d6d3d1)
border-radius: radius-sm (6px)
bg:          white
text:        14px DM Sans, stone-900
placeholder: stone-400
focus:       border-primary (#f59e0b) + ring 2px amber/20%
error:       border-destructive + FormMessage bên dưới
```

### Card

```
bg:          white
border:      1px solid --border (#e7e5e4)
border-radius: radius-md (8px)
shadow:      none
padding:     p-4 hoặc p-6
```

### Badge / Chip

| Variant | Background | Text | Border | Radius |
|---------|-----------|------|--------|--------|
| default | amber-100 | amber-800 | none | 4px |
| dark | stone-900 | amber-400 | none | 4px |
| outline | white | stone-600 | border-input | 4px |

### Sidebar

```
width:       256px (w-64)
bg:          stone-900 (#292524)

Logo area:
  height:    h-14
  padding:   px-4
  border-b:  border-stone-800
  text:      "Support AI" — Fraunces 16px/700, text-white

Nav link:
  default:   px-3 py-2, rounded-md, text-stone-400, flex items-center gap-3
  hover:     bg-stone-800, text-white
  active:    bg-stone-800, text-white, border-l-2 border-amber-500

Footer:
  border-t:  border-stone-800
  padding:   px-4 py-3
  email:     12px DM Sans, text-stone-300
  role:      caption, text-stone-500
  logout:    ghost button, text-stone-400
```

### StatCard

```
= Card với:
  label:     caption (10px DM Sans uppercase), text-muted-foreground
  value:     stat (32px Fraunces 700), text-foreground
  optional:  trend badge (+12% text-emerald-600)
```

### Auth Layout — Split Panel

```
Container: min-h-screen flex

Left panel (40%):
  bg:        stone-900 (#292524)
  padding:   px-12, flex-col justify-center
  content:
    - Amber bar: w-8 h-1 bg-amber-500 rounded mb-6
    - Tagline:   Fraunces 28px/700, text-white
    - Subtitle:  DM Sans 14px, text-stone-400

Right panel (60%):
  bg:        white
  content:   flex items-center justify-center
    - Form:  max-w-sm w-full, no border/shadow
    - Logo:  "Support AI" Fraunces 18px mb-6
```

### Alert / Banner

```
bg:          amber-100 (#fef3c7)
border-left: 3px solid amber-500
text:        stone-800, DM Sans 13px
icon:        amber-600
dismiss:     ghost icon button
```

### Table

```
thead:     caption, text-muted, border-b border-border
tbody row: border-b border-stone-100, hover:bg-stone-50
cell:      body-sm (13px DM Sans)
action:    justify-end, ghost button + DropdownMenu
```

---

## 5. Implementation Plan

**Nguyên tắc:** Design system sống trong 3 file. Đổi theme → chỉ sửa 3 file, không đụng component.

### Phase 1 — Tokens (~30 phút)
- `index.css`: replace toàn bộ `:root` variables, thêm Google Fonts import
- `tailwind.config.js`: thêm `fontFamily` (sans + display) và `colors.sidebar`

### Phase 2 — Layout (~1 giờ)
- `Sidebar.tsx`: dark bg (`bg-sidebar`), amber active indicator (`border-l-2 border-amber-500`), Fraunces logo text
- `SidebarNavLink.tsx`: update active/hover styles

### Phase 3 — Auth pages (~1 giờ)
- Tạo `AuthLayout.tsx`: split panel component (reusable)
- `LoginPage.tsx`, `SignupPage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`, `AcceptInvitationPage.tsx`: dùng `AuthLayout`

### Phase 4 — Components (~1 giờ)
- `StatCard.tsx`: `font-display` cho number
- `EmailVerificationBanner.tsx`: amber-100 style
- Verify Button/Input/Card: shadcn tự pick từ token — check visual, không rewrite

### Phase 5 — Verify (~30 phút)
- Chạy dev server, walk qua tất cả màn hình hiện có
- Không còn màu hardcode hoặc `Plus Jakarta Sans` reference nào
- Thêm `.superpowers/` vào `.gitignore`

**Tổng ước tính: ~4 giờ**

---

## 6. What NOT to do

- Không hardcode màu trong component (`#f59e0b` trong JSX là sai — dùng `bg-primary`)
- Không rewrite shadcn component internals — chỉ override token
- Không thêm font mới ngoài Fraunces + DM Sans
- Không tạo shadow trên Card — chỉ border
- Không dùng `Inter`, `Plus Jakarta Sans`, hay `system-ui` nữa
