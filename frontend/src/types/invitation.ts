export interface InvitationResponse {
  id: string
  email: string
  role: "ADMIN" | "MEMBER"
  expiresAt: string
  createdAt: string
}
