import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { CorsTestPage } from "@/pages/CorsTestPage"
import { LoginPage } from "@/pages/LoginPage"
import { SignupPage } from "@/pages/SignupPage"
import { ForgotPasswordPage } from "@/pages/ForgotPasswordPage"
import { ResetPasswordPage } from "@/pages/ResetPasswordPage"
import { DashboardPage } from "@/pages/DashboardPage"
import { ProtectedRoute } from "@/components/auth/ProtectedRoute"
import { GuestRoute } from "@/components/auth/GuestRoute"
import { useAuthInit } from "@/hooks/useAuthInit"

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
})

function AppRoutes() {
  useAuthInit()
  return (
    <Routes>
      <Route path="/cors-test" element={<CorsTestPage />} />
      <Route path="/login" element={<GuestRoute><LoginPage /></GuestRoute>} />
      <Route path="/signup" element={<GuestRoute><SignupPage /></GuestRoute>} />
      <Route path="/forgot-password" element={<GuestRoute><ForgotPasswordPage /></GuestRoute>} />
      <Route path="/reset-password" element={<GuestRoute><ResetPasswordPage /></GuestRoute>} />
      <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
