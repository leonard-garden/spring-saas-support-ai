import { useState } from "react"
import { Clock, AlertCircle, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useSubscription } from "@/hooks/useBilling"

function daysUntil(iso: string): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86400000))
}

export function StatusBanners() {
  const { data: sub, isLoading } = useSubscription()
  const [trialDismissed, setTrialDismissed] = useState(false)
  const [pastDueDismissed, setPastDueDismissed] = useState(false)

  if (isLoading || !sub) return null

  const showTrial = sub.status === "TRIALING" && !trialDismissed
  const showPastDue = sub.status === "PAST_DUE" && !pastDueDismissed

  if (!showTrial && !showPastDue) return null

  const daysLeft = sub.trialEndsAt ? daysUntil(sub.trialEndsAt) : 0

  return (
    <div className="space-y-3">
      {/* Trial banner */}
      {showTrial && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
          <Clock className="h-4 w-4 text-amber-600 shrink-0" />
          <p className="text-sm flex-1">
            <span className="font-semibold text-amber-700">{daysLeft} days left</span>
            <span className="text-amber-600"> on your {sub.planName} trial. Upgrade to keep access after your trial ends.</span>
          </p>
          <Button size="sm" className="shrink-0">
            Upgrade Now
          </Button>
          <button
            type="button"
            onClick={() => setTrialDismissed(true)}
            className="shrink-0 rounded p-0.5 text-amber-500 hover:text-amber-700 hover:bg-amber-100 transition-colors"
            aria-label="Dismiss trial banner"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}

      {/* Past-due banner */}
      {showPastDue && (
        <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
          <p className="text-sm text-red-700 flex-1">
            <span className="font-semibold">Payment failed.</span> Update your payment method to avoid service interruption.
          </p>
          <Button size="sm" variant="destructive" className="shrink-0">
            Update Payment
          </Button>
          <button
            type="button"
            onClick={() => setPastDueDismissed(true)}
            className="shrink-0 rounded p-0.5 text-red-400 hover:text-red-600 hover:bg-red-100 transition-colors"
            aria-label="Dismiss payment banner"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      )}
    </div>
  )
}
