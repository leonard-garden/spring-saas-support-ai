import { useMutation, useQueryClient } from "@tanstack/react-query"
import { changeRole } from "@/lib/memberApi"
import type { MemberResponse, Role } from "@/types/member"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { ShieldCheck, Trash2 } from "lucide-react"

interface MembersTableProps {
  members: MemberResponse[]
  currentUserId?: string
  currentUserRole?: Role
  onRemove?: (member: MemberResponse) => void
}

function getAvatarColor(email: string) {
  const colors = [
    "bg-amber-500", "bg-teal-500", "bg-blue-500", "bg-violet-500",
    "bg-rose-500", "bg-emerald-500", "bg-orange-500", "bg-cyan-500",
  ]
  let hash = 0
  for (let i = 0; i < email.length; i++) hash = email.charCodeAt(i) + ((hash << 5) - hash)
  return colors[Math.abs(hash) % colors.length]
}

function RoleBadge({ role }: { role: Role }) {
  const styles: Record<Role, string> = {
    OWNER: "bg-stone-100 text-stone-700 border-stone-200",
    ADMIN: "bg-blue-50 text-blue-700 border-blue-200",
    MEMBER: "bg-slate-100 text-slate-600 border-slate-200",
  }
  return (
    <span className={cn("inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border", styles[role])}>
      {role}
    </span>
  )
}

function StatusBadge() {
  return (
    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border border-emerald-200 bg-emerald-50 text-emerald-700">
      ACTIVE
    </span>
  )
}

function MemberRowActions({
  member,
  currentUserId,
  currentUserRole,
  onRemove,
}: {
  member: MemberResponse
  currentUserId: string
  currentUserRole: Role
  onRemove: (m: MemberResponse) => void
}) {
  const queryClient = useQueryClient()
  const isOwnRow = member.id === currentUserId
  const isOwnerRow = member.role === "OWNER"
  const canMutate = currentUserRole === "OWNER" || currentUserRole === "ADMIN"
  const targetRole = member.role === "ADMIN" ? "MEMBER" : "ADMIN"

  const roleMutation = useMutation({
    mutationFn: (role: "ADMIN" | "MEMBER") => changeRole(member.id, { role }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["members"] }),
  })

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="h-7 w-7 p-0 text-stone-400 hover:text-stone-700" aria-label="Actions">
          •••
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-52">
        <div className="px-2 py-1.5 text-xs text-muted-foreground truncate">
          {member.email}{isOwnRow ? " (You)" : ""}
        </div>
        <div className="px-2 py-1 text-[10px] font-semibold tracking-widest text-muted-foreground uppercase">
          Actions
        </div>
        <DropdownMenuItem
          disabled={isOwnerRow || !canMutate || roleMutation.isPending}
          onClick={() => !isOwnerRow && canMutate && roleMutation.mutate(targetRole)}
          className="gap-2"
        >
          <ShieldCheck className="h-4 w-4 text-muted-foreground" />
          <span>Change Role</span>
          {isOwnerRow && <span className="ml-auto text-xs text-muted-foreground">None</span>}
        </DropdownMenuItem>
        {!isOwnRow && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              disabled={isOwnerRow || !canMutate}
              className={cn("gap-2", !isOwnerRow && canMutate ? "text-destructive focus:text-destructive" : "")}
              onClick={() => !isOwnerRow && canMutate && onRemove(member)}
            >
              <Trash2 className="h-4 w-4" />
              <span>Remove Member</span>
            </DropdownMenuItem>
          </>
        )}
        {isOwnerRow && (
          <p className="px-2 py-1.5 text-xs text-muted-foreground italic">
            Owner không thể bị xóa hoặc đổi role
          </p>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function MembersTable({
  members,
  currentUserId = "",
  currentUserRole = "MEMBER",
  onRemove = () => {},
}: MembersTableProps) {
  return (
    <div className="divide-y divide-stone-100">
      {members.map((member) => (
        <div key={member.id} className="flex items-center gap-3 py-3 px-1">
          <div className={cn(
            "flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold text-white",
            getAvatarColor(member.email)
          )}>
            {member.email.charAt(0).toUpperCase()}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-stone-800 truncate">{member.email}</p>
            <p className="text-xs text-muted-foreground">
              Joined {new Date(member.createdAt).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}
            </p>
          </div>
          <RoleBadge role={member.role} />
          <StatusBadge />
          <MemberRowActions
            member={member}
            currentUserId={currentUserId}
            currentUserRole={currentUserRole}
            onRemove={onRemove}
          />
        </div>
      ))}
    </div>
  )
}
