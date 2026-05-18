import { api } from "./api"
import type { ApiResponse } from "../types/auth"
import type { DocumentResponse } from "../types/document"

export const MAX_FILE_SIZE = 10 * 1024 * 1024
export const ALLOWED_EXTENSIONS = [".pdf", ".txt", ".md"]
export const ALLOWED_MIME_TYPES = ["application/pdf", "text/plain", "text/markdown", "text/x-markdown"]

export function validateFile(file: File): { ok: true } | { ok: false; error: string } {
  const name = file.name.toLowerCase()
  const hasValidExtension = ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext))
  if (!hasValidExtension) {
    return { ok: false, error: `File type not supported. Allowed types: ${ALLOWED_EXTENSIONS.join(", ")}` }
  }
  if (file.size > MAX_FILE_SIZE) {
    return { ok: false, error: `File size exceeds 10 MB limit (${(file.size / 1024 / 1024).toFixed(1)} MB)` }
  }
  return { ok: true }
}

interface DocumentListResponse {
  documents: DocumentResponse[]
  total: number
}

export async function listDocuments(): Promise<DocumentResponse[]> {
  const { data: envelope } = await api.get<ApiResponse<DocumentListResponse>>("/kb/documents")
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data.documents
}

export async function getDocument(id: string): Promise<DocumentResponse> {
  const { data: envelope } = await api.get<ApiResponse<DocumentResponse>>(`/kb/documents/${id}`)
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}

export async function uploadDocument(
  file: File,
  onProgress: (pct: number) => void,
): Promise<DocumentResponse> {
  const formData = new FormData()
  formData.append("file", file)
  const { data: envelope } = await api.post<ApiResponse<DocumentResponse>>("/kb/documents", formData, {
    headers: { "Content-Type": undefined },
    onUploadProgress: (e) => {
      const total = e.total ?? 0
      const pct = total > 0 ? Math.round((e.loaded / total) * 100) : 0
      onProgress(pct)
    },
  })
  if (!envelope.data) throw new Error(envelope.error ?? "Empty response")
  return envelope.data
}

export async function deleteDocument(id: string): Promise<void> {
  await api.delete(`/kb/documents/${id}`)
}
