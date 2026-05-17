import type { ReactNode } from "react"

interface AuthLayoutProps {
  children: ReactNode
  tagline?: string
  subtitle?: string
}

export function AuthLayout({
  children,
  tagline = "AI customer support, embeddable in 5 minutes.",
  subtitle = "White-label chatbot trained on your docs.",
}: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen">
      {/* Left panel — branding */}
      <div className="hidden md:flex md:w-2/5 bg-sidebar flex-col justify-center px-12">
        <div className="w-8 h-1 bg-amber-400 rounded mb-6" />
        <p className="font-display text-2xl font-bold text-white leading-snug mb-3">
          {tagline}
        </p>
        <p className="text-sm text-stone-400 leading-relaxed">{subtitle}</p>
      </div>

      {/* Right panel — form */}
      <div className="flex flex-1 items-center justify-center bg-white px-6 py-12">
        <div className="w-full max-w-sm">
          <p className="font-display text-lg font-bold text-foreground mb-8">Support AI</p>
          {children}
        </div>
      </div>
    </div>
  )
}
