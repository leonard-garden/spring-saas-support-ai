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
