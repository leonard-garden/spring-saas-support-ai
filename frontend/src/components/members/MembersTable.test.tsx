import { render, screen, waitFor } from "@testing-library/react"
import userEvent, { PointerEventsCheckLevel } from "@testing-library/user-event"
import { describe, it, expect, vi, beforeEach } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MembersTable } from "./MembersTable"
import * as memberApi from "@/lib/memberApi"
import type { MemberResponse } from "@/types/member"

vi.mock("@/lib/memberApi")

const user = userEvent.setup({ pointerEventsCheck: PointerEventsCheckLevel.Never })

const members: MemberResponse[] = [
  { id: "m1", email: "alice@acme.com", role: "MEMBER", createdAt: "2024-01-01T00:00:00Z" },
  { id: "m2", email: "bob@acme.com", role: "ADMIN", createdAt: "2024-01-02T00:00:00Z" },
]

function renderTable(props: Partial<Parameters<typeof MembersTable>[0]> = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MembersTable members={members} {...props} />
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe("MembersTable", () => {
  it("renders member rows without Actions column when canMutate is false", () => {
    renderTable({ canMutate: false })
    expect(screen.queryByText("Actions")).toBeNull()
  })

  it("renders Actions column when canMutate is true", () => {
    renderTable({ canMutate: true, currentUserId: "other", onRemove: vi.fn() })
    expect(screen.getByText("Actions")).toBeDefined()
  })

  it("hides Remove option on the current user's own row", async () => {
    renderTable({ canMutate: true, currentUserId: "m1", onRemove: vi.fn() })
    const triggers = screen.getAllByRole("button", { name: /actions/i })
    await user.click(triggers[0])
    expect(screen.queryByText("Remove")).toBeNull()
  })

  it("calls changeRole when role change item is clicked", async () => {
    vi.mocked(memberApi.changeRole).mockResolvedValue({
      id: "m1",
      email: "alice@acme.com",
      role: "ADMIN",
      createdAt: "2024-01-01T00:00:00Z",
    })
    renderTable({ canMutate: true, currentUserId: "other", onRemove: vi.fn() })
    const triggers = screen.getAllByRole("button", { name: /actions/i })
    await user.click(triggers[0])
    await user.click(screen.getByText("Change to Admin"))
    await waitFor(() => {
      const calls = vi.mocked(memberApi.changeRole).mock.calls
      expect(calls.length).toBeGreaterThan(0)
      expect(calls[0][0]).toBe("m1")
      expect(calls[0][1]).toEqual({ role: "ADMIN" })
    })
  })

  it("calls onRemove with correct member when Remove is clicked", async () => {
    const onRemove = vi.fn()
    renderTable({ canMutate: true, currentUserId: "other", onRemove })
    const triggers = screen.getAllByRole("button", { name: /actions/i })
    await user.click(triggers[0])
    await user.click(screen.getByText("Remove"))
    expect(onRemove).toHaveBeenCalledWith(members[0])
  })
})
