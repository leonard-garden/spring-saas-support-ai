import { LogoutButton } from "@/components/auth/LogoutButton"
import { EmailVerificationBanner } from "@/components/auth/EmailVerificationBanner"

export function DashboardPage() {
  return (
    <div className="p-6 space-y-4">
      <EmailVerificationBanner />
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <LogoutButton />
      </div>
    </div>
  )
}
