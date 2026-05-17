# Design System: spring-saas-support-ai Admin Dashboard

**Aesthetic:** Warm Precision — refined SaaS tool aesthetic. Clean zinc neutrals, deep violet accent, sidebar-first layout.
**Theme:** Light mode (dark sidebar)
**Typography:** Plus Jakarta Sans (headings + body) — humanist geometric, distinctive without being loud
**Stack:** React + Tailwind CSS 3 + shadcn/ui

---

## Colors

### Primary — Violet
```css
--primary-50:  #F5F3FF
--primary-100: #EDE9FE
--primary-200: #DDD6FE
--primary-300: #C4B5FD
--primary-400: #A78BFA
--primary-500: #8B5CF6
--primary-600: #7C3AED  /* ← main brand color */
--primary-700: #6D28D9
--primary-800: #5B21B6
--primary-900: #4C1D95
```

### Neutral — Zinc (warm cool gray)
```css
--neutral-50:  #FAFAFA
--neutral-100: #F4F4F5
--neutral-200: #E4E4E7
--neutral-300: #D4D4D8
--neutral-400: #A1A1AA
--neutral-500: #71717A
--neutral-600: #52525B
--neutral-700: #3F3F46
--neutral-800: #27272A
--neutral-900: #18181B
--neutral-950: #09090B  /* sidebar background */
```

### Semantic
```css
--success-bg:   #F0FDF4  --success:   #16A34A  /* green-600 */
--warning-bg:   #FFFBEB  --warning:   #D97706  /* amber-600 */
--error-bg:     #FEF2F2  --error:     #DC2626  /* red-600 */
--info-bg:      #EFF6FF  --info:      #2563EB  /* blue-600 */
```

### shadcn/ui CSS Variables (globals.css)
```css
:root {
  --background: 0 0% 98%;           /* zinc-50 */
  --foreground: 240 3.7% 10.9%;     /* zinc-900 */
  --card: 0 0% 100%;
  --card-foreground: 240 3.7% 10.9%;
  --popover: 0 0% 100%;
  --popover-foreground: 240 3.7% 10.9%;
  --primary: 262 83.3% 57.8%;       /* violet-500 equivalent */
  --primary-foreground: 0 0% 100%;
  --secondary: 240 4.8% 95.9%;
  --secondary-foreground: 240 5.9% 10%;
  --muted: 240 4.8% 95.9%;
  --muted-foreground: 240 3.8% 46.1%;
  --accent: 240 4.8% 95.9%;
  --accent-foreground: 240 5.9% 10%;
  --destructive: 0 84.2% 60.2%;     /* red-500 */
  --destructive-foreground: 0 0% 98%;
  --border: 240 5.9% 90%;
  --input: 240 5.9% 90%;
  --ring: 262 83.3% 57.8%;
  --radius: 0.5rem;
}
```

---

## Typography

**Font family:** Plus Jakarta Sans (Google Fonts)
- Import: `https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap`
- Tailwind config: `fontFamily: { sans: ['Plus Jakarta Sans', 'system-ui', 'sans-serif'] }`

| Token | Size | Line Height | Weight | Use |
|-------|------|-------------|--------|-----|
| text-xs | 12px | 1.5 | 400 | Captions, badges |
| text-sm | 14px | 1.5 | 400/500 | Body small, table cells |
| text-base | 16px | 1.5 | 400 | Body default |
| text-lg | 18px | 1.4 | 500/600 | Section headings |
| text-xl | 20px | 1.3 | 600 | Page titles |
| text-2xl | 24px | 1.3 | 700 | Major headings |
| text-3xl | 30px | 1.2 | 700 | Display / hero |

Weights: regular(400), medium(500), semibold(600), bold(700)

---

## Spacing

Base unit: 4px

| Token | Value | Use |
|-------|-------|-----|
| space-1 | 4px | Tight inline gaps |
| space-2 | 8px | Input padding, badge padding |
| space-3 | 12px | Button padding-y, compact cards |
| space-4 | 16px | Standard component padding |
| space-6 | 24px | Section spacing, card padding |
| space-8 | 32px | Page section gaps |
| space-12 | 48px | Large layout gaps |

---

## Components

### Button

Variants × Sizes:

```
Primary:     bg-violet-600 text-white hover:bg-violet-700
Secondary:   bg-zinc-100 text-zinc-900 hover:bg-zinc-200
Ghost:       bg-transparent text-zinc-600 hover:bg-zinc-100
Destructive: bg-red-600 text-white hover:bg-red-700
Outline:     border border-zinc-200 text-zinc-900 hover:bg-zinc-50
```

```
sm:  h-8  px-3  text-sm   rounded-md
md:  h-9  px-4  text-sm   rounded-md  (default)
lg:  h-11 px-6  text-base rounded-md
```

States: default → hover (bg shift) → active (ring-2 ring-violet-500) → disabled (opacity-50 cursor-not-allowed)

### Input

```
Default:   border border-zinc-200 bg-white h-9 px-3 text-sm rounded-md
Focus:     ring-2 ring-violet-500 ring-offset-0 border-violet-500
Error:     border-red-500 ring-2 ring-red-200
Disabled:  bg-zinc-50 text-zinc-400 cursor-not-allowed
```

Always paired with: `<label>` (text-sm font-medium text-zinc-700) + optional error message (text-xs text-red-600)

### Select

Same states as Input. Uses shadcn/ui `<Select>` component. Trigger has chevron-down icon (lucide-react).

### Textarea

Same states as Input. `resize-none` by default. Min-height: 80px.

### Card

```tsx
<Card>                        // border border-zinc-200 bg-white rounded-lg shadow-sm
  <CardHeader>                // px-6 py-4 border-b border-zinc-100
    <CardTitle />             // text-base font-semibold text-zinc-900
    <CardDescription />       // text-sm text-zinc-500
  </CardHeader>
  <CardContent>               // px-6 py-4
    {/* content */}
  </CardContent>
  <CardFooter>                // px-6 py-4 border-t border-zinc-100 bg-zinc-50
    {/* actions */}
  </CardFooter>
</Card>
```

### Modal / Dialog

```
Overlay:  fixed inset-0 bg-black/50 backdrop-blur-sm z-50
Panel:    bg-white rounded-xl shadow-xl max-w-md w-full p-6
Title:    text-lg font-semibold text-zinc-900
Body:     text-sm text-zinc-600 mt-2
Footer:   flex justify-end gap-2 mt-6
Close:    absolute top-4 right-4 — Ghost button with X icon
```

Animation: fade-in + scale-up (100ms ease-out)

### Table

```
Container:    border border-zinc-200 rounded-lg overflow-hidden
Header row:   bg-zinc-50 text-xs font-semibold uppercase tracking-wide text-zinc-500
Body row:     border-t border-zinc-100 hover:bg-zinc-50 transition-colors
Cell:         px-4 py-3 text-sm text-zinc-700
Sort header:  cursor-pointer hover:text-zinc-900 — ChevronUp/Down icon (lucide-react)
Empty state:  centered, py-12, muted icon + "No items yet" text
Loading:      skeleton rows (3 lines, animate-pulse, bg-zinc-100)
Pagination:   flex justify-between items-center px-4 py-3 text-sm text-zinc-600
```

### Badge

```
default: bg-zinc-100 text-zinc-700
success: bg-green-100 text-green-700
warning: bg-amber-100 text-amber-700
error:   bg-red-100   text-red-700
info:    bg-blue-100  text-blue-700
pending: bg-yellow-100 text-yellow-700

Size: text-xs font-medium px-2 py-0.5 rounded-full
```

### Avatar

```
Image:      rounded-full object-cover
Fallback:   bg-violet-100 text-violet-700 font-semibold — initials (first letter of name)
Sizes:      sm(24px) md(32px) lg(40px)
```

### Sidebar

```
Container:    w-60 h-screen bg-zinc-950 flex flex-col fixed left-0 top-0
Logo area:    px-4 py-5 border-b border-zinc-800
Nav section:  flex-1 px-3 py-4 space-y-1
Footer:       px-3 py-4 border-t border-zinc-800

Nav item default: text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100
                  rounded-md px-3 py-2 text-sm font-medium flex items-center gap-3
Nav item active:  bg-violet-600 text-white
Nav item icon:    16px lucide-react icon, flex-shrink-0
```

### Topbar

```
Container:  h-14 bg-white border-b border-zinc-200 flex items-center justify-between px-6
Left slot:  Page title — text-base font-semibold text-zinc-900
Right slot: User menu — Avatar + name + ChevronDown dropdown
```

User menu dropdown:
- bg-white border border-zinc-200 rounded-lg shadow-lg py-1
- Items: text-sm text-zinc-700 px-4 py-2 hover:bg-zinc-50
- Logout item: text-red-600 hover:bg-red-50

### Alert / Toast

```
success: border-l-4 border-green-500 bg-green-50 text-green-800
warning: border-l-4 border-amber-500 bg-amber-50 text-amber-800
error:   border-l-4 border-red-500   bg-red-50   text-red-800
info:    border-l-4 border-blue-500  bg-blue-50  text-blue-800

Layout: flex items-start gap-3 p-4 rounded-md text-sm
Icon:   16px lucide-react (CheckCircle2, AlertTriangle, XCircle, Info)
```

Toast (shadcn/ui Sonner): positioned bottom-right, same color variants.

---

## Layout Patterns

### App Shell (authenticated pages)

```
┌─────────────────────────────────────────────────────┐
│ Sidebar (240px, bg-zinc-950)  │ Main area            │
│                               │ ┌──────────────────┐ │
│ [Logo]                        │ │ Topbar (56px)    │ │
│                               │ └──────────────────┘ │
│ ● Home                        │                      │
│   Members                     │ Page content         │
│   Knowledge Base              │ px-6 py-6            │
│                               │                      │
│ ─────────────────────         │                      │
│ [Avatar] User name            │                      │
│          Role badge           │                      │
└─────────────────────────────────────────────────────┘
```

CSS:
```
body:           bg-zinc-50
sidebar:        fixed left-0 top-0 w-60 h-screen
main:           ml-60 min-h-screen flex flex-col
topbar:         sticky top-0 z-10
content:        flex-1 px-6 py-6
```

### Auth Shell (unauthenticated pages)

```
┌──────────────────────────────────────────────┐
│          bg-zinc-50 full screen              │
│                                              │
│   ┌──────────────────────────────────┐      │
│   │  Card (max-w-md, centered)       │      │
│   │  ┌──────────────────────────┐    │      │
│   │  │  Logo + App name         │    │      │
│   │  │  Form heading            │    │      │
│   │  │  Form fields             │    │      │
│   │  │  Submit button           │    │      │
│   │  │  Link to other auth page │    │      │
│   │  └──────────────────────────┘    │      │
│   └──────────────────────────────────┘      │
│                                              │
└──────────────────────────────────────────────┘
```

CSS: `min-h-screen flex items-center justify-center bg-zinc-50 px-4`

### Responsive Breakpoints

```
sm:  640px  — stack forms vertically
md:  768px  — sidebar collapses to icon-only (64px)
lg:  1024px — full sidebar (240px)
xl:  1280px — wider content area
```

**Note:** Admin dashboard is desktop-primary. Below md, show hamburger + mobile overlay sidebar.

---

## Page-Specific Patterns

### Stat Card (Dashboard)
```
bg-white border border-zinc-200 rounded-lg p-5
Label: text-sm text-zinc-500 font-medium
Value: text-2xl font-bold text-zinc-900 mt-1
Icon:  16px lucide-react in bg-violet-100 rounded-md p-1.5 text-violet-600
```

### Empty State
```
flex flex-col items-center justify-center py-16
Icon:        lucide-react 40px text-zinc-300
Title:       text-base font-semibold text-zinc-500 mt-3
Description: text-sm text-zinc-400 mt-1 text-center max-w-xs
CTA button:  mt-4 (optional)
```

### Page Header
```
mb-6 flex items-center justify-between
Title:  text-xl font-semibold text-zinc-900
Action: Primary button (right-aligned)
```

---

## Tailwind Config Extensions

```js
// tailwind.config.js
module.exports = {
  theme: {
    extend: {
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'system-ui', 'sans-serif'],
      },
      colors: {
        brand: {
          50:  '#F5F3FF',
          600: '#7C3AED',
          700: '#6D28D9',
        }
      }
    }
  }
}
```

---

*Design System version: 0.2.0 — M1 Frontend*
*Last updated: 2026-05-13*
