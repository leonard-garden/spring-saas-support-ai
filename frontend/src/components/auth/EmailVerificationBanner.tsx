import { TriangleAlert } from "lucide-react"
import { useAuthStore } from "@/store/authStore"

export function EmailVerificationBanner() {
  const user = useAuthStore((s) => s.user)

  if (!user || user.emailVerified) return null

  return (
    <div role="alert" className="flex items-start gap-3 rounded-lg border-l-4 border-amber-400 bg-amber-50 px-4 py-3">
      <TriangleAlert className="h-4 w-4 text-amber-500 mt-0.5 shrink-0" aria-hidden="true" />
      <div>
        <p className="text-sm font-semibold text-amber-900">Verify your email address</p>
        <p className="text-sm text-amber-800">
          Please check your inbox and verify your email to unlock all features.
        </p>
      </div>
    </div>
  )
}
