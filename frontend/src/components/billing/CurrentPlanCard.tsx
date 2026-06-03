import { CreditCard, TrendingUp } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useSubscription } from "@/hooks/useBilling"
import type { SubscriptionStatus } from "@/types/billing"

// ─── Helpers ─────────────────────────────────────────────────────────────────

function daysUntil(iso: string): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86400000))
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  })
}

// ─── StatusBadge ─────────────────────────────────────────────────────────────

interface StatusBadgeProps {
  status: SubscriptionStatus
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const map: Record<SubscriptionStatus, { label: string; className: string }> = {
    TRIALING: { label: "Trial", className: "bg-amber-50 text-amber-700 border-amber-200" },
    ACTIVE: { label: "Active", className: "bg-emerald-50 text-emerald-700 border-emerald-200" },
    PAST_DUE: { label: "Past Due", className: "bg-red-50 text-red-700 border-red-200" },
    CANCELED: { label: "Canceled", className: "bg-stone-100 text-stone-500 border-stone-200" },
    UNPAID: { label: "Unpaid", className: "bg-red-50 text-red-700 border-red-200" },
  }
  const cfg = map[status] ?? { label: status, className: "bg-stone-100 text-stone-500" }
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${cfg.className}`}
    >
      {cfg.label}
    </span>
  )
}

// ─── Props ───────────────────────────────────────────────────────────────────

interface CurrentPlanCardProps {
  onCancelClick: () => void
}

// ─── Component ───────────────────────────────────────────────────────────────

export function CurrentPlanCard({ onCancelClick }: CurrentPlanCardProps) {
  const { data: sub, isLoading } = useSubscription()

  if (isLoading || !sub) {
    return (
      <Card className="col-span-2">
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">Current Plan</CardTitle>
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-2.5">
            <Skeleton className="h-9 w-9 rounded-lg" />
            <div className="space-y-1.5">
              <Skeleton className="h-5 w-20" />
              <Skeleton className="h-3.5 w-16" />
            </div>
          </div>
          <div className="space-y-2">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-full" />
          </div>
          <div className="space-y-2 pt-1">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        </CardContent>
      </Card>
    )
  }

  const daysLeft = sub.status === "TRIALING" && sub.trialEndsAt ? daysUntil(sub.trialEndsAt) : null

  return (
    <Card className="col-span-2">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Current Plan</CardTitle>
          <StatusBadge status={sub.status} />
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Plan identity */}
        <div className="flex items-center gap-2.5">
          <div className="rounded-lg bg-primary/10 p-2">
            <TrendingUp className="h-5 w-5 text-primary" />
          </div>
          <div>
            <p className="font-display text-xl font-bold">{sub.planName}</p>
            <p className="text-xs text-muted-foreground capitalize">{sub.planSlug} plan</p>
          </div>
        </div>

        {/* Period details */}
        <div className="space-y-1 text-sm">
          {daysLeft !== null && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Trial ends</span>
              <span className="font-medium text-amber-600">{daysLeft} days left</span>
            </div>
          )}
          {sub.currentPeriodEnd && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Period ends</span>
              <span className="font-medium">{formatDate(sub.currentPeriodEnd)}</span>
            </div>
          )}
          {sub.cancelAtPeriodEnd && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Cancels</span>
              <span className="font-medium text-red-600">At period end</span>
            </div>
          )}
          {sub.pendingPlanId && sub.currentPeriodEnd && (
            <p className="text-xs text-muted-foreground pt-1">
              Downgrade to{" "}
              <span className="font-medium text-foreground capitalize">{sub.pendingPlanId}</span>{" "}
              pending at {formatDate(sub.currentPeriodEnd)}
            </p>
          )}
        </div>

        {/* Actions */}
        <div className="pt-1 space-y-2">
          <Button variant="outline" size="sm" className="w-full text-xs" asChild>
            <a href="#" target="_blank" rel="noopener noreferrer">
              <CreditCard className="h-3.5 w-3.5 mr-1.5" />
              Manage Payment Method
            </a>
          </Button>
          {!sub.cancelAtPeriodEnd && sub.status !== "CANCELED" && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full text-xs text-muted-foreground hover:text-destructive"
              onClick={onCancelClick}
            >
              Cancel Subscription
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
