import { describe, it, expect, beforeEach } from "vitest"
import { useAuthStore, initialAuthState } from "./authStore"
import { getAccessToken, clearTokens } from "@/lib/tokenStorage"
import type { AuthUser } from "@/types/auth"

const mockUser: AuthUser = {
  id: "user-1",
  email: "test@example.com",
  role: "OWNER",
  businessId: "biz-1",
  businessName: "Acme Corp",
  emailVerified: true,
}

beforeEach(() => {
  useAuthStore.setState(initialAuthState)
  clearTokens()
})

it("T1: initializes with status loading and null user/token", () => {
  const state = useAuthStore.getState()
  expect(state.status).toBe("loading")
  expect(state.user).toBeNull()
  expect(state.accessToken).toBeNull()
})

it("T2: setAuth transitions to authenticated and stores token + user", () => {
  useAuthStore.getState().setAuth("jwt-abc", mockUser)
  const state = useAuthStore.getState()
  expect(state.status).toBe("authenticated")
  expect(state.accessToken).toBe("jwt-abc")
  expect(state.user).toEqual(mockUser)
})

it("T3: clearAuth transitions to unauthenticated and nulls token + user", () => {
  useAuthStore.getState().setAuth("jwt-abc", mockUser)
  useAuthStore.getState().clearAuth()
  const state = useAuthStore.getState()
  expect(state.status).toBe("unauthenticated")
  expect(state.accessToken).toBeNull()
  expect(state.user).toBeNull()
})

it("T4: useAuthStore.getState() works imperatively without React", () => {
  const before = useAuthStore.getState()
  expect(before).toBeDefined()
  useAuthStore.setState({ status: "authenticated" })
  expect(useAuthStore.getState().status).toBe("authenticated")
})

it("T5: setAuth syncs access token into tokenStorage for api.ts interceptor", () => {
  useAuthStore.getState().setAuth("jwt-xyz", mockUser)
  expect(getAccessToken()).toBe("jwt-xyz")
})
