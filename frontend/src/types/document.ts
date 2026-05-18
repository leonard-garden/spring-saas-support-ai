export type DocumentStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED"

export interface DocumentResponse {
  id: string
  filename: string
  contentType: string
  sizeBytes: number
  chunkCount: number | null
  status: DocumentStatus
  errorMessage: string | null
  createdAt: string
}
