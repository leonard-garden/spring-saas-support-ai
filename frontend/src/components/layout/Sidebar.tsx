import { Home, Users, BookOpen } from "lucide-react"
import { useAuthStore } from "@/store/authStore"
import { LogoutButton } from "@/components/auth/LogoutButton"
import { SidebarNavLink } from "./SidebarNavLink"

export function Sidebar() {
  const user = useAuthStore((s) => s.user)

  return (
    <aside className="w-64 shrink-0 flex flex-col h-full bg-sidebar text-stone-300">
      <div className="px-4 h-14 flex items-center border-b border-stone-800">
        <span className="font-display text-base font-bold text-white tracking-tight">
          Support AI
        </span>
      </div>

      <nav className="flex-1 px-2 py-4 space-y-0.5">
        <SidebarNavLink to="/dashboard" end icon={<Home className="h-4 w-4" />} label="Home" />
        <SidebarNavLink to="/members" icon={<Users className="h-4 w-4" />} label="Members" />
        <SidebarNavLink to="/kb" icon={<BookOpen className="h-4 w-4" />} label="Knowledge Base" />
      </nav>

      <div className="border-t border-stone-800 px-4 py-3 space-y-0.5">
        <p className="text-sm font-medium text-stone-200">{user?.email}</p>
        <p className="text-xs text-stone-500 uppercase tracking-wide">
          {user?.role} · {user?.businessName}
        </p>
        <div className="pt-2">
          <LogoutButton />
        </div>
      </div>
    </aside>
  )
}
