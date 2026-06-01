import { api } from "./api"
import type { ApiResponse } from "../types/auth"
import { isAxiosError } from "axios"

export interface KnowledgeBaseResponse {
  id: string
  businessId: string
  documentCount: number
  readyCount: number
  createdAt: string
}

export async function getKb(): Promise<KnowledgeBaseResponse | null> {
  try {
    const { data: envelope } = await api.get<ApiResponse<KnowledgeBaseResponse>>("/kb")
    return envelope.data ?? null
  } catch (err) {
    if (isAxiosError(err) && err.response?.status === 404) return null
    throw err
  }
}
