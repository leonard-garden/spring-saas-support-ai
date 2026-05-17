import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it } from "vitest"
import { EmailVerificationBanner } from "./EmailVerificationBanner"
import { useAuthStore, initialAuthState } from "@/store/authStore"
import type { AuthUser } from "@/types/auth"

const baseUser: AuthUser = {
  id: "u1",
  email: "user@example.com",
  role: "OWNER",
  businessId: "b1",
  businessName: "Acme",
  emailVerified: false,
}

beforeEach(() => {
  useAuthStore.setState(initialAuthState)
})

describe("EmailVerificationBanner", () => {
  it("shows banner when user.emailVerified is false", () => {
    useAuthStore.setState({ ...initialAuthState, status: "authenticated", user: { ...baseUser, emailVerified: false } })
    render(<EmailVerificationBanner />)
    expect(screen.getAllByText(/verify your email/i).length).toBeGreaterThan(0)
  })

  it("renders nothing when user.emailVerified is true", () => {
    useAuthStore.setState({ ...initialAuthState, status: "authenticated", user: { ...baseUser, emailVerified: true } })
    const { container } = render(<EmailVerificationBanner />)
    expect(screen.queryByText(/verify your email/i)).toBeNull()
    expect(container.firstChild).toBeNull()
  })

  it("renders nothing when user is null", () => {
    useAuthStore.setState({ ...initialAuthState, status: "unauthenticated", user: null })
    const { container } = render(<EmailVerificationBanner />)
    expect(container.firstChild).toBeNull()
  })
})
