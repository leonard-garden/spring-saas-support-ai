# Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the Warm Slate design system (stone neutrals, amber accent, Fraunces + DM Sans, crisp outlined components, dark sidebar, split-panel auth) to the existing frontend.

**Architecture:** Pure styling change — override shadcn CSS tokens in `index.css`, extend Tailwind config, update layout components, create `AuthLayout` split-panel wrapper, update StatCard and banner. No logic changes, no new API calls, no store changes.

**Tech Stack:** React 18, Tailwind CSS v3, shadcn/ui, lucide-react, react-router-dom

> **Note on TDD:** This plan makes no behavior changes. Unit/integration tests are untouched. Verification steps are visual — run the dev server and inspect each page.

---

## File Map

| Action | File | What changes |
|--------|------|--------------|
| Modify | `frontend/src/index.css` | Replace `:root` CSS variables, swap font import |
| Modify | `frontend/tailwind.config.js` | Replace `font-sans`, add `font-display`, add `sidebar` color, remove `brand` colors |
| Modify | `frontend/src/components/layout/Sidebar.tsx` | Dark bg, Fraunces wordmark, stone text |
| Modify | `frontend/src/components/layout/SidebarNavLink.tsx` | Amber left-border active state |
| Create | `frontend/src/components/layout/AuthLayout.tsx` | Split-panel auth wrapper (new file) |
| Modify | `frontend/src/pages/LoginPage.tsx` | Use `AuthLayout`, remove Card wrapper |
| Modify | `frontend/src/pages/SignupPage.tsx` | Use `AuthLayout`, remove Card wrapper |
| Modify | `frontend/src/pages/ForgotPasswordPage.tsx` | Use `AuthLayout`, remove Card wrapper |
| Modify | `frontend/src/pages/ResetPasswordPage.tsx` | Use `AuthLayout`, remove Card wrapper |
| Modify | `frontend/src/pages/AcceptInvitationPage.tsx` | Use `AuthLayout`, remove Card wrapper |
| Modify | `frontend/src/components/dashboard/StatCard.tsx` | `font-display` on number |
| Modify | `frontend/src/components/auth/EmailVerificationBanner.tsx` | Amber-100 style, remove `variant="destructive"` |

---

## Task 1: CSS Tokens + Tailwind Config

**Files:**
- Modify: `frontend/src/index.css`
- Modify: `frontend/tailwind.config.js`

- [ ] **Step 1: Replace `index.css`**

Replace the entire file content:

```css
@import url("https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,600;9..144,700&family=DM+Sans:wght@400;500;600&display=swap");

@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 30 14% 97%;
    --foreground: 20 6% 10%;

    --card: 0 0% 100%;
    --card-foreground: 20 6% 10%;

    --popover: 0 0% 100%;
    --popover-foreground: 20 6% 10%;

    --primary: 38 92% 50%;
    --primary-foreground: 20 6% 15%;

    --secondary: 30 6% 96%;
    --secondary-foreground: 20 6% 25%;

    --muted: 30 6% 96%;
    --muted-foreground: 25 5% 45%;

    --accent: 30 6% 96%;
    --accent-foreground: 20 6% 25%;

    --destructive: 0 84% 60%;
    --destructive-foreground: 0 0% 98%;

    --border: 20 6% 90%;
    --input: 20 5% 83%;
    --ring: 38 92% 50%;

    --radius: 0.5rem;

    --sidebar: 20 6% 15%;
  }

  * {
    @apply border-border;
  }
  body {
    @apply bg-background text-foreground font-sans antialiased;
  }
}
```

- [ ] **Step 2: Update `tailwind.config.js`**

Replace the entire file content:

```js
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ["DM Sans", "sans-serif"],
        display: ["Fraunces", "serif"],
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      colors: {
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        sidebar: "hsl(var(--sidebar))",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}
```

- [ ] **Step 3: Start dev server and verify tokens load**

```bash
cd frontend && npm run dev
```

Open http://localhost:5173. Expected: page background shifts to warm off-white (`#fafaf9`), body font changes to DM Sans, primary color is now amber. No console errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/index.css frontend/tailwind.config.js
git commit -m "style(tokens): apply Warm Slate CSS variables and DM Sans / Fraunces fonts"
```

---

## Task 2: Dark Sidebar

**Files:**
- Modify: `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/components/layout/SidebarNavLink.tsx`

- [ ] **Step 1: Update `Sidebar.tsx`**

Replace the entire file:

```tsx
import { Home, Users, BookOpen } from "lucide-react"
import { useAuthStore } from "@/store/authStore"
import { LogoutButton } from "@/components/auth/LogoutButton"
import { SidebarNavLink } from "./SidebarNavLink"

export function Sidebar() {
  const user = useAuthStore((s) => s.user)

  return (
    <aside className="w-64 shrink-0 flex flex-col h-full bg-sidebar text-stone-300">
      <div className="px-4 h-14 flex items-center border-b border-stone-800">
        <span className="font-display text-base font-bold text-white tracking-tight">
          Support AI
        </span>
      </div>

      <nav className="flex-1 px-2 py-4 space-y-0.5">
        <SidebarNavLink to="/dashboard" end icon={<Home className="h-4 w-4" />} label="Home" />
        <SidebarNavLink to="/members" icon={<Users className="h-4 w-4" />} label="Members" />
        <SidebarNavLink to="/kb" icon={<BookOpen className="h-4 w-4" />} label="Knowledge Base" />
      </nav>

      <div className="border-t border-stone-800 px-4 py-3 space-y-0.5">
        <p className="text-sm font-medium text-stone-200">{user?.email}</p>
        <p className="text-xs text-stone-500 uppercase tracking-wide">
          {user?.role} · {user?.businessName}
        </p>
        <div className="pt-2">
          <LogoutButton />
        </div>
      </div>
    </aside>
  )
}
```

- [ ] **Step 2: Update `SidebarNavLink.tsx`**

Replace the entire file:

```tsx
import { NavLink } from "react-router-dom"
import type { ReactNode } from "react"

interface SidebarNavLinkProps {
  to: string
  icon: ReactNode
  label: string
  end?: boolean
}

export function SidebarNavLink({ to, icon, label, end }: SidebarNavLinkProps) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
          isActive
            ? "bg-stone-800 text-white border-l-2 border-amber-400 pl-[10px]"
            : "text-stone-400 hover:bg-stone-800 hover:text-white"
        }`
      }
    >
      {icon}
      <span>{label}</span>
    </NavLink>
  )
}
```

> Note: `pl-[10px]` compensates for the 2px left border so text doesn't shift on active.

- [ ] **Step 3: Verify in browser**

Navigate to http://localhost:5173/dashboard (login first).

Expected:
- Sidebar is dark stone (`#292524`)
- Logo "Support AI" is white, Fraunces font
- Active nav link has amber left border
- Inactive links are stone-400, hover turns white
- Footer shows user email in stone-200

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/layout/Sidebar.tsx frontend/src/components/layout/SidebarNavLink.tsx
git commit -m "style(sidebar): dark stone sidebar with amber active indicator"
```

---

## Task 3: AuthLayout + Wire Auth Pages

**Files:**
- Create: `frontend/src/components/layout/AuthLayout.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `frontend/src/pages/SignupPage.tsx`
- Modify: `frontend/src/pages/ForgotPasswordPage.tsx`
- Modify: `frontend/src/pages/ResetPasswordPage.tsx`
- Modify: `frontend/src/pages/AcceptInvitationPage.tsx`

- [ ] **Step 1: Create `AuthLayout.tsx`**

```tsx
import type { ReactNode } from "react"

interface AuthLayoutProps {
  children: ReactNode
  tagline?: string
  subtitle?: string
}

export function AuthLayout({
  children,
  tagline = "AI customer support, embeddable in 5 minutes.",
  subtitle = "White-label chatbot trained on your docs.",
}: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen">
      {/* Left panel — branding */}
      <div className="hidden md:flex md:w-2/5 bg-sidebar flex-col justify-center px-12">
        <div className="w-8 h-1 bg-amber-400 rounded mb-6" />
        <p className="font-display text-2xl font-bold text-white leading-snug mb-3">
          {tagline}
        </p>
        <p className="text-sm text-stone-400 leading-relaxed">{subtitle}</p>
      </div>

      {/* Right panel — form */}
      <div className="flex flex-1 items-center justify-center bg-white px-6 py-12">
        <div className="w-full max-w-sm">
          <p className="font-display text-lg font-bold text-foreground mb-8">Support AI</p>
          {children}
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Update `LoginPage.tsx`**

Replace the entire file:

```tsx
import { useState } from "react"
import { useNavigate, Link } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Eye, EyeOff } from "lucide-react"
import { login } from "@/lib/authApi"
import { useAuthStore } from "@/store/authStore"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { AuthLayout } from "@/components/layout/AuthLayout"

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
})

type LoginFormValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  })

  async function onSubmit(values: LoginFormValues) {
    setError(null)
    try {
      const { token, user } = await login(values.email, values.password)
      setAuth(token, user)
      navigate("/dashboard")
    } catch {
      setError("Invalid email or password.")
    }
  }

  return (
    <AuthLayout>
      <h1 className="font-display text-2xl font-bold text-foreground mb-6">Sign in</h1>
      {error && (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      )}
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Email</FormLabel>
                <FormControl>
                  <Input type="email" placeholder="you@example.com" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="password"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Password</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input type={showPassword ? "text" : "password"} className="pr-10" {...field} />
                    <button
                      type="button"
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      tabIndex={-1}
                      onClick={() => setShowPassword((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            Sign in
          </Button>
        </form>
      </Form>
      <p className="mt-4 text-center text-sm">
        <Link to="/forgot-password" className="underline text-muted-foreground hover:text-foreground">
          Forgot password?
        </Link>
      </p>
      <p className="mt-2 text-center text-sm text-muted-foreground">
        No account?{" "}
        <Link to="/signup" className="underline text-foreground">
          Sign up
        </Link>
      </p>
    </AuthLayout>
  )
}
```

- [ ] **Step 3: Update `SignupPage.tsx`**

Replace the entire file:

```tsx
import { useState } from "react"
import { Link } from "react-router-dom"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Eye, EyeOff } from "lucide-react"
import { signup } from "@/lib/authApi"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { AuthLayout } from "@/components/layout/AuthLayout"

const signupSchema = z.object({
  businessName: z.string().min(2),
  email: z.string().email(),
  password: z.string().min(8),
})

type SignupFormValues = z.infer<typeof signupSchema>

export function SignupPage() {
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)

  const form = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { businessName: "", email: "", password: "" },
  })

  async function onSubmit(values: SignupFormValues) {
    setError(null)
    try {
      await signup(values.businessName, values.email, values.password)
      setSuccess(true)
    } catch {
      setError("Signup failed. Please try again.")
    }
  }

  if (success) {
    return (
      <AuthLayout>
        <p role="status" className="text-center text-sm">
          Account created! Check your inbox to verify your email, then{" "}
          <Link to="/login" className="underline text-foreground">
            sign in
          </Link>
          .
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout tagline="Set up your support AI in minutes." subtitle="No credit card required. 14-day Pro trial included.">
      <h1 className="font-display text-2xl font-bold text-foreground mb-6">Create account</h1>
      {error && (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      )}
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="businessName"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Business name</FormLabel>
                <FormControl>
                  <Input placeholder="Acme Inc." {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Email</FormLabel>
                <FormControl>
                  <Input type="email" placeholder="you@example.com" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="password"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Password</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input type={showPassword ? "text" : "password"} className="pr-10" {...field} />
                    <button
                      type="button"
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      tabIndex={-1}
                      onClick={() => setShowPassword((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            Create account
          </Button>
        </form>
      </Form>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link to="/login" className="underline text-foreground">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
```

- [ ] **Step 4: Update `ForgotPasswordPage.tsx`**

Replace the entire file:

```tsx
import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Link } from "react-router-dom"
import { forgotPassword } from "@/lib/authApi"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { AuthLayout } from "@/components/layout/AuthLayout"

const forgotSchema = z.object({
  email: z.string().email(),
})

type ForgotFormValues = z.infer<typeof forgotSchema>

export function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false)

  const form = useForm<ForgotFormValues>({
    resolver: zodResolver(forgotSchema),
    defaultValues: { email: "" },
  })

  async function onSubmit(values: ForgotFormValues) {
    try {
      await forgotPassword(values.email)
    } finally {
      setSubmitted(true)
    }
  }

  if (submitted) {
    return (
      <AuthLayout tagline="Check your inbox." subtitle="A reset link is on its way if that address exists.">
        <p role="status" className="text-sm text-muted-foreground text-center">
          If that email exists, a reset link has been sent.{" "}
          <Link to="/login" className="underline text-foreground">
            Back to sign in
          </Link>
        </p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout tagline="Forgot your password?" subtitle="Enter your email and we'll send a reset link.">
      <h1 className="font-display text-2xl font-bold text-foreground mb-6">Forgot password</h1>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Email</FormLabel>
                <FormControl>
                  <Input type="email" placeholder="you@example.com" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            Send reset link
          </Button>
        </form>
      </Form>
      <p className="mt-4 text-center text-sm">
        <Link to="/login" className="underline text-muted-foreground hover:text-foreground">
          Back to sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
```

- [ ] **Step 5: Update `ResetPasswordPage.tsx`**

Replace the entire file:

```tsx
import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Link, useSearchParams } from "react-router-dom"
import { Eye, EyeOff } from "lucide-react"
import { resetPassword } from "@/lib/authApi"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { AuthLayout } from "@/components/layout/AuthLayout"

const resetSchema = z
  .object({
    newPassword: z.string().min(8),
    confirmPassword: z.string().min(8),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  })

type ResetFormValues = z.infer<typeof resetSchema>

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const form = useForm<ResetFormValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  })

  if (!token) {
    return (
      <AuthLayout>
        <p role="alert" className="text-center text-sm text-destructive">
          Invalid or missing reset token.{" "}
          <Link to="/forgot-password" className="underline">
            Request a new link
          </Link>
        </p>
      </AuthLayout>
    )
  }

  if (success) {
    return (
      <AuthLayout>
        <p role="status" className="text-center text-sm text-muted-foreground">
          Password reset successfully.{" "}
          <Link to="/login" className="underline text-foreground">
            Sign in
          </Link>
        </p>
      </AuthLayout>
    )
  }

  async function onSubmit(values: ResetFormValues) {
    setError(null)
    try {
      await resetPassword(token!, values.newPassword)
      setSuccess(true)
    } catch {
      setError("Failed to reset password. The link may have expired.")
    }
  }

  return (
    <AuthLayout tagline="Reset your password." subtitle="Choose a strong password to secure your account.">
      <h1 className="font-display text-2xl font-bold text-foreground mb-6">Reset password</h1>
      {error && (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      )}
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="newPassword"
            render={({ field }) => (
              <FormItem>
                <FormLabel>New password</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input type={showNewPassword ? "text" : "password"} className="pr-10" {...field} />
                    <button
                      type="button"
                      aria-label={showNewPassword ? "Hide password" : "Show password"}
                      tabIndex={-1}
                      onClick={() => setShowNewPassword((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground"
                    >
                      {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="confirmPassword"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Confirm password</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input type={showConfirmPassword ? "text" : "password"} className="pr-10" {...field} />
                    <button
                      type="button"
                      aria-label={showConfirmPassword ? "Hide confirm password" : "Show confirm password"}
                      tabIndex={-1}
                      onClick={() => setShowConfirmPassword((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground"
                    >
                      {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            Reset password
          </Button>
        </form>
      </Form>
    </AuthLayout>
  )
}
```

- [ ] **Step 6: Update `AcceptInvitationPage.tsx`**

Replace the entire file:

```tsx
import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Link, useSearchParams, useNavigate } from "react-router-dom"
import { Eye, EyeOff } from "lucide-react"
import { acceptInvitation } from "@/lib/authApi"
import { useAuthStore } from "@/store/authStore"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { AuthLayout } from "@/components/layout/AuthLayout"

const acceptSchema = z
  .object({
    password: z.string().min(8),
    confirmPassword: z.string().min(8),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  })

type AcceptFormValues = z.infer<typeof acceptSchema>

export function AcceptInvitationPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [error, setError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const form = useForm<AcceptFormValues>({
    resolver: zodResolver(acceptSchema),
    defaultValues: { password: "", confirmPassword: "" },
  })

  if (!token) {
    return (
      <AuthLayout>
        <p role="alert" className="text-center text-sm text-destructive">
          Invalid or missing invitation link. Please ask your admin to resend the invitation.
        </p>
      </AuthLayout>
    )
  }

  async function onSubmit(values: AcceptFormValues) {
    setError(null)
    try {
      const { token: accessToken, user } = await acceptInvitation(token!, values.password)
      setAuth(accessToken, user)
      navigate("/dashboard", { replace: true })
    } catch {
      setError("This invitation link is invalid or has expired. Please ask your admin to send a new one.")
    }
  }

  return (
    <AuthLayout tagline="You've been invited." subtitle="Set a password to join your team on Support AI.">
      <h1 className="font-display text-2xl font-bold text-foreground mb-6">Set your password</h1>
      {error && (
        <p role="alert" className="mb-4 text-sm text-destructive">
          {error}
        </p>
      )}
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="password"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Password</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input type={showPassword ? "text" : "password"} className="pr-10" {...field} />
                    <button
                      type="button"
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      tabIndex={-1}
                      onClick={() => setShowPassword((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground"
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="confirmPassword"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Confirm password</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input type={showConfirm ? "text" : "password"} className="pr-10" {...field} />
                    <button
                      type="button"
                      aria-label={showConfirm ? "Hide confirm password" : "Show confirm password"}
                      tabIndex={-1}
                      onClick={() => setShowConfirm((v) => !v)}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-muted-foreground"
                    >
                      {showConfirm ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            Create account
          </Button>
        </form>
      </Form>
      <p className="mt-4 text-center text-sm text-muted-foreground">
        Already have an account?{" "}
        <Link to="/login" className="underline text-foreground">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
```

- [ ] **Step 7: Verify auth pages in browser**

Visit each route and confirm the split-panel layout renders:
- http://localhost:5173/login — dark left panel, form right
- http://localhost:5173/signup — left tagline "Set up your support AI in minutes."
- http://localhost:5173/forgot-password
- http://localhost:5173/reset-password?token=test — should show error state (no valid token)
- http://localhost:5173/accept-invitation?token=test — should show error state

On mobile (< md breakpoint), left panel should be hidden — only form shows.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/layout/AuthLayout.tsx \
        frontend/src/pages/LoginPage.tsx \
        frontend/src/pages/SignupPage.tsx \
        frontend/src/pages/ForgotPasswordPage.tsx \
        frontend/src/pages/ResetPasswordPage.tsx \
        frontend/src/pages/AcceptInvitationPage.tsx
git commit -m "style(auth): split-panel AuthLayout with dark branding panel"
```

---

## Task 4: Component Updates

**Files:**
- Modify: `frontend/src/components/dashboard/StatCard.tsx`
- Modify: `frontend/src/components/auth/EmailVerificationBanner.tsx`

- [ ] **Step 1: Update `StatCard.tsx`**

Replace the entire file:

```tsx
import { Card, CardContent } from "@/components/ui/card"

interface StatCardProps {
  label: string
  value: number | string
}

export function StatCard({ label, value }: StatCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-1">
          {label}
        </p>
        <p className="font-display text-4xl font-bold text-foreground">{value}</p>
      </CardContent>
    </Card>
  )
}
```

- [ ] **Step 2: Update `EmailVerificationBanner.tsx`**

Replace the entire file:

```tsx
import { TriangleAlert } from "lucide-react"
import { useAuthStore } from "@/store/authStore"

export function EmailVerificationBanner() {
  const user = useAuthStore((s) => s.user)

  if (!user || user.emailVerified) return null

  return (
    <div className="flex items-start gap-3 rounded-lg border-l-4 border-amber-400 bg-amber-50 px-4 py-3">
      <TriangleAlert className="h-4 w-4 text-amber-500 mt-0.5 shrink-0" />
      <div>
        <p className="text-sm font-semibold text-amber-900">Verify your email address</p>
        <p className="text-sm text-amber-800">
          Please check your inbox and verify your email to unlock all features.
        </p>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Verify in browser**

Go to http://localhost:5173/dashboard (logged in, email unverified).

Expected:
- StatCard shows Fraunces large bold number
- Banner is amber-50 background with amber left border and amber icon — not red/destructive

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dashboard/StatCard.tsx \
        frontend/src/components/auth/EmailVerificationBanner.tsx
git commit -m "style(components): Fraunces stat number, amber verification banner"
```

---

## Task 5: Final Verification + Cleanup

**Files:** None modified

- [ ] **Step 1: Run existing test suite**

```bash
cd frontend && npm test -- --run
```

Expected: all existing tests pass. (No test changes needed — this plan makes no behavior changes.)

- [ ] **Step 2: Visual walk-through checklist**

Open http://localhost:5173 and check each page:

| Page | What to check |
|------|--------------|
| `/login` | Split panel, dark left, Fraunces heading, amber button |
| `/signup` | Custom left tagline, amber button, success state in AuthLayout |
| `/forgot-password` | AuthLayout, single email field |
| `/dashboard` | Dark sidebar, amber active indicator, Fraunces stat numbers, amber banner if unverified |
| `/members` | Dark sidebar, table renders, invite button amber |
| `/kb` | Dark sidebar, no regressions |

- [ ] **Step 3: Check for hardcoded color or font references**

```bash
cd frontend/src && grep -r "Plus Jakarta\|purple\|#8B5CF6\|#7C3AED\|violet" --include="*.tsx" --include="*.ts" --include="*.css"
```

Expected: no results. If any found, replace with token equivalents.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "style(design-system): complete Warm Slate design system implementation"
```
