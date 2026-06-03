import { useState } from "react"
import { CurrentPlanCard } from "@/components/billing/CurrentPlanCard"
import { StatusBanners } from "@/components/billing/StatusBanners"
import { UsageCard } from "@/components/billing/UsageCard"
import { InvoiceTable } from "@/components/billing/InvoiceTable"
import { PlanGrid } from "@/components/billing/PlanGrid"
import { CancelDialog } from "@/components/billing/CancelDialog"
import { UpgradeDialog } from "@/components/billing/UpgradeDialog"

// ─── Page ────────────────────────────────────────────────────────────────────

export function BillingPage() {
  const [selectedPlan, setSelectedPlan] = useState<{ slug: string; direction: "upgrade" | "downgrade" } | null>(null)
  const [showCancelConfirm, setShowCancelConfirm] = useState(false)

  return (
    <div className="space-y-8 max-w-5xl">

      {/* Header */}
      <div>
        <h1 className="text-2xl font-semibold">Billing &amp; Plans</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Manage your subscription and track usage
        </p>
      </div>

      {/* Trial / past-due banners */}
      <StatusBanners />

      {/* Current plan + Usage side by side */}
      <div className="grid grid-cols-5 gap-4">
        <CurrentPlanCard onCancelClick={() => setShowCancelConfirm(true)} />
        <UsageCard className="col-span-3" />
      </div>

      {/* Plans section */}
      <div className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold">Available Plans</h2>
          <p className="text-sm text-muted-foreground">
            Upgrade or downgrade at any time. Downgrades take effect at the next billing cycle.
          </p>
        </div>

        <PlanGrid
          onUpgrade={(slug) => setSelectedPlan({ slug, direction: "upgrade" })}
          onDowngrade={(slug) => setSelectedPlan({ slug, direction: "downgrade" })}
        />
      </div>

      {/* Upgrade dialog */}
      <UpgradeDialog
        open={selectedPlan?.direction === "upgrade"}
        planSlug={selectedPlan?.direction === "upgrade" ? selectedPlan.slug : null}
        onClose={() => setSelectedPlan(null)}
      />

      {/* Downgrade dialog */}
      {selectedPlan && selectedPlan.direction === "downgrade" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
            <div className="space-y-1">
              <h3 className="font-semibold text-base">Downgrade to {selectedPlan.slug}?</h3>
              <p className="text-sm text-muted-foreground">
                Your plan will change at the{" "}
                <span className="font-medium">end of the current billing cycle</span>. You keep full
                access until then.
              </p>
            </div>
            <div className="flex gap-2 pt-1">
              <button
                className="flex-1 rounded-md border px-4 py-2 text-sm font-medium hover:bg-muted transition-colors"
                onClick={() => setSelectedPlan(null)}
              >
                Keep Current Plan
              </button>
              <button className="flex-1 rounded-md bg-destructive px-4 py-2 text-sm font-medium text-destructive-foreground hover:bg-destructive/90 transition-colors">
                Confirm Downgrade
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Invoice history */}
      <InvoiceTable />

      {/* Cancel subscription dialog */}
      <CancelDialog open={showCancelConfirm} onClose={() => setShowCancelConfirm(false)} />

    </div>
  )
}
