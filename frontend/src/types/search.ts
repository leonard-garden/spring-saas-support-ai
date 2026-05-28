export type SearchSource = "VECTOR" | "FTS"

export interface SearchResult {
  chunkId: string
  content: string
  score: number
  documentId: string
  documentName: string
  chunkIndex: number
  source: SearchSource
}

export interface SearchRequest {
  query: string
  topK?: number
}
