export interface WidgetResponse {
  id: string
  name: string
  welcomeMessage: string
  primaryColor: string
  isActive: boolean
  kbIds: string[]
  embedSnippet: string
  createdAt: string
}

export interface UpdateWidgetConfigRequest {
  name?: string
  welcomeMessage?: string
  primaryColor?: string
}

export interface ReplaceKnowledgeBasesRequest {
  kbIds: string[]
}
