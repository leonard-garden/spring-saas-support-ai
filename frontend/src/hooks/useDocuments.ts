import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { listDocuments, uploadDocument, deleteDocument } from "@/lib/documentApi"
import type { DocumentResponse } from "@/types/document"

export const DOCUMENTS_QUERY_KEY = ["documents"] as const

export function useDocuments() {
  return useQuery({
    queryKey: DOCUMENTS_QUERY_KEY,
    queryFn: listDocuments,
    refetchOnWindowFocus: true,
    staleTime: 30_000,
  })
}

export function useDeleteDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteDocument,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DOCUMENTS_QUERY_KEY }),
  })
}

interface UploadVariables {
  file: File
  onProgress: (pct: number) => void
}

export function useUploadDocument() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, onProgress }: UploadVariables) => uploadDocument(file, onProgress),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: DOCUMENTS_QUERY_KEY }),
  })
}

// Re-export DocumentResponse so consumers can use it from this module if needed
export type { DocumentResponse }
