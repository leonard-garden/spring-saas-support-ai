import { useState } from "react"
import { MessageSquare, Plus } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useAuthStore } from "@/store/authStore"
import { useWidget, useCreateWidget } from "@/hooks/useChatWidget"
import { WidgetConfigForm } from "@/components/widget/WidgetConfigForm"
import { EmbedCodeBlock } from "@/components/widget/EmbedCodeBlock"
import { WidgetPreview } from "@/components/widget/WidgetPreview"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { api } from "@/lib/api"
import type { ApiResponse } from "@/types/auth"

interface KbInfo {
  id: string
  readyCount: number
}

function useKb() {
  return useQuery<KbInfo>({
    queryKey: ["kb"],
    queryFn: async () => {
      const { data: envelope } = await api.get<ApiResponse<{ id: string; readyCount: number }>>("/kb")
      if (!envelope.data) throw new Error("KB not found")
      return { id: envelope.data.id, readyCount: envelope.data.readyCount }
    },
    retry: false,
  })
}

type TabId = "config" | "kbs" | "embed"

interface TabButtonProps {
  id: TabId
  label: string
  active: boolean
  onClick: (id: TabId) => void
}

function TabButton({ id, label, active, onClick }: TabButtonProps) {
  return (
    <button
      onClick={() => onClick(id)}
      className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
        active
          ? "bg-white text-stone-900 shadow-sm"
          : "text-stone-600 hover:text-stone-900"
      }`}
    >
      {label}
    </button>
  )
}

export function ChatWidgetPage() {
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === "ADMIN" || user?.role === "OWNER"
  const [activeTab, setActiveTab] = useState<TabId>("config")

  const { data: widget, isLoading, error } = useWidget()
  const createWidget = useCreateWidget()
  const { data: kb } = useKb()

  const is404 = (error as { response?: { status?: number } } | null)?.response?.status === 404

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <p className="text-sm text-stone-500">Loading…</p>
      </div>
    )
  }

  if (is404 || !widget) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold text-stone-900">Chat Widget</h1>
          <p className="mt-1 text-sm text-stone-500">
            Set up your embeddable AI chat widget.
          </p>
        </div>
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16 gap-4">
            <MessageSquare className="h-10 w-10 text-stone-300" />
            <p className="text-stone-500 text-sm">No widget configured yet.</p>
            {isAdmin && (
              <Button
                onClick={() => createWidget.mutate()}
                disabled={createWidget.isPending}
                className="bg-amber-500 hover:bg-amber-600 text-stone-900 font-medium"
              >
                <Plus className="mr-2 h-4 w-4" />
                {createWidget.isPending ? "Creating…" : "Create Widget"}
              </Button>
            )}
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-stone-900">Chat Widget</h1>
        <p className="mt-1 text-sm text-stone-500">
          Configure your embeddable AI support chatbot.
        </p>
      </div>

      {/* Tab bar */}
      <div className="inline-flex items-center gap-1 rounded-lg bg-stone-100 p-1">
        <TabButton id="config" label="Configuration" active={activeTab === "config"} onClick={setActiveTab} />
        <TabButton id="kbs" label="Knowledge Bases" active={activeTab === "kbs"} onClick={setActiveTab} />
        <TabButton id="embed" label="Embed Code" active={activeTab === "embed"} onClick={setActiveTab} />
      </div>

      {/* Configuration tab */}
      {activeTab === "config" && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <Card>
            <CardContent className="pt-6">
              <WidgetConfigForm
                widget={widget}
                kbId={kb?.id ?? null}
                kbDocCount={kb?.readyCount ?? 0}
                isAdmin={isAdmin}
              />
            </CardContent>
          </Card>
          <div className="hidden lg:block">
            <WidgetPreview
              name={widget.name}
              welcomeMessage={widget.welcomeMessage}
              primaryColor={widget.primaryColor}
            />
          </div>
        </div>
      )}

      {/* Knowledge Bases tab */}
      {activeTab === "kbs" && (
        <Card>
          <CardContent className="pt-6">
            <h2 className="text-base font-medium text-stone-800 mb-4">Knowledge Base Selection</h2>
            <p className="text-sm text-stone-500 mb-4">
              Select which knowledge bases power this widget's responses.
            </p>
            <WidgetConfigForm
              widget={widget}
              kbId={kb?.id ?? null}
              kbDocCount={kb?.readyCount ?? 0}
              isAdmin={isAdmin}
            />
          </CardContent>
        </Card>
      )}

      {/* Embed Code tab */}
      {activeTab === "embed" && (
        <Card>
          <CardContent className="pt-6">
            <h2 className="text-base font-medium text-stone-800 mb-4">Embed on Your Website</h2>
            <EmbedCodeBlock snippet={widget.embedSnippet} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
