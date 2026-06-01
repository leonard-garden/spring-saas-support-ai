import React, { useState, useEffect } from "react"
import { MessageSquare, ChevronDown, ChevronUp } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { ConversationDetail } from "./ConversationDetail"
import { fetchConversations } from "@/lib/conversationsApi"
import type { ConversationSummary } from "@/types/conversation"

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  })
}

function visitorLabel(sessionId: string | null): string {
  if (!sessionId) return "anonymous"
  return sessionId.slice(0, 8)
}

export function ConversationsTab() {
  const [page, setPage] = useState(0)
  const [allItems, setAllItems] = useState<ConversationSummary[]>([])
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const { data, isFetching } = useQuery({
    queryKey: ["conversations", page],
    queryFn: () => fetchConversations(page),
    placeholderData: (prev) => prev,
  })

  useEffect(() => {
    if (data?.items) {
      setAllItems(prev => {
        const existingIds = new Set(prev.map(i => i.id))
        const fresh = data.items.filter(i => !existingIds.has(i.id))
        return fresh.length > 0 ? [...prev, ...fresh] : prev
      })
    }
  }, [data])

  function handleToggle(id: string) {
    setExpandedId(prev => (prev === id ? null : id))
  }

  const hasMore = data ? (page + 1) * data.limit < data.total : false

  function handleLoadMore() {
    setPage(p => p + 1)
  }

  if (!isFetching && allItems.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 gap-3">
        <MessageSquare className="h-10 w-10 text-stone-300" />
        <p className="text-sm text-stone-500">No conversations yet.</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="overflow-hidden rounded-lg border border-stone-200">
        <table className="w-full text-sm">
          <thead className="bg-stone-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-stone-500 uppercase tracking-wide">
                Visitor ID
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-stone-500 uppercase tracking-wide">
                Started
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-stone-500 uppercase tracking-wide">
                Last Message
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium text-stone-500 uppercase tracking-wide">
                Messages
              </th>
              <th className="w-8" />
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-200 bg-white">
            {allItems.map(conv => (
              <React.Fragment key={conv.id}>
                <tr
                  className="cursor-pointer hover:bg-stone-50 transition-colors"
                  onClick={() => handleToggle(conv.id)}
                >
                  <td className="px-4 py-3 font-mono text-xs text-stone-700">
                    {visitorLabel(conv.sessionId)}
                  </td>
                  <td className="px-4 py-3 text-stone-600">{formatDate(conv.createdAt)}</td>
                  <td className="px-4 py-3 text-stone-600">{formatDate(conv.lastMessageAt)}</td>
                  <td className="px-4 py-3 text-right text-stone-700">{conv.messageCount}</td>
                  <td className="px-4 py-3 text-stone-400">
                    {expandedId === conv.id ? (
                      <ChevronUp className="h-4 w-4" />
                    ) : (
                      <ChevronDown className="h-4 w-4" />
                    )}
                  </td>
                </tr>
                {expandedId === conv.id && (
                  <tr>
                    <td colSpan={5} className="bg-stone-50 px-4 py-4">
                      <ConversationDetail conversationId={conv.id} />
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      </div>
      {hasMore && (
        <div className="flex justify-center">
          <Button
            variant="outline"
            onClick={handleLoadMore}
            disabled={isFetching}
            className="text-stone-700 border-stone-300"
          >
            {isFetching ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </div>
  )
}
