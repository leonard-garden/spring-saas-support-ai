export type Role = "OWNER" | "ADMIN" | "MEMBER"

export interface MemberResponse {
  id: string
  email: string
  role: Role
  createdAt: string
}

export interface InviteRequest {
  email: string
  role: "ADMIN" | "MEMBER"
}

export interface InviteResponse {
  message: string
}

export interface UpdateRoleRequest {
  role: "ADMIN" | "MEMBER"
}
