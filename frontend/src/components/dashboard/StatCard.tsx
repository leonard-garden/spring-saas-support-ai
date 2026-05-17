import { Card, CardContent } from "@/components/ui/card"

interface StatCardProps {
  label: string
  value: number | string
}

export function StatCard({ label, value }: StatCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <p className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-1">
          {label}
        </p>
        <p className="font-display text-4xl font-bold text-foreground">{value}</p>
      </CardContent>
    </Card>
  )
}
