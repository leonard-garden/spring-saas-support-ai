import { api } from "./api"
import type { ApiResponse } from "../types/auth"
import type { Plan, Subscription, UsageStats, Invoice } from "../types/billing"
import { isAxiosError } from "axios"

export async function getPlans(): Promise<Plan[]> {
  const { data: envelope } = await api.get<ApiResponse<Plan[]>>("/billing/plans")
  return envelope.data ?? []
}

export async function getSubscription(): Promise<Subscription | null> {
  try {
    const { data: envelope } = await api.get<ApiResponse<Subscription>>("/billing/subscription")
    return envelope.data ?? null
  } catch (err) {
    if (isAxiosError(err) && err.response?.status === 404) return null
    throw err
  }
}

export async function getUsage(): Promise<UsageStats | null> {
  try {
    const { data: envelope } = await api.get<ApiResponse<UsageStats>>("/billing/usage")
    return envelope.data ?? null
  } catch (err) {
    if (isAxiosError(err) && (err.response?.status === 403 || err.response?.status === 404)) {
      return null
    }
    throw err
  }
}

export async function getInvoices(): Promise<Invoice[]> {
  try {
    const { data: envelope } = await api.get<ApiResponse<Invoice[]>>("/billing/invoices")
    return envelope.data ?? []
  } catch (err) {
    if (isAxiosError(err) && err.response?.status === 403) return []
    throw err
  }
}

export async function createCheckoutSession(planSlug: string): Promise<{ url: string }> {
  const { data: envelope } = await api.post<ApiResponse<{ url: string }>>("/billing/checkout", {
    planSlug,
  })
  if (!envelope.data) throw new Error("No checkout URL returned")
  return envelope.data
}

export async function cancelSubscription(): Promise<{ cancelled: boolean; cancelAt: string }> {
  const { data: envelope } = await api.post<
    ApiResponse<{ cancelled: boolean; cancelAt: string }>
  >("/billing/cancel")
  if (!envelope.data) throw new Error("No response from cancel endpoint")
  return envelope.data
}

export async function upgradeSubscription(planSlug: string): Promise<{ planName: string }> {
  const { data: envelope } = await api.post<ApiResponse<{ planName: string }>>(
    "/billing/upgrade",
    { planSlug }
  )
  if (!envelope.data) throw new Error("No response from upgrade endpoint")
  return envelope.data
}

export async function downgradeSubscription(
  planSlug: string
): Promise<{ planName: string; effectiveAt: string }> {
  const { data: envelope } = await api.post<
    ApiResponse<{ planName: string; effectiveAt: string }>
  >("/billing/downgrade", { planSlug })
  if (!envelope.data) throw new Error("No response from downgrade endpoint")
  return envelope.data
}
