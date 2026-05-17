import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { listPendingInvitations, resendInvitation, revokeInvitation } from "@/lib/invitationApi"
import type { Role } from "@/types/member"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

function getAvatarColor(email: string) {
  const colors = [
    "bg-amber-500", "bg-teal-500", "bg-blue-500", "bg-violet-500",
    "bg-rose-500", "bg-emerald-500", "bg-orange-500", "bg-cyan-500",
  ]
  let hash = 0
  for (let i = 0; i < email.length; i++) hash = email.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
}

function RoleBadge({ role }: { role: "ADMIN" | "MEMBER" }) {
  const styles: Record<string, string> = {
    ADMIN: "bg-blue-50 text-blue-700 border-blue-200",
    MEMBER: "bg-slate-100 text-slate-600 border-slate-200",
  }
  return (
    <span className={cn("inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border", styles[role])}>
      {role}
    </span>
  )
}

interface PendingInvitationsTabProps {
  currentUserRole?: Role
}

export function PendingInvitationsTab({ currentUserRole = "MEMBER" }: PendingInvitationsTabProps) {
  const queryClient = useQueryClient()
  const canMutate = currentUserRole === "OWNER" || currentUserRole === "ADMIN"

  const { data: invitations = [], isLoading, isError } = useQuery({
    queryKey: ["invitations-pending"],
    queryFn: listPendingInvitations,
  })

  const resendMutation = useMutation({
    mutationFn: resendInvitation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["invitations-pending"] }),
  })

  const revokeMutation = useMutation({
    mutationFn: revokeInvitation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["invitations-pending"] }),
  })

  if (isLoading) return <p className="text-sm text-muted-foreground py-4">Loading…</p>
  if (isError) return <p className="text-sm text-destructive py-4">Failed to load invitations.</p>

  if (invitations.length === 0) {
    return (
      <div className="py-10 text-center text-muted-foreground text-sm">
        No pending invitations.
      </div>
    )
  }

  return (
    <div className="divide-y divide-stone-100">
      {invitations.map((inv) => (
        <div key={inv.id} className="flex items-center gap-3 py-3 px-1">
          <div className={cn(
            "flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold text-white",
            getAvatarColor(inv.email)
          )}>
            {inv.email.charAt(0).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-stone-800 truncate">{inv.email}</p>
            <p className="text-xs text-muted-foreground">
              Expires {new Date(inv.expiresAt).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}
            </p>
          </div>
          <RoleBadge role={inv.role} />
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border border-amber-200 bg-amber-50 text-amber-700">
            PENDING
          </span>
          {canMutate && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="h-7 w-7 p-0 text-stone-400 hover:text-stone-700" aria-label="Actions">
                  •••
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-44">
                <DropdownMenuItem
                  onClick={() => resendMutation.mutate(inv.id)}
                  disabled={resendMutation.isPending}
                >
                  Resend Invite
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onClick={() => revokeMutation.mutate(inv.id)}
                  disabled={revokeMutation.isPending}
                >
                  Revoke
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      ))}
    </div>
  )
}
