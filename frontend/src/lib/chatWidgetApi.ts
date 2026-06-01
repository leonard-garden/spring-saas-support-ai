import { api } from "./api"
import type { ApiResponse } from "../types/auth"
import type { WidgetResponse, UpdateWidgetConfigRequest, ReplaceKnowledgeBasesRequest } from "../types/widget"

export async function getWidget(): Promise<WidgetResponse> {
  const { data: envelope } = await api.get<ApiResponse<WidgetResponse>>("/chat/widget")
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}

export async function createWidget(): Promise<WidgetResponse> {
  const { data: envelope } = await api.post<ApiResponse<WidgetResponse>>("/chat/widget")
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}

export async function updateWidgetConfig(id: string, req: UpdateWidgetConfigRequest): Promise<WidgetResponse> {
  const { data: envelope } = await api.put<ApiResponse<WidgetResponse>>(`/chat/widget/${id}/config`, req)
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}

export async function replaceWidgetKnowledgeBases(id: string, req: ReplaceKnowledgeBasesRequest): Promise<WidgetResponse> {
  const { data: envelope } = await api.put<ApiResponse<WidgetResponse>>(`/chat/widget/${id}/knowledge-bases`, req)
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}
