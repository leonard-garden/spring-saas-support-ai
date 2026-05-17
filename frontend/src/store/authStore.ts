import { create } from "zustand"
import type { AuthStatus, AuthUser } from "@/types/auth"
import { setAccessToken, clearTokens } from "@/lib/tokenStorage"

interface AuthState {
  status: AuthStatus
  user: AuthUser | null
  accessToken: string | null
  setAuth: (token: string, user: AuthUser) => void
  clearAuth: () => void
}

// Exported so tests can reset store to this exact shape in beforeEach — prevents drift when new fields are added.
export const initialAuthState = {
  status: "loading" as AuthStatus,
  user: null,
  accessToken: null,
}

export const useAuthStore = create<AuthState>()((set) => ({
  ...initialAuthState,
  setAuth: (token, user) => {
    setAccessToken(token)
    set({ status: "authenticated", user, accessToken: token })
  },
  clearAuth: () => {
    clearTokens()
    set({ status: "unauthenticated", user: null, accessToken: null })
  },
}))
