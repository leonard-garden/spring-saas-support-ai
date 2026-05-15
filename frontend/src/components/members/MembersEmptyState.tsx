import { Card, CardContent } from "@/components/ui/card"

export function MembersEmptyState() {
  return (
    <Card>
      <CardContent className="py-10 text-center">
        <p className="text-muted-foreground">No members yet.</p>
      </CardContent>
    </Card>
  )
}
