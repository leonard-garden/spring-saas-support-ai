import { useQuery } from "@tanstack/react-query"
import { fetchMessages } from "@/lib/conversationsApi"

interface Props {
  conversationId: string
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })
}

export function ConversationDetail({ conversationId }: Props) {
  const { data: messages, isLoading } = useQuery({
    queryKey: ["conversation-messages", conversationId],
    queryFn: () => fetchMessages(conversationId),
  })

  if (isLoading) {
    return <p className="text-sm text-stone-400 py-2">Loading messages…</p>
  }

  if (!messages || messages.length === 0) {
    return <p className="text-sm text-stone-400 py-2">No messages yet.</p>
  }

  return (
    <div className="space-y-3 max-h-80 overflow-y-auto">
      {messages.map(msg => (
        <div
          key={msg.id}
          className={`flex flex-col ${msg.role === "USER" ? "items-end" : "items-start"}`}
        >
          <div
            className={`max-w-xs lg:max-w-md rounded-lg px-3 py-2 text-sm ${
              msg.role === "USER"
                ? "bg-amber-100 text-stone-900"
                : "bg-stone-100 text-stone-900"
            }`}
          >
            {msg.content}
          </div>
          <span className="mt-1 text-xs text-stone-400">{formatTime(msg.createdAt)}</span>
        </div>
      ))}
    </div>
  )
}
