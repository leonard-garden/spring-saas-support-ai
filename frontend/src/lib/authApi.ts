import { api } from "./api"
import { setAccessToken, setRefreshToken } from "./tokenStorage"
import type { ApiResponse, AuthResponse, AuthUser, MeResponse } from "../types/auth"

export interface LoginRequest {
  email: string
  password: string
}

export interface SignupRequest {
  businessName: string
  email: string
  password: string
}

export interface LoginResult {
  token: string
  user: AuthUser
}

export async function login(email: string, password: string): Promise<LoginResult> {
  const { data: envelope } = await api.post<ApiResponse<AuthResponse>>("/auth/login", {
    email,
    password,
  } satisfies LoginRequest)
  const auth = envelope.data!
  setAccessToken(auth.accessToken)
  setRefreshToken(auth.refreshToken)
  const { data: meEnvelope } = await api.get<ApiResponse<MeResponse>>("/auth/me")
  return { token: auth.accessToken, user: meEnvelope.data! }
}

export async function logout(): Promise<void> {
  await api.post("/auth/logout")
}

export async function forgotPassword(email: string): Promise<void> {
  await api.post("/auth/forgot-password", { email })
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await api.post("/auth/reset-password", { token, newPassword })
}

export async function acceptInvitation(token: string, password: string): Promise<LoginResult> {
  const { data: envelope } = await api.post<ApiResponse<AuthResponse>>("/invitations/accept", { token, password })
  const auth = envelope.data!
  setAccessToken(auth.accessToken)
  setRefreshToken(auth.refreshToken)
  const { data: meEnvelope } = await api.get<ApiResponse<MeResponse>>("/auth/me")
  return { token: auth.accessToken, user: meEnvelope.data! }
}

export async function verifyEmail(token: string): Promise<void> {
  await api.post("/auth/verify-email", { token })
}

export async function resendVerificationEmail(): Promise<void> {
  await api.post("/auth/resend-verification")
}

export async function signup(
  businessName: string,
  email: string,
  password: string,
): Promise<AuthResponse> {
  const { data: envelope } = await api.post<ApiResponse<AuthResponse>>("/auth/signup", {
    businessName,
    email,
    password,
  } satisfies SignupRequest)
  return envelope.data!
}
