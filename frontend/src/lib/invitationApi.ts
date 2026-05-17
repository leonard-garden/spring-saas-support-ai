import { api } from "./api"
import type { ApiResponse, PagedResponse } from "../types/auth"
import type { InvitationResponse } from "../types/invitation"

export async function listPendingInvitations(): Promise<InvitationResponse[]> {
  const { data: envelope } = await api.get<ApiResponse<PagedResponse<InvitationResponse>>>("/invitations?size=100")
  return envelope.data!.content
}

export async function resendInvitation(id: string): Promise<InvitationResponse> {
  const { data: envelope } = await api.post<ApiResponse<InvitationResponse>>(`/invitations/${id}/resend`, {})
  return envelope.data!
}

export async function revokeInvitation(id: string): Promise<void> {
  await api.delete(`/invitations/${id}`)
}
