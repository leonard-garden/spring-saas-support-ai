import { useState } from "react"
import { MessageSquare, Plus } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useAuthStore } from "@/store/authStore"
import { useWidget, useCreateWidget, useReplaceWidgetKbs } from "@/hooks/useChatWidget"
import { WidgetConfigForm } from "@/components/widget/WidgetConfigForm"
import { EmbedCodeBlock } from "@/components/widget/EmbedCodeBlock"
import { WidgetPreview } from "@/components/widget/WidgetPreview"
import { ConversationsTab } from "@/components/chat/ConversationsTab"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { api } from "@/lib/api"
import type { ApiResponse } from "@/types/auth"
import type { WidgetResponse } from "@/types/widget"

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

type TabId = "config" | "kbs" | "embed" | "conversations"

function TabButton({ id, label, active, onClick }: { id: TabId; label: string; active: boolean; onClick: (id: TabId) => void }) {
  return (
    <button
      onClick={() => onClick(id)}
      className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
        active ? "bg-white text-stone-900 shadow-sm" : "text-stone-600 hover:text-stone-900"
      }`}
    >
      {label}
    </button>
  )
}

function KbSelectionTab({ widget, kb, isAdmin }: { widget: WidgetResponse; kb: KbInfo | undefined; isAdmin: boolean }) {
  const [kbSelected, setKbSelected] = useState(kb ? widget.kbIds.includes(kb.id) : false)
  const [saveStatus, setSaveStatus] = useState<"idle" | "saving" | "saved" | "error">("idle")
  const [saveError, setSaveError] = useState<string | null>(null)
  const replaceKbs = useReplaceWidgetKbs(widget.id)

  async function handleSave() {
    setSaveStatus("saving")
    setSaveError(null)
    try {
      await replaceKbs.mutateAsync({ kbIds: kb && kbSelected ? [kb.id] : [] })
      setSaveStatus("saved")
      setTimeout(() => setSaveStatus("idle"), 3000)
    } catch (e: unknown) {
      setSaveStatus("error")
      setSaveError(e instanceof Error ? e.message : "Save failed")
    }
  }

  return (
    <Card>
      <CardContent className="pt-6 max-w-lg space-y-4">
        <div>
          <h2 className="text-base font-medium text-stone-800">Knowledge Base Selection</h2>
          <p className="mt-1 text-sm text-stone-500">
            Select which knowledge bases power this widget's responses.
          </p>
        </div>
        {kb ? (
          <label className="flex items-center gap-3 cursor-pointer p-3 rounded-lg border border-stone-200 hover:bg-stone-50 transition-colors">
            <input
              type="checkbox"
              checked={kbSelected}
              onChange={(e) => setKbSelected(e.target.checked)}
              disabled={!isAdmin}
              className="h-4 w-4 rounded border-stone-300 accent-amber-500"
            />
            <div>
              <p className="text-sm font-medium text-stone-800">Knowledge Base</p>
              <p className="text-xs text-stone-500">{kb.readyCount} docs ready</p>
            </div>
          </label>
        ) : (
          <p className="text-sm text-stone-400">No knowledge base found.</p>
        )}
        {saveStatus === "saved" && <p className="text-sm text-green-600">Saved.</p>}
        {saveStatus === "error" && <p className="text-sm text-red-600">{saveError}</p>}
        {isAdmin && (
          <Button
            onClick={handleSave}
            disabled={saveStatus === "saving"}
            className="bg-amber-500 hover:bg-amber-600 text-stone-900 font-medium"
          >
            {saveStatus === "saving" ? "Saving…" : "Save"}
          </Button>
        )}
      </CardContent>
    </Card>
  )
}

export function ChatWidgetPage() {
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === "ADMIN" || user?.role === "OWNER"
  const [activeTab, setActiveTab] = useState<TabId>("config")
  const [previewColor, setPreviewColor] = useState<string | null>(null)

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
          <p className="mt-1 text-sm text-stone-500">Set up your embeddable AI support chatbot.</p>
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
        <p className="mt-1 text-sm text-stone-500">Configure your embeddable AI support chatbot.</p>
      </div>

      <div className="inline-flex items-center gap-1 rounded-lg bg-stone-100 p-1">
        <TabButton id="config" label="Configuration" active={activeTab === "config"} onClick={setActiveTab} />
        <TabButton id="kbs" label="Knowledge Bases" active={activeTab === "kbs"} onClick={setActiveTab} />
        <TabButton id="embed" label="Embed Code" active={activeTab === "embed"} onClick={setActiveTab} />
        <TabButton id="conversations" label="Conversations" active={activeTab === "conversations"} onClick={setActiveTab} />
      </div>

      {activeTab === "config" && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <Card>
            <CardContent className="pt-6">
              <WidgetConfigForm
                widget={widget}
                isAdmin={isAdmin}
                onColorChange={setPreviewColor}
              />
            </CardContent>
          </Card>
          <div className="hidden lg:block">
            <WidgetPreview
              name={widget.name}
              welcomeMessage={widget.welcomeMessage}
              primaryColor={previewColor ?? widget.primaryColor}
            />
          </div>
        </div>
      )}

      {activeTab === "kbs" && (
        <KbSelectionTab widget={widget} kb={kb} isAdmin={isAdmin} />
      )}

      {activeTab === "embed" && (
        <Card>
          <CardContent className="pt-6">
            <h2 className="text-base font-medium text-stone-800 mb-4">Embed on Your Website</h2>
            <EmbedCodeBlock snippet={widget.embedSnippet} />
          </CardContent>
        </Card>
      )}

      {activeTab === "conversations" && <ConversationsTab />}
    </div>
  )
}
