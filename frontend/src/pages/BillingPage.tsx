import { useState } from "react"
import { AlertCircle } from "lucide-react"
import { Button } from "@/components/ui/button"
import { CurrentPlanCard } from "@/components/billing/CurrentPlanCard"
import { StatusBanners } from "@/components/billing/StatusBanners"
import { UsageCard } from "@/components/billing/UsageCard"
import { InvoiceTable } from "@/components/billing/InvoiceTable"
import { PlanGrid } from "@/components/billing/PlanGrid"

// ─── Page ────────────────────────────────────────────────────────────────────

export function BillingPage() {
  const [selectedPlan, setSelectedPlan] = useState<{ slug: string; direction: "upgrade" | "downgrade" } | null>(null)
  const [showCancelConfirm, setShowCancelConfirm] = useState(false)

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

        <PlanGrid
          onUpgrade={(slug) => setSelectedPlan({ slug, direction: "upgrade" })}
          onDowngrade={(slug) => setSelectedPlan({ slug, direction: "downgrade" })}
        />
      </div>

      {/* Upgrade dialog */}
      {selectedPlan && selectedPlan.direction === "upgrade" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
            <div className="space-y-1">
              <h3 className="font-semibold text-base">Upgrade to {selectedPlan.slug}</h3>
              <p className="text-sm text-muted-foreground">
                You'll be redirected to Stripe to complete your payment. The upgrade takes effect immediately.
              </p>
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
              <h3 className="font-semibold text-base">Downgrade to {selectedPlan.slug}?</h3>
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
