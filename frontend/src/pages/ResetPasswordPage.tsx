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
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

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
      <div className="flex min-h-screen items-center justify-center">
        <p role="alert" className="text-center text-destructive">
          Invalid or missing reset token.{" "}
          <Link to="/forgot-password" className="underline">
            Request a new link
          </Link>
        </p>
      </div>
    )
  }

  if (success) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p role="status" className="text-center">
          Password reset successfully.{" "}
          <Link to="/login" className="underline">
            Sign in
          </Link>
        </p>
      </div>
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
    <div className="flex min-h-screen items-center justify-center">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Reset password</CardTitle>
        </CardHeader>
        <CardContent>
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
        </CardContent>
      </Card>
    </div>
  )
}
