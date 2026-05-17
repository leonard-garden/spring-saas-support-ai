import { useState } from "react"
import { useNavigate } from "react-router-dom"
import { logout } from "@/lib/authApi"
import { useAuthStore } from "@/store/authStore"
import { Button } from "@/components/ui/button"

export function LogoutButton() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const [loading, setLoading] = useState(false)

  async function handleLogout() {
    setLoading(true)
    try {
      await logout()
    } finally {
      clearAuth()
      navigate("/login")
    }
  }

  return (
    <Button variant="outline" onClick={handleLogout} disabled={loading}>
      Log out
    </Button>
  )
}
