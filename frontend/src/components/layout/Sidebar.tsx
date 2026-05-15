import { Home, Users, BookOpen } from "lucide-react"
import { useAuthStore } from "@/store/authStore"
import { LogoutButton } from "@/components/auth/LogoutButton"
import { SidebarNavLink } from "./SidebarNavLink"

export function Sidebar() {
  const user = useAuthStore((s) => s.user)

  return (
    <aside className="w-64 shrink-0 border-r bg-card flex flex-col h-full">
      <div className="px-4 py-4 text-lg font-semibold border-b">Support AI</div>

      <nav className="flex-1 px-2 py-4 space-y-1">
        <SidebarNavLink to="/dashboard" end icon={<Home className="h-4 w-4" />} label="Home" />
        <SidebarNavLink to="/members" icon={<Users className="h-4 w-4" />} label="Members" />
        <SidebarNavLink to="/kb" icon={<BookOpen className="h-4 w-4" />} label="Knowledge Base" />
      </nav>

      <div className="border-t px-4 py-3 space-y-1">
        <p className="text-sm font-medium">{user?.email}</p>
        <p className="text-xs text-muted-foreground">{user?.role} · {user?.businessName}</p>
        <div className="pt-2">
          <LogoutButton />
        </div>
      </div>
    </aside>
  )
}
