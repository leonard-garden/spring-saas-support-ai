import { render, screen, waitFor } from "@testing-library/react"
import userEvent, { PointerEventsCheckLevel } from "@testing-library/user-event"
import { describe, it, expect, vi, beforeEach } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { InviteModal } from "./InviteModal"
import * as memberApi from "@/lib/memberApi"

vi.mock("@/lib/memberApi")

const user = userEvent.setup({ pointerEventsCheck: PointerEventsCheckLevel.Never })

function renderModal(open = true, onOpenChange = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return {
    onOpenChange,
    ...render(
      <QueryClientProvider client={client}>
        <InviteModal open={open} onOpenChange={onOpenChange} />
      </QueryClientProvider>
    ),
  }
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe("InviteModal", () => {
  it("renders email input and role select", () => {
    renderModal()
    expect(screen.getByLabelText(/email/i)).toBeDefined()
    expect(screen.getByRole("combobox")).toBeDefined()
  })

  it("shows validation error on invalid email", async () => {
    renderModal()
    await user.type(screen.getByLabelText(/email/i), "not-an-email")
    await user.click(screen.getByRole("button", { name: /send invite/i }))
    await waitFor(() => expect(screen.getByText(/invalid email/i)).toBeDefined())
  })

  it("calls inviteMember with correct payload on valid submit", async () => {
    vi.mocked(memberApi.inviteMember).mockResolvedValue({ message: "ok" })
    renderModal()
    await user.type(screen.getByLabelText(/email/i), "alice@acme.com")
    await user.click(screen.getByRole("button", { name: /send invite/i }))
    await waitFor(() => {
      const calls = vi.mocked(memberApi.inviteMember).mock.calls
      expect(calls.length).toBeGreaterThan(0)
      expect(calls[0][0]).toEqual({ email: "alice@acme.com", role: "MEMBER" })
    })
  })

  it("closes modal on successful invite", async () => {
    vi.mocked(memberApi.inviteMember).mockResolvedValue({ message: "ok" })
    const { onOpenChange } = renderModal()
    await user.type(screen.getByLabelText(/email/i), "bob@acme.com")
    await user.click(screen.getByRole("button", { name: /send invite/i }))
    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false))
  })
})
