import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { listMembers } from "@/lib/memberApi"
import { MembersTable } from "@/components/members/MembersTable"
import { MembersEmptyState } from "@/components/members/MembersEmptyState"
import { InviteModal } from "@/components/members/InviteModal"
import { RemoveConfirmDialog } from "@/components/members/RemoveConfirmDialog"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/store/authStore"
import type { MemberResponse } from "@/types/member"

const PAGE_SIZE = 10

export function MembersPage() {
  const [page, setPage] = useState(0)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [removeTarget, setRemoveTarget] = useState<MemberResponse | null>(null)
  const user = useAuthStore((s) => s.user)

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
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Members</h1>
        {user?.role === "OWNER" && (
          <Button onClick={() => setInviteOpen(true)}>Invite Member</Button>
        )}
      </div>
      <InviteModal open={inviteOpen} onOpenChange={setInviteOpen} />
      <RemoveConfirmDialog
        member={removeTarget}
        onOpenChange={(v) => { if (!v) setRemoveTarget(null) }}
      />

      {members?.length === 0 ? (
        <MembersEmptyState />
      ) : (
        <>
          <MembersTable
            members={paged}
            canMutate={user?.role === "OWNER"}
            currentUserId={user?.id ?? ""}
            onRemove={setRemoveTarget}
          />
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
