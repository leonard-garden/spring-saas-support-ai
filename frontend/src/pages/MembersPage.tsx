import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { listMembers } from "@/lib/memberApi"
import { MembersTable } from "@/components/members/MembersTable"
import { MembersEmptyState } from "@/components/members/MembersEmptyState"
import { Button } from "@/components/ui/button"

const PAGE_SIZE = 10

export function MembersPage() {
  const [page, setPage] = useState(0)

  const { data: members, isLoading, isError } = useQuery({
    queryKey: ["members"],
    queryFn: listMembers,
  })

  if (isLoading) {
    return <p className="text-muted-foreground">Loading members…</p>
  }

  if (isError) {
    return <p className="text-destructive">Failed to load members.</p>
  }

  const totalPages = Math.ceil((members?.length ?? 0) / PAGE_SIZE)
  const paged = (members ?? []).slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold">Members</h1>

      {members?.length === 0 ? (
        <MembersEmptyState />
      ) : (
        <>
          <MembersTable members={paged} />
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Prev
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
