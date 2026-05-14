export type Role = "OWNER" | "ADMIN" | "AGENT"
export type AuthStatus = "loading" | "authenticated" | "unauthenticated"

export interface AuthUser {
  id: string
  email: string
  role: Role
  businessId: string
  businessName: string
  emailVerified: boolean
}

export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: string | null
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  businessId: string
  memberId: string
}

export type MeResponse = AuthUser
