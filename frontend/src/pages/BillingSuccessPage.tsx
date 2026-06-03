import { CheckCircle2 } from "lucide-react"
import { useNavigate, useSearchParams } from "react-router-dom"
import { Button } from "@/components/ui/button"

export function BillingSuccessPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const planName = searchParams.get("plan_name")

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] space-y-6 text-center px-4">
      <CheckCircle2 className="h-16 w-16 text-green-500" />

      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">Your subscription is now active</h1>
        {planName && (
          <p className="text-muted-foreground">
            Welcome to the <span className="font-medium text-foreground">{planName}</span> plan.
          </p>
        )}
      </div>

      <p className="text-sm text-muted-foreground max-w-sm">
        It may take a few seconds for your plan to activate while we process the confirmation from
        Stripe.
      </p>

      <Button onClick={() => navigate("/dashboard")}>Go to Dashboard</Button>
    </div>
  )
}
