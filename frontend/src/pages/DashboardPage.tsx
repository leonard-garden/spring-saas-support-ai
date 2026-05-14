import { Link } from "react-router-dom"
import { EmailVerificationBanner } from "@/components/auth/EmailVerificationBanner"
import { StatCard } from "@/components/dashboard/StatCard"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/store/authStore"

const PLAN_LABEL = "Free Trial"

export function DashboardPage() {
  const user = useAuthStore((s) => s.user)

  return (
    <div className="space-y-6">
      <EmailVerificationBanner />

      <div>
        <h1 className="text-2xl font-semibold">{user?.businessName}</h1>
        <span className="mt-1 inline-flex items-center rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
          {PLAN_LABEL}
        </span>
      </div>

      <div className="flex gap-3">
        <Button asChild variant="outline">
          <Link to="/members">Invite Member</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/kb">Add Knowledge Base</Link>
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <StatCard label="Members" value={0} />
        <StatCard label="Knowledge Bases" value={0} />
      </div>
    </div>
  )
}
