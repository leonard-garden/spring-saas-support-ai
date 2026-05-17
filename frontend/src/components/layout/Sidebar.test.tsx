import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it } from "vitest"
import { MemoryRouter } from "react-router-dom"
import { Sidebar } from "./Sidebar"
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

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Sidebar />
    </MemoryRouter>
  )
}

describe("Sidebar", () => {
  it("renders 3 nav links: Home, Members, Knowledge Base", () => {
    renderAt("/dashboard")
    expect(screen.getByRole("link", { name: /home/i })).toBeDefined()
    expect(screen.getByRole("link", { name: /members/i })).toBeDefined()
    expect(screen.getByRole("link", { name: /knowledge base/i })).toBeDefined()
  })

  it("Home link points to /dashboard, Members to /members, KB to /kb", () => {
    renderAt("/dashboard")
    expect(screen.getByRole("link", { name: /home/i })).toHaveAttribute("href", "/dashboard")
    expect(screen.getByRole("link", { name: /members/i })).toHaveAttribute("href", "/members")
    expect(screen.getByRole("link", { name: /knowledge base/i })).toHaveAttribute("href", "/kb")
  })

  it("displays user email, role, and businessName from store", () => {
    renderAt("/dashboard")
    expect(screen.getByText("owner@acme.com")).toBeDefined()
    expect(screen.getByText(/OWNER/)).toBeDefined()
    expect(screen.getByText(/Acme Inc/)).toBeDefined()
  })

  it("active nav link has amber accent border when at /members", () => {
    renderAt("/members")
    const membersLink = screen.getByRole("link", { name: /members/i })
    const homeLink = screen.getByRole("link", { name: /home/i })
    expect(membersLink.className).toContain("border-amber-400")
    expect(homeLink.className).not.toContain("border-amber-400")
  })

  it("renders a Log out button", () => {
    renderAt("/dashboard")
    expect(screen.getByRole("button", { name: /log out/i })).toBeDefined()
  })
})
