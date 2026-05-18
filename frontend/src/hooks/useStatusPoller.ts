import { useEffect, useRef } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { getDocument } from "@/lib/documentApi"
import type { DocumentStatus, DocumentResponse } from "@/types/document"
import { DOCUMENTS_QUERY_KEY } from "./useDocuments"

export function useStatusPoller(docId: string, currentStatus: DocumentStatus): DocumentResponse | null {
  const queryClient = useQueryClient()
  const isProcessing = currentStatus === "PROCESSING"
  const prevStatusRef = useRef<string | undefined>(undefined)

  const { data } = useQuery({
    queryKey: ["document", docId],
    queryFn: () => getDocument(docId),
    refetchInterval: isProcessing ? 3000 : false,
    enabled: isProcessing,
    gcTime: 0,
  })

  useEffect(() => {
    const newStatus = data?.status
    const prev = prevStatusRef.current
    prevStatusRef.current = newStatus

    if (prev === "PROCESSING" && (newStatus === "READY" || newStatus === "FAILED")) {
      queryClient.invalidateQueries({ queryKey: DOCUMENTS_QUERY_KEY })
    }
  }, [data?.status, queryClient])

  return isProcessing ? (data ?? null) : null
}
