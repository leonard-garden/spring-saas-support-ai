import { useState } from "react"
import { Button } from "@/components/ui/button"
import type { WidgetResponse } from "@/types/widget"
import { useUpdateWidgetConfig, useReplaceWidgetKbs } from "@/hooks/useChatWidget"

interface Props {
  widget: WidgetResponse
  kbId: string | null
  kbDocCount: number
  isAdmin: boolean
}

export function WidgetConfigForm({ widget, kbId, kbDocCount, isAdmin }: Props) {
  const [name, setName] = useState(widget.name)
  const [welcomeMessage, setWelcomeMessage] = useState(widget.welcomeMessage)
  const [primaryColor, setPrimaryColor] = useState(widget.primaryColor)
  const [kbSelected, setKbSelected] = useState(kbId ? widget.kbIds.includes(kbId) : false)
  const [saveStatus, setSaveStatus] = useState<"idle" | "saving" | "saved" | "error">("idle")
  const [saveError, setSaveError] = useState<string | null>(null)

  const updateConfig = useUpdateWidgetConfig(widget.id)
  const replaceKbs = useReplaceWidgetKbs(widget.id)

  async function handleSave() {
    setSaveStatus("saving")
    setSaveError(null)
    try {
      await updateConfig.mutateAsync({ name, welcomeMessage, primaryColor })
      await replaceKbs.mutateAsync({ kbIds: kbId && kbSelected ? [kbId] : [] })
      setSaveStatus("saved")
      setTimeout(() => setSaveStatus("idle"), 3000)
    } catch (e: unknown) {
      setSaveStatus("error")
      setSaveError(e instanceof Error ? e.message : "Save failed")
    }
  }

  return (
    <div className="space-y-6 max-w-lg">
      {/* Bot Name */}
      <div>
        <label className="block text-sm font-medium text-stone-700 mb-1">Bot Name</label>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={!isAdmin}
          maxLength={100}
          className="w-full rounded-md border border-stone-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400 disabled:bg-stone-50 disabled:text-stone-400"
        />
      </div>

      {/* Brand Color */}
      <div>
        <label className="block text-sm font-medium text-stone-700 mb-1">Brand Color</label>
        <div className="flex items-center gap-3">
          <input
            type="text"
            value={primaryColor}
            onChange={(e) => setPrimaryColor(e.target.value)}
            disabled={!isAdmin}
            placeholder="#3B82F6"
            className="w-36 rounded-md border border-stone-300 px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-amber-400 disabled:bg-stone-50 disabled:text-stone-400"
          />
          <div
            className="h-8 w-8 rounded-md border border-stone-300 shrink-0"
            style={{ backgroundColor: primaryColor }}
          />
        </div>
      </div>

      {/* Welcome Message */}
      <div>
        <label className="block text-sm font-medium text-stone-700 mb-1">Welcome Message</label>
        <textarea
          value={welcomeMessage}
          onChange={(e) => setWelcomeMessage(e.target.value)}
          disabled={!isAdmin}
          rows={3}
          maxLength={500}
          className="w-full rounded-md border border-stone-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400 disabled:bg-stone-50 disabled:text-stone-400 resize-none"
        />
      </div>

      {/* KB Selection */}
      {kbId && (
        <div>
          <label className="block text-sm font-medium text-stone-700 mb-2">Knowledge Bases</label>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={kbSelected}
              onChange={(e) => setKbSelected(e.target.checked)}
              disabled={!isAdmin}
              className="h-4 w-4 rounded border-stone-300 text-amber-500 focus:ring-amber-400"
            />
            <span className="text-sm text-stone-700">
              Knowledge Base
              <span className="ml-2 text-xs text-stone-500">({kbDocCount} docs ready)</span>
            </span>
          </label>
        </div>
      )}

      {/* Status */}
      {saveStatus === "saved" && (
        <p className="text-sm text-green-600">Changes saved.</p>
      )}
      {saveStatus === "error" && (
        <p className="text-sm text-red-600">{saveError}</p>
      )}

      {isAdmin && (
        <Button
          onClick={handleSave}
          disabled={saveStatus === "saving"}
          className="bg-amber-500 hover:bg-amber-600 text-stone-900 font-medium"
        >
          {saveStatus === "saving" ? "Saving…" : "Save Changes"}
        </Button>
      )}
    </div>
  )
}
