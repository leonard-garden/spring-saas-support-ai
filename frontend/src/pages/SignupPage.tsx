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
