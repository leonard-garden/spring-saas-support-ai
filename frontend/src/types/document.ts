export type DocumentStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED"

export interface DocumentResponse {
  id: string
  fileName: string
  fileType: string
  fileSize: number
  chunkCount: number | null
  status: DocumentStatus
  errorMessage: string | null
  createdAt: string
}
