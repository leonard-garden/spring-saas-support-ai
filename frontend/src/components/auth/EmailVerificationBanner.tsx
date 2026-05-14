import { useAuthStore } from "@/store/authStore"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"

export function EmailVerificationBanner() {
  const user = useAuthStore((s) => s.user)

  if (!user || user.emailVerified) return null

  return (
    <Alert variant="destructive">
      <AlertTitle>Verify your email address</AlertTitle>
      <AlertDescription>
        Please check your inbox and verify your email to unlock all features.
      </AlertDescription>
    </Alert>
  )
}
