import { api } from "./api"
import type { ApiResponse } from "../types/auth"
import type { SearchRequest, SearchResult } from "../types/search"

export async function searchKb(request: SearchRequest): Promise<SearchResult[]> {
  const { data: envelope } = await api.post<ApiResponse<SearchResult[]>>("/kb/search", request)
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}
