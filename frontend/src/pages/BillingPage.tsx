import { useState } from "react"
import { CreditCard, Zap, Shield, Building2, CheckCircle2, AlertCircle, Clock, TrendingUp, Users, FileText, MessageSquare, BookOpen, Download, ExternalLink } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"

// ─── Mock data (replace with API calls later) ────────────────────────────────

const MOCK_SUBSCRIPTION = {
  status: "TRIALING",
  planSlug: "pro",
  planName: "Pro",
  trialEndsAt: new Date(Date.now() + 8 * 24 * 60 * 60 * 1000).toISOString(),
  currentPeriodEnd: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
  cancelAtPeriodEnd: false,
}

const MOCK_INVOICES = [
  { id: "in_001", date: "2026-05-01", amount: 99, status: "paid", description: "Pro Plan — May 2026", pdfUrl: "#" },
  { id: "in_002", date: "2026-04-01", amount: 99, status: "paid", description: "Pro Plan — Apr 2026", pdfUrl: "#" },
  { id: "in_003", date: "2026-03-01", amount: 29, status: "paid", description: "Starter Plan — Mar 2026", pdfUrl: "#" },
  { id: "in_004", date: "2026-02-01", amount: 29, status: "paid", description: "Starter Plan — Feb 2026", pdfUrl: "#" },
  { id: "in_005", date: "2026-01-01", amount: 29, status: "failed", description: "Starter Plan — Jan 2026", pdfUrl: "#" },
]

const MOCK_USAGE = {
  knowledgeBases: { used: 3, limit: 10 },
  documents: { used: 127, limit: 500 },
  messages: { used: 2340, limit: 10000 },
  members: { used: 4, limit: 10 },
}

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

// ─── Helpers ─────────────────────────────────────────────────────────────────

function daysUntil(iso: string) {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86400000))
}

function usagePercent(used: number, limit: number) {
  if (limit === -1) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

function formatNumber(n: number) {
  return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n)
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { label: string; className: string }> = {
    TRIALING: { label: "Trial", className: "bg-primary/10 text-primary border-primary/20" },
    ACTIVE: { label: "Active", className: "bg-emerald-50 text-emerald-700 border-emerald-200" },
    PAST_DUE: { label: "Past Due", className: "bg-red-50 text-red-700 border-red-200" },
    CANCELED: { label: "Canceled", className: "bg-stone-100 text-stone-500 border-stone-200" },
    UNPAID: { label: "Unpaid", className: "bg-red-50 text-red-700 border-red-200" },
  }
  const cfg = map[status] ?? { label: status, className: "bg-stone-100 text-stone-500" }
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${cfg.className}`}>
      {cfg.label}
    </span>
  )
}

// ─── Usage meter ─────────────────────────────────────────────────────────────

function UsageMeter({
  icon: Icon,
  label,
  used,
  limit,
}: {
  icon: React.ElementType
  label: string
  used: number
  limit: number
}) {
  const pct = usagePercent(used, limit)
  const isUnlimited = limit === -1
  const isWarning = pct >= 80
  const isCritical = pct >= 95

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="flex items-center gap-1.5 text-muted-foreground">
          <Icon className="h-3.5 w-3.5" />
          {label}
        </span>
        <span className={`font-medium tabular-nums ${isCritical ? "text-red-600" : isWarning ? "text-amber-600" : "text-foreground"}`}>
          {isUnlimited ? (
            <span className="text-muted-foreground text-xs">Unlimited</span>
          ) : (
            `${formatNumber(used)} / ${formatNumber(limit)}`
          )}
        </span>
      </div>
      {!isUnlimited && (
        <Progress
          value={pct}
          className={`h-1.5 ${isCritical ? "[&>div]:bg-red-500" : isWarning ? "[&>div]:bg-amber-500" : "[&>div]:bg-primary"}`}
        />
      )}
    </div>
  )
}

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
        {isCurrent && <StatusBadge status={MOCK_SUBSCRIPTION.status} />}
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
  const sub = MOCK_SUBSCRIPTION
  const usage = MOCK_USAGE
  const [selectedPlan, setSelectedPlan] = useState<{ slug: string; direction: "upgrade" | "downgrade" } | null>(null)
  const [showCancelConfirm, setShowCancelConfirm] = useState(false)

  const currentPlan = PLANS.find((p) => p.slug === sub.planSlug) ?? PLANS[0]
  const daysLeft = sub.status === "TRIALING" ? daysUntil(sub.trialEndsAt) : null

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

      {/* Trial banner */}
      {sub.status === "TRIALING" && (
        <div className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/5 px-4 py-3">
          <Clock className="h-4 w-4 text-primary shrink-0" />
          <p className="text-sm">
            <span className="font-semibold text-primary">{daysLeft} days left</span>
            <span className="text-muted-foreground"> on your Pro trial. Upgrade to keep access after your trial ends.</span>
          </p>
          <Button size="sm" className="ml-auto shrink-0">
            Upgrade Now
          </Button>
        </div>
      )}

      {/* Past due banner */}
      {sub.status === "PAST_DUE" && (
        <div className="flex items-center gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
          <p className="text-sm text-red-700">
            <span className="font-semibold">Payment failed.</span> Update your payment method to avoid service interruption.
          </p>
          <Button size="sm" variant="destructive" className="ml-auto shrink-0">
            Update Payment
          </Button>
        </div>
      )}

      {/* Current plan + Usage side by side */}
      <div className="grid grid-cols-5 gap-4">

        {/* Current plan card */}
        <Card className="col-span-2">
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Current Plan</CardTitle>
              <StatusBadge status={sub.status} />
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center gap-2.5">
              <div className="rounded-lg bg-primary/10 p-2">
                <TrendingUp className="h-5 w-5 text-primary" />
              </div>
              <div>
                <p className="font-display text-xl font-bold">{currentPlan.name}</p>
                <p className="text-xs text-muted-foreground">${currentPlan.price}/month</p>
              </div>
            </div>

            <div className="space-y-1 text-sm">
              {sub.status === "TRIALING" && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Trial ends</span>
                  <span className="font-medium text-primary">{daysLeft} days</span>
                </div>
              )}
              <div className="flex justify-between">
                <span className="text-muted-foreground">Period ends</span>
                <span className="font-medium">{new Date(sub.currentPeriodEnd).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}</span>
              </div>
              {sub.cancelAtPeriodEnd && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Cancels</span>
                  <span className="font-medium text-red-600">At period end</span>
                </div>
              )}
            </div>

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
                  onClick={() => setShowCancelConfirm(true)}
                >
                  Cancel Subscription
                </Button>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Usage card */}
        <Card className="col-span-3">
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base">Usage This Period</CardTitle>
              <span className="text-xs text-muted-foreground">Resets {new Date(sub.currentPeriodEnd).toLocaleDateString("en-US", { month: "short", day: "numeric" })}</span>
            </div>
            <CardDescription className="text-xs">
              Paid plans include a 10% grace period above limits
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <UsageMeter icon={BookOpen} label="Knowledge Bases" used={usage.knowledgeBases.used} limit={usage.knowledgeBases.limit} />
            <UsageMeter icon={FileText} label="Documents" used={usage.documents.used} limit={usage.documents.limit} />
            <UsageMeter icon={MessageSquare} label="Messages" used={usage.messages.used} limit={usage.messages.limit} />
            <UsageMeter icon={Users} label="Members" used={usage.members.used} limit={usage.members.limit} />
          </CardContent>
        </Card>
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
              isCurrent={plan.slug === sub.planSlug}
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
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold">Payment History</h2>
            <p className="text-sm text-muted-foreground">All invoices for your account</p>
          </div>
        </div>

        <Card>
          <CardContent className="p-0">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/40">
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Date</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Description</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Amount</th>
                  <th className="px-4 py-3 text-left font-medium text-muted-foreground">Status</th>
                  <th className="px-4 py-3 text-right font-medium text-muted-foreground">Invoice</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {MOCK_INVOICES.map((inv) => (
                  <tr key={inv.id} className="hover:bg-muted/20 transition-colors">
                    <td className="px-4 py-3 text-muted-foreground tabular-nums">
                      {new Date(inv.date).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}
                    </td>
                    <td className="px-4 py-3 font-medium">{inv.description}</td>
                    <td className="px-4 py-3 tabular-nums">${inv.amount.toFixed(2)}</td>
                    <td className="px-4 py-3">
                      {inv.status === "paid" ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 border border-emerald-200 px-2 py-0.5 text-xs font-medium text-emerald-700">
                          <CheckCircle2 className="h-3 w-3" /> Paid
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full bg-red-50 border border-red-200 px-2 py-0.5 text-xs font-medium text-red-700">
                          <AlertCircle className="h-3 w-3" /> Failed
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <a
                        href={inv.pdfUrl}
                        className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
                      >
                        <Download className="h-3.5 w-3.5" />
                        PDF
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {MOCK_INVOICES.length === 0 && (
              <div className="py-12 text-center text-sm text-muted-foreground">
                No invoices yet
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Cancel dialog */}
      {showCancelConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
            <div className="space-y-1">
              <h3 className="font-semibold text-base">Cancel Subscription?</h3>
              <p className="text-sm text-muted-foreground">
                Your subscription will be cancelled at the end of the current period on{" "}
                <span className="font-medium">{new Date(sub.currentPeriodEnd).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}</span>.
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
