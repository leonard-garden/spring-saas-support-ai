import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import { useAuthStore } from "@/store/authStore"

interface GuestRouteProps {
  children: ReactNode
}

export function GuestRoute({ children }: GuestRouteProps) {
  const status = useAuthStore((s) => s.status)

  if (status === "loading") return <div>Loading...</div>
  if (status === "authenticated") return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
