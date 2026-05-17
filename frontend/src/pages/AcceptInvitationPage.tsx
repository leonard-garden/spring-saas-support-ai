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
      <AuthLayout tagline="Join your team." subtitle="This invitation link is invalid or has expired.">
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
