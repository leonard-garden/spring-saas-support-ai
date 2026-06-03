import { Loader2 } from "lucide-react"
import { useMutation } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { createCheckoutSession } from "@/lib/billingApi"
import { usePlans, useSubscription } from "@/hooks/useBilling"

interface UpgradeDialogProps {
  open: boolean
  planSlug: string | null
  onClose: () => void
}

export function UpgradeDialog({ open, planSlug, onClose }: UpgradeDialogProps) {
  const { data: plans } = usePlans()
  const { data: subscription } = useSubscription()

  const mutation = useMutation({
    mutationFn: () => createCheckoutSession(planSlug!),
    onSuccess: (data) => {
      window.location.href = data.url
    },
  })

  if (!open || !planSlug) return null

  const targetPlan = plans?.find((p) => p.slug === planSlug)
  const currentPlan = plans?.find((p) => p.slug === subscription?.planSlug)

  const errorMessage =
    mutation.error instanceof Error
      ? mutation.error.message
      : mutation.error != null
        ? "An unexpected error occurred. Please try again."
        : null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4">
        <div className="space-y-1">
          <h3 className="font-semibold text-base">
            Upgrade to {targetPlan?.name ?? planSlug}
          </h3>
          <p className="text-sm text-muted-foreground">
            You'll be redirected to Stripe to complete your payment. The upgrade takes effect
            immediately.
          </p>
        </div>

        {currentPlan && targetPlan && (
          <div className="rounded-lg bg-muted p-3 text-sm space-y-1">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Current plan</span>
              <span className="font-medium">
                {currentPlan.name} —{" "}
                {currentPlan.priceMonthly === 0
                  ? "Free"
                  : `$${currentPlan.priceMonthly}/mo`}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">New plan</span>
              <span className="font-medium text-primary">
                {targetPlan.name} — ${targetPlan.priceMonthly}/mo
              </span>
            </div>
          </div>
        )}

        {errorMessage && (
          <p className="text-sm text-destructive">{errorMessage}</p>
        )}

        <div className="flex gap-2 pt-1">
          <Button
            variant="outline"
            className="flex-1"
            onClick={onClose}
            disabled={mutation.isPending}
          >
            Cancel
          </Button>
          <Button
            className="flex-1"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Redirecting…
              </>
            ) : (
              "Continue to Stripe →"
            )}
          </Button>
        </div>
      </div>
    </div>
  )
}
