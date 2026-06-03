import { Shield, Zap, TrendingUp, Building2, CheckCircle2, type LucideIcon } from "lucide-react"
import { Button } from "@/components/ui/button"
import type { Plan, SubscriptionStatus } from "@/types/billing"

// ─── Icon map ─────────────────────────────────────────────────────────────────

const SLUG_ICON: Record<string, LucideIcon> = {
  free: Shield,
  starter: Zap,
  pro: TrendingUp,
  business: Building2,
}

// ─── Feature string builder ───────────────────────────────────────────────────

function formatLimit(n: number): string {
  if (n === -1) return "Unlimited"
  if (n >= 1000) return `${(n / 1000).toFixed(0)}k`
  return String(n)
}

function buildFeatures(plan: Plan): string[] {
  return [
    `${formatLimit(plan.maxKnowledgeBases)} Knowledge Base${plan.maxKnowledgeBases !== 1 ? "s" : ""}`,
    `${formatLimit(plan.maxDocsPerKb)} Documents/KB`,
    `${formatLimit(plan.maxMessagesPerMonth)} Messages/mo`,
    `${formatLimit(plan.maxMembers)} Member${plan.maxMembers !== 1 ? "s" : ""}`,
  ]
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface PlanCardProps {
  plan: Plan
  isCurrent: boolean
  subscriptionStatus: SubscriptionStatus | undefined
  currentPlanPrice: number
  onUpgrade: (slug: string) => void
  onDowngrade: (slug: string) => void
}

// ─── Component ────────────────────────────────────────────────────────────────

export function PlanCard({
  plan,
  isCurrent,
  subscriptionStatus,
  currentPlanPrice,
  onUpgrade,
  onDowngrade,
}: PlanCardProps) {
  const Icon = SLUG_ICON[plan.slug] ?? TrendingUp
  const highlight = plan.slug === "pro"
  const isUpgrade = plan.priceMonthly > currentPlanPrice
  const features = buildFeatures(plan)

  return (
    <div
      className={`relative rounded-xl border bg-card p-5 flex flex-col gap-4 transition-shadow ${
        highlight
          ? "border-primary/40 shadow-md ring-1 ring-primary/20"
          : "border-border hover:border-stone-300 hover:shadow-sm"
      }`}
    >
      {highlight && (
        <div className="absolute -top-3 left-1/2 -translate-x-1/2">
          <span className="rounded-full bg-primary px-3 py-0.5 text-xs font-semibold text-primary-foreground shadow-sm">
            Most Popular
          </span>
        </div>
      )}

      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2.5">
          <div className={`rounded-lg p-1.5 ${highlight ? "bg-primary/10" : "bg-muted"}`}>
            <Icon className={`h-4 w-4 ${highlight ? "text-primary" : "text-muted-foreground"}`} />
          </div>
          <div>
            <p className="font-semibold text-sm">{plan.name}</p>
            <p className="text-xs text-muted-foreground">
              {plan.slug === "free" && "For individuals getting started"}
              {plan.slug === "starter" && "For small teams"}
              {plan.slug === "pro" && "For growing businesses"}
              {plan.slug === "business" && "For enterprises"}
            </p>
          </div>
        </div>
        {isCurrent && subscriptionStatus && (
          <CurrentBadge status={subscriptionStatus} />
        )}
      </div>

      <div>
        <span className="font-display text-3xl font-bold">${plan.priceMonthly}</span>
        <span className="text-muted-foreground text-sm">/mo</span>
      </div>

      <ul className="space-y-1.5 flex-1">
        {features.map((f) => (
          <li key={f} className="flex items-center gap-2 text-sm text-muted-foreground">
            <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-emerald-500" />
            {f}
          </li>
        ))}
      </ul>

      {isCurrent ? (
        <Button variant="outline" disabled className="w-full text-sm">
          Current Plan
        </Button>
      ) : isUpgrade ? (
        <Button className="w-full text-sm" onClick={() => onUpgrade(plan.slug)}>
          Upgrade to {plan.name}
        </Button>
      ) : (
        <Button
          variant="ghost"
          className="w-full text-sm text-muted-foreground"
          onClick={() => onDowngrade(plan.slug)}
        >
          Downgrade to {plan.name}
        </Button>
      )}
    </div>
  )
}

// ─── Current badge (inline — tiny, no separate file warranted) ────────────────

function CurrentBadge({ status }: { status: SubscriptionStatus }) {
  const map: Record<SubscriptionStatus, { label: string; className: string }> = {
    TRIALING: { label: "Trial", className: "bg-primary/10 text-primary border-primary/20" },
    ACTIVE: { label: "Active", className: "bg-emerald-50 text-emerald-700 border-emerald-200" },
    PAST_DUE: { label: "Past Due", className: "bg-red-50 text-red-700 border-red-200" },
    CANCELED: { label: "Canceled", className: "bg-stone-100 text-stone-500 border-stone-200" },
    UNPAID: { label: "Unpaid", className: "bg-red-50 text-red-700 border-red-200" },
  }
  const cfg = map[status]
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${cfg.className}`}
    >
      {cfg.label}
    </span>
  )
}
