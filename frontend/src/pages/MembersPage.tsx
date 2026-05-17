import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { listMembers } from "@/lib/memberApi"
import { listPendingInvitations } from "@/lib/invitationApi"
import { MembersTable } from "@/components/members/MembersTable"
import { PendingInvitationsTab } from "@/components/members/PendingInvitationsTab"
import { InviteModal } from "@/components/members/InviteModal"
import { RemoveConfirmDialog } from "@/components/members/RemoveConfirmDialog"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/store/authStore"
import type { MemberResponse } from "@/types/member"
import { cn } from "@/lib/utils"

const PAGE_SIZE = 10

export function MembersPage() {
  const [tab, setTab] = useState<"members" | "pending">("members")
  const [page, setPage] = useState(0)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [removeTarget, setRemoveTarget] = useState<MemberResponse | null>(null)
  const user = useAuthStore((s) => s.user)

  const canInvite = user?.role === "OWNER" || user?.role === "ADMIN"

  const { data: members = [], isLoading, isError } = useQuery({
    queryKey: ["members"],
    queryFn: listMembers,
  })

  const { data: pending = [] } = useQuery({
    queryKey: ["invitations-pending"],
    queryFn: listPendingInvitations,
    enabled: canInvite,
  })

  const totalPages = Math.ceil(members.length / PAGE_SIZE)
  const paged = members.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const start = page * PAGE_SIZE + 1
  const end = Math.min((page + 1) * PAGE_SIZE, members.length)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-stone-800">Members</h1>
        {canInvite && (
          <Button
            onClick={() => setInviteOpen(true)}
            className="bg-amber-500 hover:bg-amber-600 text-white"
          >
            + Invite Member
          </Button>
        )}
      </div>

      <InviteModal open={inviteOpen} onOpenChange={setInviteOpen} />
      <RemoveConfirmDialog
        member={removeTarget}
        onOpenChange={(v) => { if (!v) setRemoveTarget(null) }}
      />

      <div className="rounded-lg border border-stone-200 bg-white shadow-sm">
        {/* Tabs */}
        <div className="flex border-b border-stone-200 px-4">
          <button
            onClick={() => { setTab("members"); setPage(0) }}
            className={cn(
              "flex items-center gap-1.5 py-3 px-1 mr-6 text-sm font-medium border-b-2 -mb-px transition-colors",
              tab === "members"
                ? "border-amber-500 text-stone-800"
                : "border-transparent text-muted-foreground hover:text-stone-700"
            )}
          >
            Members
            <span className={cn(
              "inline-flex items-center justify-center w-5 h-5 rounded-full text-xs",
              tab === "members" ? "bg-stone-800 text-white" : "bg-stone-100 text-stone-600"
            )}>
              {members.length}
            </span>
          </button>
          {canInvite && (
            <button
              onClick={() => setTab("pending")}
              className={cn(
                "flex items-center gap-1.5 py-3 px-1 text-sm font-medium border-b-2 -mb-px transition-colors",
                tab === "pending"
                  ? "border-amber-500 text-stone-800"
                  : "border-transparent text-muted-foreground hover:text-stone-700"
              )}
            >
              Pending
              <span className={cn(
                "inline-flex items-center justify-center w-5 h-5 rounded-full text-xs",
                tab === "pending" ? "bg-stone-800 text-white" : "bg-stone-100 text-stone-600"
              )}>
                {pending.length}
              </span>
            </button>
          )}
        </div>

        {/* Content */}
        <div className="px-4 py-2">
          {tab === "members" && (
            <>
              {isLoading && <p className="py-4 text-sm text-muted-foreground">Loading members…</p>}
              {isError && <p className="py-4 text-sm text-destructive">Failed to load members.</p>}
              {!isLoading && !isError && members.length === 0 && (
                <div className="py-10 text-center">
                  <p className="text-4xl mb-3">👥</p>
                  <p className="text-sm font-medium text-stone-700">No members yet</p>
                  <p className="text-xs text-muted-foreground mb-4">Invite your team to collaborate.</p>
                  {canInvite && (
                    <Button variant="outline" size="sm" onClick={() => setInviteOpen(true)}>
                      + Invite Member
                    </Button>
                  )}
                </div>
              )}
              {!isLoading && members.length > 0 && (
                <MembersTable
                  members={paged}
                  currentUserId={user?.id ?? ""}
                  currentUserRole={user?.role}
                  onRemove={setRemoveTarget}
                />
              )}
            </>
          )}
          {tab === "pending" && (
            <PendingInvitationsTab currentUserRole={user?.role} />
          )}
        </div>

        {/* Pagination */}
        {tab === "members" && members.length > 0 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-stone-100">
            <p className="text-xs text-muted-foreground">
              {start}–{end} of {members.length} members
            </p>
            <div className="flex items-center gap-1">
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
              >
                ← Prev
              </Button>
              {Array.from({ length: totalPages }, (_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={cn(
                    "w-7 h-7 rounded text-xs font-medium transition-colors",
                    i === page
                      ? "bg-amber-500 text-white"
                      : "hover:bg-stone-100 text-stone-600"
                  )}
                >
                  {i + 1}
                </button>
              ))}
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                Next →
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
