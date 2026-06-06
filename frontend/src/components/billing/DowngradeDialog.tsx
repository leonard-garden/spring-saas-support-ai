import { AlertCircle, Loader2 } from "lucide-react"
import { isAxiosError } from "axios"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { downgradeSubscription } from "@/lib/billingApi"
import { usePlans, useSubscription } from "@/hooks/useBilling"
import type { Plan } from "@/types/billing"

// ─── Types ───────────────────────────────────────────────────────────────────

interface DowngradeDialogProps {
  open: boolean
  planSlug: string | null
  onClose: () => void
}

interface LossItem {
  label: string
  from: string
  to: string
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatLimit(value: number): string {
  if (value === -1) return "Unlimited"
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(0)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(0)}k`
  return String(value)
}

function buildLossItems(current: Plan, target: Plan): LossItem[] {
  const items: LossItem[] = []

  if (target.maxKnowledgeBases < current.maxKnowledgeBases) {
    items.push({
      label: "Knowledge bases",
      from: formatLimit(current.maxKnowledgeBases),
      to: formatLimit(target.maxKnowledgeBases),
    })
  }

  if (target.maxDocsPerKb < current.maxDocsPerKb) {
    items.push({
      label: "Documents per KB",
      from: formatLimit(current.maxDocsPerKb),
      to: formatLimit(target.maxDocsPerKb),
    })
  }

  if (target.maxMessagesPerMonth < current.maxMessagesPerMonth) {
    items.push({
      label: "Messages / month",
      from: formatLimit(current.maxMessagesPerMonth),
      to: formatLimit(target.maxMessagesPerMonth),
    })
  }

  if (target.maxMembers < current.maxMembers) {
    items.push({
      label: "Team members",
      from: formatLimit(current.maxMembers),
      to: formatLimit(target.maxMembers),
    })
  }

  return items
}

function getErrorMessage(error: unknown): string {
  if (isAxiosError(error) && error.response?.status === 400) {
    return "A downgrade is already scheduled"
  }
  if (error instanceof Error) {
    return error.message
  }
  return "An unexpected error occurred. Please try again."
}

// ─── Component ───────────────────────────────────────────────────────────────

export function DowngradeDialog({ open, planSlug, onClose }: DowngradeDialogProps) {
  const queryClient = useQueryClient()
  const { data: plans } = usePlans()
  const { data: subscription } = useSubscription()

  const mutation = useMutation({
    mutationFn: () => downgradeSubscription(planSlug!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["billing", "subscription"] })
      onClose()
    },
  })

  if (!open || !planSlug) return null

  const targetPlan = plans?.find((p) => p.slug === planSlug) ?? null
  const currentPlan = plans?.find((p) => p.slug === subscription?.planSlug) ?? null
  const lossItems: LossItem[] =
    currentPlan && targetPlan ? buildLossItems(currentPlan, targetPlan) : []

  const targetName = targetPlan?.name ?? planSlug
  const alreadyScheduled =
    subscription?.pendingPlanId != null ||
    (isAxiosError(mutation.error) && mutation.error.response?.status === 400)

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="w-full max-w-sm rounded-xl border bg-card p-6 shadow-xl space-y-4"
        onClick={(e) => e.stopPropagation()}
      >

        {/* Header */}
        <div className="space-y-1">
          <h3 className="font-semibold text-base">Downgrade to {targetName}?</h3>
          <p className="text-sm text-muted-foreground">
            Your plan will change at the{" "}
            <span className="font-medium text-foreground">end of the current billing cycle</span>.
            You keep full access until then.
          </p>
        </div>

        {alreadyScheduled ? (
          /* Blocked state — downgrade already pending */
          <>
            <div className="flex gap-2 rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>A downgrade is already scheduled for the end of this billing cycle. You cannot schedule another one until it takes effect.</span>
            </div>
            <Button variant="outline" className="w-full" onClick={onClose}>
              Close
            </Button>
          </>
        ) : (
          /* Normal confirmation flow */
          <>
            {/* Amber timing notice */}
            <div className="flex gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-300">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>Takes effect at the end of your current billing cycle.</span>
            </div>

            {/* What you'll lose */}
            {lossItems.length > 0 && (
              <div className="rounded-lg bg-muted p-3 space-y-2">
                <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                  Limits that will decrease
                </p>
                <ul className="space-y-1">
                  {lossItems.map((item) => (
                    <li key={item.label} className="flex items-center justify-between text-sm">
                      <span className="text-foreground">{item.label}</span>
                      <span className="text-muted-foreground">
                        <span className="line-through">{item.from}</span>
                        {" → "}
                        <span className="font-medium text-foreground">{item.to}</span>
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Inline error (non-400 errors only) */}
            {mutation.error != null && !alreadyScheduled && (
              <p className="text-sm text-destructive">{getErrorMessage(mutation.error)}</p>
            )}

            {/* Actions */}
            <div className="flex gap-2 pt-1">
              <Button
                variant="outline"
                className="flex-1"
                onClick={onClose}
                disabled={mutation.isPending}
              >
                Keep Current Plan
              </Button>
              <Button
                variant="destructive"
                className="flex-1"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate()}
              >
                {mutation.isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Scheduling…
                  </>
                ) : (
                  "Confirm Downgrade"
                )}
              </Button>
            </div>
          </>
        )}

      </div>
    </div>
  )
}
