import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"

export function KbPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Knowledge Base</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Train your AI chatbot on your documentation, FAQs, and product info.
          </p>
        </div>
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              {/* span wrapper required: disabled buttons do not fire pointer events */}
              <span tabIndex={0}>
                <Button disabled aria-disabled="true">
                  Add Knowledge Base
                </Button>
              </span>
            </TooltipTrigger>
            <TooltipContent>Available in M2</TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>

      <Card>
        <CardContent className="flex flex-col items-center justify-center gap-2 py-16 text-center">
          <p className="text-base font-medium">No knowledge bases yet</p>
          <p className="text-sm text-muted-foreground">
            Knowledge base creation is coming in M2. Stay tuned.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
