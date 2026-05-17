import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import { useAuthStore } from "@/store/authStore"

interface ProtectedRouteProps {
  children: ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const status = useAuthStore((s) => s.status)

  if (status === "loading") return <div>Loading...</div>
  if (status === "unauthenticated") return <Navigate to="/login" replace />
  return <>{children}</>
}
