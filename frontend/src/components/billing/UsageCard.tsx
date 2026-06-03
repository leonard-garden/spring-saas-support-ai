import React from "react"
import { BookOpen, FileText, MessageSquare, Users } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Skeleton } from "@/components/ui/skeleton"
import { useUsage, useSubscription } from "@/hooks/useBilling"

// ─── Helpers ─────────────────────────────────────────────────────────────────

function usagePercent(used: number, limit: number): number {
  if (limit === -1) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

function formatNumber(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n)
}

// ─── UsageMeter ───────────────────────────────────────────────────────────────

interface UsageMeterProps {
  icon: React.ElementType
  label: string
  used: number
  limit: number
}

function UsageMeter({ icon: Icon, label, used, limit }: UsageMeterProps) {
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
        <span
          className={`font-medium tabular-nums ${
            isCritical ? "text-red-600" : isWarning ? "text-amber-600" : "text-foreground"
          }`}
        >
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
          className={`h-1.5 ${
            isCritical
              ? "[&>div]:bg-red-500"
              : isWarning
              ? "[&>div]:bg-amber-500"
              : "[&>div]:bg-primary"
          }`}
        />
      )}
    </div>
  )
}

// ─── Skeleton state ───────────────────────────────────────────────────────────

function UsageCardSkeleton() {
  return (
    <Card className="col-span-3">
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-3 w-24" />
        </div>
        <Skeleton className="h-3 w-48 mt-1" />
      </CardHeader>
      <CardContent className="space-y-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Skeleton className="h-3 w-28" />
              <Skeleton className="h-3 w-16" />
            </div>
            <Skeleton className="h-1.5 w-full" />
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

// ─── Fallback metric when API errors ─────────────────────────────────────────

const FALLBACK_METRIC = { used: 0, limit: 0 }
const FALLBACK_DISPLAY = "--"

function FallbackMeter({ icon: Icon, label }: { icon: React.ElementType; label: string }) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="flex items-center gap-1.5 text-muted-foreground">
          <Icon className="h-3.5 w-3.5" />
          {label}
        </span>
        <span className="font-medium tabular-nums text-muted-foreground">{FALLBACK_DISPLAY}</span>
      </div>
    </div>
  )
}

// ─── UsageCard ────────────────────────────────────────────────────────────────

export interface UsageCardProps {
  className?: string
}

export function UsageCard({ className }: UsageCardProps) {
  const { data: usage, isLoading: usageLoading, isError: usageError } = useUsage()
  const { data: subscription, isLoading: subLoading } = useSubscription()

  const isLoading = usageLoading || subLoading

  if (isLoading) {
    return <UsageCardSkeleton />
  }

  const resetLabel =
    subscription?.currentPeriodEnd
      ? `Resets ${new Date(subscription.currentPeriodEnd).toLocaleDateString("en-US", {
          month: "short",
          day: "numeric",
        })}`
      : null

  const meters: Array<{ icon: React.ElementType; label: string; key: keyof typeof usage }> = [
    { icon: BookOpen, label: "Knowledge Bases", key: "knowledgeBases" },
    { icon: FileText, label: "Documents", key: "documents" },
    { icon: MessageSquare, label: "Messages", key: "messages" },
    { icon: Users, label: "Members", key: "members" },
  ]

  return (
    <Card className={className}>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Usage This Period</CardTitle>
          {resetLabel && (
            <span className="text-xs text-muted-foreground">{resetLabel}</span>
          )}
        </div>
        <CardDescription className="text-xs">
          Paid plans include a 10% grace period above limits
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {usageError || !usage ? (
          <>
            <FallbackMeter icon={BookOpen} label="Knowledge Bases" />
            <FallbackMeter icon={FileText} label="Documents" />
            <FallbackMeter icon={MessageSquare} label="Messages" />
            <FallbackMeter icon={Users} label="Members" />
          </>
        ) : (
          meters.map(({ icon, label, key }) => {
            const metric = usage[key] ?? FALLBACK_METRIC
            return (
              <UsageMeter
                key={key}
                icon={icon}
                label={label}
                used={metric.used}
                limit={metric.limit}
              />
            )
          })
        )}
      </CardContent>
    </Card>
  )
}
