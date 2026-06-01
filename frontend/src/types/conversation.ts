export interface ConversationSummary {
  id: string
  chatbotId: string
  chatbotName: string
  sessionId: string | null
  messageCount: number
  lastMessageAt: string
  createdAt: string
}

export interface ChatMessage {
  id: string
  role: "USER" | "ASSISTANT"
  content: string
  createdAt: string
}

export interface ConversationsPage {
  items: ConversationSummary[]
  total: number
  page: number
  limit: number
}
