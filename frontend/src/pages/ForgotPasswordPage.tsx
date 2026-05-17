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
