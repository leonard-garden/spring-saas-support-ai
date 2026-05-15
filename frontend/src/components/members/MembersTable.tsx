import { useMutation, useQueryClient } from "@tanstack/react-query"
import { changeRole } from "@/lib/memberApi"
import type { MemberResponse } from "@/types/member"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"

interface MembersTableProps {
  members: MemberResponse[]
  canMutate?: boolean
  currentUserId?: string
  onRemove?: (member: MemberResponse) => void
}

interface MemberRowActionsProps {
  member: MemberResponse
  currentUserId: string
  onRemove: (member: MemberResponse) => void
}

function MemberRowActions({ member, currentUserId, onRemove }: MemberRowActionsProps) {
  const queryClient = useQueryClient()

  const roleMutation = useMutation({
    mutationFn: (role: "ADMIN" | "MEMBER") => changeRole(member.id, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["members"] })
    },
  })

  const isOwnRow = member.id === currentUserId
  const targetRole = member.role === "ADMIN" ? "MEMBER" : "ADMIN"
  const targetRoleLabel = member.role === "ADMIN" ? "Change to Member" : "Change to Admin"

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" aria-label="Actions">
          ···
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem
          onClick={() => roleMutation.mutate(targetRole)}
          disabled={roleMutation.isPending}
        >
          {targetRoleLabel}
        </DropdownMenuItem>
        {!isOwnRow && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="text-destructive focus:text-destructive"
              onClick={() => onRemove(member)}
            >
              Remove
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function MembersTable({
  members,
  canMutate = false,
  currentUserId = "",
  onRemove = () => {},
}: MembersTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Email</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Joined</TableHead>
          <TableHead>Status</TableHead>
          {canMutate && <TableHead>Actions</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {members.map((member) => (
          <TableRow key={member.id}>
            <TableCell>{member.email}</TableCell>
            <TableCell>{member.role}</TableCell>
            <TableCell>{new Date(member.createdAt).toLocaleDateString()}</TableCell>
            <TableCell>
              <Badge variant="secondary">Active</Badge>
            </TableCell>
            {canMutate && (
              <TableCell>
                <MemberRowActions
                  member={member}
                  currentUserId={currentUserId}
                  onRemove={onRemove}
                />
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
