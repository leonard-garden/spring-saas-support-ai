import { useState } from "react"
import { TriangleAlert, CheckCircle2 } from "lucide-react"
import { useAuthStore } from "@/store/authStore"
import { resendVerificationEmail } from "@/lib/authApi"

export function EmailVerificationBanner() {
  const user = useAuthStore((s) => s.user)
  const [sent, setSent] = useState(false)
  const [sending, setSending] = useState(false)

  if (!user || user.emailVerified) return null

  async function handleResend() {
    setSending(true)
    try {
      await resendVerificationEmail()
      setSent(true)
    } finally {
      setSending(false)
    }
  }

  return (
    <div role="alert" className="flex items-start gap-3 rounded-lg border-l-4 border-amber-400 bg-amber-50 px-4 py-3">
      {sent
        ? <CheckCircle2 className="h-4 w-4 text-emerald-500 mt-0.5 shrink-0" aria-hidden="true" />
        : <TriangleAlert className="h-4 w-4 text-amber-500 mt-0.5 shrink-0" aria-hidden="true" />
      }
      <div className="flex-1">
        <p className="text-sm font-semibold text-amber-900">Verify your email address</p>
        {sent ? (
          <p className="text-sm text-emerald-700">Verification email sent! Check your inbox.</p>
        ) : (
          <p className="text-sm text-amber-800">
            Please check your inbox and verify your email to unlock all features.{" "}
            <button
              onClick={handleResend}
              disabled={sending}
              className="underline font-medium hover:text-amber-900 disabled:opacity-50"
            >
              {sending ? "Sending…" : "Resend email"}
            </button>
          </p>
        )}
      </div>
    </div>
  )
}
