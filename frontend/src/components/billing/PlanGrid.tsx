import { Skeleton } from "@/components/ui/skeleton"
import { usePlans, useSubscription } from "@/hooks/useBilling"
import { PlanCard } from "@/components/billing/PlanCard"

// ─── Props ────────────────────────────────────────────────────────────────────

interface PlanGridProps {
  onUpgrade: (planSlug: string) => void
  onDowngrade: (planSlug: string) => void
}

// ─── Skeleton ─────────────────────────────────────────────────────────────────

function PlanGridSkeleton() {
  return (
    <div className="grid grid-cols-4 gap-4 pt-3">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="rounded-xl border bg-card p-5 flex flex-col gap-4">
          <div className="flex items-center gap-2.5">
            <Skeleton className="h-8 w-8 rounded-lg" />
            <div className="space-y-1.5 flex-1">
              <Skeleton className="h-3.5 w-20" />
              <Skeleton className="h-3 w-28" />
            </div>
          </div>
          <Skeleton className="h-9 w-24" />
          <div className="space-y-2 flex-1">
            <Skeleton className="h-3.5 w-full" />
            <Skeleton className="h-3.5 w-full" />
            <Skeleton className="h-3.5 w-full" />
            <Skeleton className="h-3.5 w-3/4" />
          </div>
          <Skeleton className="h-9 w-full rounded-md" />
        </div>
      ))}
    </div>
  )
}

// ─── Component ────────────────────────────────────────────────────────────────

export function PlanGrid({ onUpgrade, onDowngrade }: PlanGridProps) {
  const { data: plans, isLoading: plansLoading } = usePlans()
  const { data: subscription, isLoading: subLoading } = useSubscription()

  if (plansLoading || subLoading) {
    return <PlanGridSkeleton />
  }

  if (!plans || plans.length === 0) {
    return (
      <p className="text-sm text-muted-foreground py-4">
        No plans available at this time.
      </p>
    )
  }

  const currentPlanSlug = subscription?.planSlug ?? "free"
  const currentPlan = plans.find((p) => p.slug === currentPlanSlug)
  const currentPlanPrice = currentPlan?.priceMonthly ?? 0

  return (
    <div className="grid grid-cols-4 gap-4 pt-3">
      {plans.map((plan) => (
        <PlanCard
          key={plan.slug}
          plan={plan}
          isCurrent={plan.slug === currentPlanSlug}
          subscriptionStatus={subscription?.status}
          currentPlanPrice={currentPlanPrice}
          onUpgrade={onUpgrade}
          onDowngrade={onDowngrade}
        />
      ))}
    </div>
  )
}
