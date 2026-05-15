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
            ? "bg-primary text-primary-foreground"
            : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
        }`
      }
    >
      {icon}
      <span>{label}</span>
    </NavLink>
  )
}
