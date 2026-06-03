import { useState } from "react"
import { Zap, Shield, Building2, CheckCircle2, AlertCircle, TrendingUp } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { CurrentPlanCard } from "@/components/billing/CurrentPlanCard"
import { StatusBanners } from "@/components/billing/StatusBanners"
import { UsageCard } from "@/components/billing/UsageCard"
import { InvoiceTable } from "@/components/billing/InvoiceTable"


const PLANS = [
  {
    slug: "free",
    name: "Free",
    price: 0,
    icon: Shield,
    description: "For individuals getting started",
    features: ["1 Knowledge Base", "5 Documents", "100 Messages/mo", "1 Member"],
    limits: { kbs: 1, docs: 5, messages: 100, members: 1 },
    highlight: false,
  },
  {
    slug: "starter",
    name: "Starter",
    price: 29,
    icon: Zap,
    description: "For small teams",
    features: ["3 Knowledge Bases", "50 Documents/KB", "1,000 Messages/mo", "3 Members"],
    limits: { kbs: 3, docs: 50, messages: 1000, members: 3 },
    highlight: false,
  },
  {
    slug: "pro",
    name: "Pro",
    price: 99,
    icon: TrendingUp,
    description: "For growing businesses",
    features: ["10 Knowledge Bases", "500 Documents/KB", "10,000 Messages/mo", "10 Members"],
    limits: { kbs: 10, docs: 500, messages: 10000, members: 10 },
    highlight: true,
  },
  {
    slug: "business",
    name: "Business",
    price: 299,
    icon: Building2,
    description: "For enterprises",
    features: ["Unlimited KBs", "Unlimited Documents", "100,000 Messages/mo", "Unlimited Members"],
    limits: { kbs: -1, docs: -1, messages: 100000, members: -1 },
    highlight: false,
  },
]

// ─── Plan card ────────────────────────────────────────────────────────────────

function PlanCard({
  plan,
  isCurrent,
  currentPlanPrice,
  onSelect,
}: {
  plan: (typeof PLANS)[0]
  isCurrent: boolean
  currentPlanPrice: number
  onSelect: (slug: string, direction: "upgrade" | "downgrade") => void
}) {
  const Icon = plan.icon
  const isUpgrade = plan.price > currentPlanPrice
  const isDowngrade = plan.price < currentPlanPrice

  return (
    <div
      className={`relative rounded-xl border bg-card p-5 flex flex-col gap-4 transition-shadow ${
        plan.highlight
          ? "border-primary/40 shadow-md ring-1 ring-primary/20"
          : "border-border hover:border-stone-300 hover:shadow-sm"
      }`}
    >
      {plan.highlight && (
        <div className="absolute -top-3 left-1/2 -translate-x-1/2">
          <span className="rounded-full bg-primary px-3 py-0.5 text-xs font-semibold text-primary-foreground shadow-sm">
            Most Popular
          </span>
        </div>
      )}

      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2.5">
          <div className={`rounded-lg p-1.5 ${plan.highlight ? "bg-primary/10" : "bg-muted"}`}>
            <Icon className={`h-4 w-4 ${plan.highlight ? "text-primary" : "text-muted-foreground"}`} />
          </div>
          <div>
            <p className="font-semibold text-sm">{plan.name}</p>
            <p className="text-xs text-muted-foreground">{plan.description}</p>
          </div>
        </div>
        {isCurrent && (
          <span className="inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium bg-primary/10 text-primary border-primary/20">
            Current
          </span>
        )}
      </div>

      <div>
        <span className="font-display text-3xl font-bold">${plan.price}</span>
        <span className="text-muted-foreground text-sm">/mo</span>
      </div>

      <ul className="space-y-1.5 flex-1">
        {plan.features.map((f) => (
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
        <Button
          className="w-full text-sm"
          onClick={() => onSelect(plan.slug, "upgrade")}
        >
          Upgrade to {plan.name}
        </Button>
      ) : (
        <Button
          variant="outline"
          className="w-full text-sm text-muted-foreground"
          onClick={() => onSelect(plan.slug, "downgrade")}
        >
          Downgrade to {plan.name}
        </Button>
      )}
    </div>
  )
}

// ─── Page ────────────────────────────────────────────────────────────────────

export function BillingPage() {
  const [selectedPlan, setSelectedPlan] = useState<{ slug: string; direction: "upgrade" | "downgrade" } | null>(null)
  const [showCancelConfirm, setShowCancelConfirm] = useState(false)

  const currentPlan = PLANS[2] // Pro — kept for plans grid comparison; usage card still uses mock

  function handlePlanSelect(slug: string, direction: "upgrade" | "downgrade") {
    setSelectedPlan({ slug, direction })
  }

  return (
    <div className="space-y-8 max-w-5xl">

      {/* Header */}
      <div>
        <h1 className="text-2xl font-semibold">Billing & Plans</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage your subscription and track usage
        </p>
      </div>

      {/* Status banners (trial + past-due, dismissible) */}
      <StatusBanners />

      {/* Current plan + Usage side by side */}
      <div className="grid grid-cols-5 gap-4">

        {/* Current plan card — real API data */}
        <CurrentPlanCard onCancelClick={() => setShowCancelConfirm(true)} />

        {/* Usage card — real API data */}
        <UsageCard className="col-span-3" />
      </div>

      {/* Plans section */}
      <div className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold">Available Plans</h2>
          <p className="text-sm text-muted-foreground">Upgrade or downgrade at any time. Downgrades take effect at the next billing cycle.</p>
        </div>

        <div className="grid grid-cols-4 gap-4 pt-3">
          {PLANS.map((plan) => (
            <PlanCard
              key={plan.slug}
              plan={plan}
              isCurrent={plan.slug === currentPlan.slug}
              currentPlanPrice={currentPlan.price}
              onSelect={handlePlanSelect}
            />
          ))}
        </div>
      </div>

      {/* Upgrade dialog */}
      {selectedPlan && selectedPlan.direction === "upgrade" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
            <div className="space-y-1">
              <h3 className="font-semibold text-base">Upgrade to {PLANS.find(p => p.slug === selectedPlan.slug)?.name}</h3>
              <p className="text-sm text-muted-foreground">
                You'll be redirected to Stripe to complete your payment. The upgrade takes effect immediately.
              </p>
            </div>
            <div className="rounded-lg bg-muted p-3 text-sm space-y-1">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Current plan</span>
                <span>{currentPlan.name} (${currentPlan.price}/mo)</span>
              </div>
              <div className="flex justify-between font-medium">
                <span className="text-muted-foreground">New plan</span>
                <span className="text-primary">{PLANS.find(p => p.slug === selectedPlan.slug)?.name} (${PLANS.find(p => p.slug === selectedPlan.slug)?.price}/mo)</span>
              </div>
            </div>
            <div className="flex gap-2 pt-1">
              <Button variant="outline" className="flex-1" onClick={() => setSelectedPlan(null)}>Cancel</Button>
              <Button className="flex-1">Continue to Stripe →</Button>
            </div>
          </div>
        </div>
      )}

      {/* Downgrade dialog */}
      {selectedPlan && selectedPlan.direction === "downgrade" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
            <div className="space-y-1">
              <h3 className="font-semibold text-base">Downgrade to {PLANS.find(p => p.slug === selectedPlan.slug)?.name}?</h3>
              <p className="text-sm text-muted-foreground">
                Your plan will change at the <span className="font-medium">end of the current billing cycle</span>. You keep full access until then.
              </p>
            </div>
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 space-y-1">
              <p className="font-medium flex items-center gap-1.5"><AlertCircle className="h-3.5 w-3.5" /> What you'll lose</p>
              <ul className="pl-5 space-y-0.5 list-disc text-amber-700">
                <li>Reduced KB and document limits</li>
                <li>Fewer messages per month</li>
                <li>Fewer team members</li>
              </ul>
            </div>
            <div className="flex gap-2 pt-1">
              <Button variant="outline" className="flex-1" onClick={() => setSelectedPlan(null)}>Keep Current Plan</Button>
              <Button variant="destructive" className="flex-1">Confirm Downgrade</Button>
            </div>
          </div>
        </div>
      )}

      {/* Invoice history */}
      <InvoiceTable />

      {/* Cancel dialog */}
      {showCancelConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
            <div className="space-y-1">
              <h3 className="font-semibold text-base">Cancel Subscription?</h3>
              <p className="text-sm text-muted-foreground">
                Your subscription will be cancelled at the end of the current billing period.
                You'll have full access until then.
              </p>
            </div>
            <div className="rounded-lg bg-muted p-3 text-sm text-muted-foreground">
              After cancellation, your account will downgrade to the <span className="font-medium text-foreground">Free plan</span>.
            </div>
            <div className="flex gap-2 pt-1">
              <Button variant="outline" className="flex-1" onClick={() => setShowCancelConfirm(false)}>Keep Subscription</Button>
              <Button variant="destructive" className="flex-1">Cancel at Period End</Button>
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
