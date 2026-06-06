import { useQuery } from "@tanstack/react-query"
import { getPlans, getSubscription, getUsage, getInvoices } from "@/lib/billingApi"

export function usePlans() {
  return useQuery({ queryKey: ["billing", "plans"], queryFn: getPlans })
}

export function useSubscription() {
  return useQuery({ queryKey: ["billing", "subscription"], queryFn: getSubscription })
}

export function useUsage() {
  return useQuery({ queryKey: ["billing", "usage"], queryFn: getUsage })
}

export function useInvoices() {
  return useQuery({ queryKey: ["billing", "invoices"], queryFn: getInvoices })
}
