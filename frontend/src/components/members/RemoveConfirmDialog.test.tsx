import { render, screen, waitFor } from "@testing-library/react"
import userEvent, { PointerEventsCheckLevel } from "@testing-library/user-event"
import { describe, it, expect, vi, beforeEach } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RemoveConfirmDialog } from "./RemoveConfirmDialog"
import * as memberApi from "@/lib/memberApi"
import type { MemberResponse } from "@/types/member"

vi.mock("@/lib/memberApi")

const user = userEvent.setup({ pointerEventsCheck: PointerEventsCheckLevel.Never })

const mockMember: MemberResponse = {
  id: "m1",
  email: "alice@acme.com",
  role: "MEMBER",
  createdAt: "2024-01-01T00:00:00Z",
}

function renderDialog(member: MemberResponse | null = mockMember, onOpenChange = vi.fn()) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return {
    onOpenChange,
    ...render(
      <QueryClientProvider client={client}>
        <RemoveConfirmDialog member={member} onOpenChange={onOpenChange} />
      </QueryClientProvider>
    ),
  }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe("RemoveConfirmDialog", () => {
  it("renders member email in confirmation message", () => {
    renderDialog()
    expect(screen.getByText("alice@acme.com")).toBeDefined()
  })

  it("calls removeMember with member id when Remove is clicked", async () => {
    vi.mocked(memberApi.removeMember).mockResolvedValue(undefined)
    renderDialog()
    await user.click(screen.getByRole("button", { name: /remove/i }))
    await waitFor(() => {
      const calls = vi.mocked(memberApi.removeMember).mock.calls
      expect(calls.length).toBeGreaterThan(0)
      expect(calls[0][0]).toBe("m1")
    })
  })

  it("calls onOpenChange(false) after successful removal", async () => {
    vi.mocked(memberApi.removeMember).mockResolvedValue(undefined)
    const { onOpenChange } = renderDialog()
    await user.click(screen.getByRole("button", { name: /remove/i }))
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })

  it("does not render dialog content when member is null", () => {
    renderDialog(null)
    expect(screen.queryByText(/remove member/i)).toBeNull()
  })
})
