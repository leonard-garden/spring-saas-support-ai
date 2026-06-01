import { api } from "@/lib/api"
import type { ApiResponse } from "@/types/auth"
import type { ConversationSummary, ChatMessage, ConversationsPage } from "@/types/conversation"

interface PageEnvelope<T> {
  items: T[]
  total: number
  page: number
  limit: number
}

export async function fetchConversations(page: number): Promise<ConversationsPage> {
  const { data: envelope } = await api.get<ApiResponse<PageEnvelope<ConversationSummary>>>(
    `/chat/conversations?page=${page}&size=20`
  )
  if (!envelope.data) throw new Error(envelope.error ?? "No data")
  return envelope.data
}

export async function fetchMessages(conversationId: string): Promise<ChatMessage[]> {
  const { data: envelope } = await api.get<ApiResponse<ChatMessage[]>>(
    `/chat/conversations/${conversationId}/messages`
  )
  if (!envelope.data) throw new Error(envelope.error ?? "No data")
  return envelope.data
}
