import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { getWidget, createWidget, updateWidgetConfig, replaceWidgetKnowledgeBases } from "@/lib/chatWidgetApi"
import type { UpdateWidgetConfigRequest, ReplaceKnowledgeBasesRequest } from "@/types/widget"

export const WIDGET_QUERY_KEY = ["widget"]

export function useWidget() {
  return useQuery({
    queryKey: WIDGET_QUERY_KEY,
    queryFn: getWidget,
    retry: (failureCount, error: unknown) => {
      // Don't retry on 404 (widget not yet created)
      const status = (error as { response?: { status?: number } })?.response?.status
      if (status === 404) return false
      return failureCount < 2
    },
  })
}

export function useCreateWidget() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: createWidget,
    onSuccess: (data) => qc.setQueryData(WIDGET_QUERY_KEY, data),
  })
}

export function useUpdateWidgetConfig(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: UpdateWidgetConfigRequest) => updateWidgetConfig(id, req),
    onSuccess: (data) => qc.setQueryData(WIDGET_QUERY_KEY, data),
  })
}

export function useReplaceWidgetKbs(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ReplaceKnowledgeBasesRequest) => replaceWidgetKnowledgeBases(id, req),
    onSuccess: (data) => qc.setQueryData(WIDGET_QUERY_KEY, data),
  })
}
