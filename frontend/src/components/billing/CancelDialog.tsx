import { Loader2 } from "lucide-react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { cancelSubscription } from "@/lib/billingApi"
import { useSubscription } from "@/hooks/useBilling"

interface CancelDialogProps {
  open: boolean
  onClose: () => void
}

export function CancelDialog({ open, onClose }: CancelDialogProps) {
  const queryClient = useQueryClient()
  const { data: subscription } = useSubscription()

  const mutation = useMutation({
    mutationFn: cancelSubscription,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["billing", "subscription"] })
      onClose()
    },
  })

  if (!open) return null

  const periodEndDate = subscription?.currentPeriodEnd
    ? new Date(subscription.currentPeriodEnd).toLocaleDateString("en-US", {
        month: "long",
        day: "numeric",
        year: "numeric",
      })
    : null

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
          <h3 className="font-semibold text-base">Cancel Subscription?</h3>
          <p className="text-sm text-muted-foreground">
            Your subscription will be cancelled on{" "}
            {periodEndDate ? (
              <span className="font-medium text-foreground">{periodEndDate}</span>
            ) : (
              "the end of the current billing period"
            )}
            . You'll have full access until then.
          </p>
        </div>

        <div className="rounded-lg bg-muted p-3 text-sm text-muted-foreground">
          After cancellation, your account moves to the{" "}
          <span className="font-medium text-foreground">Free plan</span>.
        </div>

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
            Keep Subscription
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
                Cancelling…
              </>
            ) : (
              "Cancel at Period End"
            )}
          </Button>
        </div>
      </div>
    </div>
  )
}
