import { LogoutButton } from "@/components/auth/LogoutButton"

export function DashboardPage() {
  return (
    <div className="p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <LogoutButton />
      </div>
    </div>
  )
}
