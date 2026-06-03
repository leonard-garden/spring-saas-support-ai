import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"
import { useAuthStore } from "@/store/authStore"

interface OwnerRouteProps {
  children: ReactNode
}

export function OwnerRoute({ children }: OwnerRouteProps) {
  const user = useAuthStore((s) => s.user)
  const status = useAuthStore((s) => s.status)

  if (status === "loading") return null
  if (user?.role !== "OWNER") return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
