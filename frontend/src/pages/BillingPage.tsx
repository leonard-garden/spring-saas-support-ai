import { useState } from "react"
import { CurrentPlanCard } from "@/components/billing/CurrentPlanCard"
import { StatusBanners } from "@/components/billing/StatusBanners"
import { UsageCard } from "@/components/billing/UsageCard"
import { InvoiceTable } from "@/components/billing/InvoiceTable"
import { PlanGrid } from "@/components/billing/PlanGrid"
import { CancelDialog } from "@/components/billing/CancelDialog"
import { UpgradeDialog } from "@/components/billing/UpgradeDialog"
import { DowngradeDialog } from "@/components/billing/DowngradeDialog"

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
      <DowngradeDialog
        open={selectedPlan?.direction === "downgrade"}
        planSlug={selectedPlan?.direction === "downgrade" ? selectedPlan.slug : null}
        onClose={() => setSelectedPlan(null)}
      />

      {/* Invoice history */}
      <InvoiceTable />

      {/* Cancel subscription dialog */}
      <CancelDialog open={showCancelConfirm} onClose={() => setShowCancelConfirm(false)} />

    </div>
  )
}
