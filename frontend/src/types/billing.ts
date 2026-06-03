export type SubscriptionStatus =
  | "TRIALING"
  | "ACTIVE"
  | "PAST_DUE"
  | "CANCELED"
  | "UNPAID"

export interface UsageMetric {
  used: number
  limit: number
}

export interface UsageStats {
  knowledgeBases: UsageMetric
  documents: UsageMetric
  messages: UsageMetric
  members: UsageMetric
}

export interface Plan {
  id: string
  name: string
  slug: string
  priceMonthly: number
  stripePriceId: string | null
  maxKnowledgeBases: number
  maxDocsPerKb: number
  maxMessagesPerMonth: number
  maxMembers: number
  active: boolean
}

export interface Subscription {
  planId: string
  planName: string
  planSlug: string
  status: SubscriptionStatus
  trialEndsAt: string | null
  currentPeriodStart: string | null
  currentPeriodEnd: string | null
  stripeSubscriptionId: string | null
  cancelAtPeriodEnd: boolean
  pendingPlanId: string | null
}

export interface Invoice {
  id: string
  date: string
  description: string
  amount: number
  status: "paid" | "failed"
  pdfUrl: string | null
}
