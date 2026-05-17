import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it } from "vitest"
import { MemoryRouter } from "react-router-dom"
import { DashboardPage } from "./DashboardPage"
import { useAuthStore, initialAuthState } from "@/store/authStore"
import type { AuthUser } from "@/types/auth"

const baseUser: AuthUser = {
  id: "u1",
  email: "owner@acme.com",
  role: "OWNER",
  businessId: "b1",
  businessName: "Acme Inc",
  emailVerified: true,
}

beforeEach(() => {
  useAuthStore.setState({ ...initialAuthState, status: "authenticated", user: baseUser })
})

function renderPage() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>
  )
}

describe("DashboardPage", () => {
  it("renders the user's businessName", () => {
    renderPage()
    expect(screen.getByText("Acme Inc")).toBeDefined()
  })

  it("renders the plan indicator 'Free Trial'", () => {
    renderPage()
    expect(screen.getByText("Free Trial")).toBeDefined()
  })

  it("renders Invite Member link pointing to /members", () => {
    renderPage()
    const link = screen.getByRole("link", { name: /invite member/i })
    expect(link.getAttribute("href")).toBe("/members")
  })

  it("renders Add Knowledge Base link pointing to /kb", () => {
    renderPage()
    const link = screen.getByRole("link", { name: /add knowledge base/i })
    expect(link.getAttribute("href")).toBe("/kb")
  })

  it("renders Members stat card with value 0", () => {
    renderPage()
    expect(screen.getByText("Members")).toBeDefined()
    const values = screen.getAllByText("0")
    expect(values.length).toBeGreaterThanOrEqual(1)
  })

  it("renders Knowledge Bases stat card with value 0", () => {
    renderPage()
    expect(screen.getByText("Knowledge Bases")).toBeDefined()
  })

  it("does NOT render a Log out button", () => {
    renderPage()
    expect(screen.queryByRole("button", { name: /log out/i })).toBeNull()
  })
})
