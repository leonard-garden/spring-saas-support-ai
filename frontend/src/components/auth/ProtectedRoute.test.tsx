import { render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { beforeEach, describe, expect, it } from "vitest"
import { ProtectedRoute } from "./ProtectedRoute"
import { GuestRoute } from "./GuestRoute"
import { useAuthStore, initialAuthState } from "@/store/authStore"
import type { AuthStatus } from "@/types/auth"

function renderProtected(status: AuthStatus) {
  useAuthStore.setState({ ...initialAuthState, status })
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <div>Protected Content</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>
  )
}

function renderGuest(status: AuthStatus) {
  useAuthStore.setState({ ...initialAuthState, status })
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <Routes>
        <Route
          path="/"
          element={
            <GuestRoute>
              <div>Guest Content</div>
            </GuestRoute>
          }
        />
        <Route path="/dashboard" element={<div>Dashboard Page</div>} />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  useAuthStore.setState(initialAuthState)
})

describe("ProtectedRoute", () => {
  it("T1: shows loading indicator when status is loading", () => {
    renderProtected("loading")
    expect(screen.getByText("Loading...")).toBeInTheDocument()
  })

  it("T2: redirects to /login when unauthenticated", () => {
    renderProtected("unauthenticated")
    expect(screen.getByText("Login Page")).toBeInTheDocument()
    expect(screen.queryByText("Protected Content")).not.toBeInTheDocument()
  })

  it("T3: renders children when authenticated", () => {
    renderProtected("authenticated")
    expect(screen.getByText("Protected Content")).toBeInTheDocument()
  })
})

describe("GuestRoute", () => {
  it("T4: redirects to /dashboard when authenticated", () => {
    renderGuest("authenticated")
    expect(screen.getByText("Dashboard Page")).toBeInTheDocument()
    expect(screen.queryByText("Guest Content")).not.toBeInTheDocument()
  })

  it("T5: renders children when unauthenticated", () => {
    renderGuest("unauthenticated")
    expect(screen.getByText("Guest Content")).toBeInTheDocument()
  })

  it("T6: shows loading indicator when status is loading", () => {
    renderGuest("loading")
    expect(screen.getByText("Loading...")).toBeInTheDocument()
  })
})
