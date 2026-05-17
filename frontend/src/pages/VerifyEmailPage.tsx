import { useState } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"
import { CheckCircle2, XCircle, Mail } from "lucide-react"
import { verifyEmail } from "@/lib/authApi"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/store/authStore"
import { api } from "@/lib/api"
import type { ApiResponse, MeResponse } from "@/types/auth"

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")
  const navigate = useNavigate()
  const { user, setAuth, accessToken } = useAuthStore()
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">("idle")

  const invalidToken = !token

  async function handleVerify() {
    if (!token) return
    setStatus("loading")
    try {
      await verifyEmail(token)
      if (accessToken) {
        try {
          const { data: me } = await api.get<ApiResponse<MeResponse>>("/auth/me")
          if (me.data) setAuth(accessToken, me.data)
        } catch { /* non-critical */ }
      }
      setStatus("success")
    } catch {
      setStatus("error")
    }
  }

  return (
    <div className="min-h-screen bg-stone-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white rounded-2xl shadow-sm border border-stone-200 p-8 text-center">
          {/* Logo / Brand */}
          <p className="text-xs font-semibold tracking-widest text-stone-400 uppercase mb-8">
            Support AI
          </p>

          {status === "idle" && !invalidToken && (
            <>
              <div className="flex justify-center mb-5">
                <div className="w-16 h-16 rounded-full bg-amber-50 border border-amber-100 flex items-center justify-center">
                  <Mail className="h-7 w-7 text-amber-500" />
                </div>
              </div>
              <h1 className="text-xl font-semibold text-stone-800 mb-2">Verify your email</h1>
              <p className="text-sm text-muted-foreground mb-6">
                Click the button below to confirm your email address and activate your account.
              </p>
              <Button
                onClick={handleVerify}
                className="w-full bg-amber-500 hover:bg-amber-600 text-white"
              >
                Verify my email
              </Button>
            </>
          )}

          {status === "loading" && (
            <>
              <div className="flex justify-center mb-5">
                <div className="w-16 h-16 rounded-full bg-stone-100 flex items-center justify-center animate-pulse">
                  <Mail className="h-7 w-7 text-stone-400" />
                </div>
              </div>
              <h1 className="text-xl font-semibold text-stone-800 mb-2">Verifying…</h1>
              <p className="text-sm text-muted-foreground">Please wait a moment.</p>
            </>
          )}

          {status === "success" && (
            <>
              <div className="flex justify-center mb-5">
                <div className="w-16 h-16 rounded-full bg-emerald-50 border border-emerald-100 flex items-center justify-center">
                  <CheckCircle2 className="h-7 w-7 text-emerald-500" />
                </div>
              </div>
              <h1 className="text-xl font-semibold text-stone-800 mb-2">Email verified!</h1>
              <p className="text-sm text-muted-foreground mb-6">
                Your account is now fully activated. You're good to go.
              </p>
              <Button
                onClick={() => navigate(user ? "/dashboard" : "/login")}
                className="w-full bg-amber-500 hover:bg-amber-600 text-white"
              >
                {user ? "Go to dashboard" : "Sign in"}
              </Button>
            </>
          )}

          {(status === "error" || invalidToken) && (
            <>
              <div className="flex justify-center mb-5">
                <div className="w-16 h-16 rounded-full bg-red-50 border border-red-100 flex items-center justify-center">
                  <XCircle className="h-7 w-7 text-destructive" />
                </div>
              </div>
              <h1 className="text-xl font-semibold text-stone-800 mb-2">Link expired</h1>
              <p className="text-sm text-muted-foreground mb-6">
                This verification link is invalid or has already been used.
              </p>
              <Button asChild variant="outline" className="w-full">
                <Link to={user ? "/dashboard" : "/login"}>
                  {user ? "Back to dashboard" : "Sign in"}
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
