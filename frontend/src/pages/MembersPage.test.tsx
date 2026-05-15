import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, it, expect, vi, beforeEach } from "vitest"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MembersPage } from "./MembersPage"
import * as memberApi from "@/lib/memberApi"
import type { MemberResponse } from "@/types/member"

vi.mock("@/lib/memberApi")

function makeMember(i: number): MemberResponse {
  return {
    id: `m${i}`,
    email: `user${i}@acme.com`,
    role: "MEMBER",
    createdAt: "2024-01-01T00:00:00Z",
  }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MembersPage />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe("MembersPage", () => {
  it("renders member rows when members are returned", async () => {
    vi.mocked(memberApi.listMembers).mockResolvedValue([makeMember(1), makeMember(2)])
    renderPage()
    await waitFor(() => expect(screen.getByText("user1@acme.com")).toBeDefined())
    expect(screen.getByText("user2@acme.com")).toBeDefined()
  })

  it("shows empty state when member list is empty", async () => {
    vi.mocked(memberApi.listMembers).mockResolvedValue([])
    renderPage()
    await waitFor(() => expect(screen.getByText("No members yet.")).toBeDefined())
  })

  it("shows Next button enabled and Prev disabled on first page when >10 members", async () => {
    vi.mocked(memberApi.listMembers).mockResolvedValue(
      Array.from({ length: 12 }, (_, i) => makeMember(i + 1))
    )
    renderPage()
    await waitFor(() => expect(screen.getByText("user1@acme.com")).toBeDefined())
    const next = screen.getByRole("button", { name: /next/i })
    const prev = screen.getByRole("button", { name: /prev/i })
    expect(next).not.toBeDisabled()
    expect(prev).toBeDisabled()
  })

  it("navigates to second page when Next is clicked", async () => {
    vi.mocked(memberApi.listMembers).mockResolvedValue(
      Array.from({ length: 12 }, (_, i) => makeMember(i + 1))
    )
    renderPage()
    await waitFor(() => expect(screen.getByText("user1@acme.com")).toBeDefined())
    await userEvent.click(screen.getByRole("button", { name: /next/i }))
    expect(screen.getByText("user11@acme.com")).toBeDefined()
    expect(screen.queryByText("user1@acme.com")).toBeNull()
  })
})
