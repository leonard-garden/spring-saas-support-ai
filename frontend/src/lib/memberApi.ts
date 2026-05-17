import { api } from "./api"
import type { ApiResponse, PagedResponse } from "../types/auth"
import type { MemberResponse, InviteRequest, InviteResponse, UpdateRoleRequest } from "../types/member"

export async function listMembers(): Promise<MemberResponse[]> {
  const { data: envelope } = await api.get<ApiResponse<PagedResponse<MemberResponse>>>("/members?size=100")
  return envelope.data!.content
}

export async function inviteMember(req: InviteRequest): Promise<InviteResponse> {
  const { data: envelope } = await api.post<ApiResponse<InviteResponse>>("/invitations/invite", req)
  return envelope.data!
}

export async function removeMember(memberId: string): Promise<void> {
  await api.delete(`/members/${memberId}`)
}

export async function changeRole(memberId: string, req: UpdateRoleRequest): Promise<MemberResponse> {
  const { data: envelope } = await api.patch<ApiResponse<MemberResponse>>(`/members/${memberId}/role`, req)
  return envelope.data!
}
