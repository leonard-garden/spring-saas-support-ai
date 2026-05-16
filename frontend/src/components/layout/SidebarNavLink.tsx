import { NavLink } from "react-router-dom"
import type { ReactNode } from "react"

interface SidebarNavLinkProps {
  to: string
  icon: ReactNode
  label: string
  end?: boolean
}

export function SidebarNavLink({ to, icon, label, end }: SidebarNavLinkProps) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
          isActive
            ? "bg-stone-800 text-white border-l-2 border-amber-400 pl-[10px]"
            : "text-stone-400 hover:bg-stone-800 hover:text-white"
        }`
      }
    >
      {icon}
      <span>{label}</span>
    </NavLink>
  )
}
