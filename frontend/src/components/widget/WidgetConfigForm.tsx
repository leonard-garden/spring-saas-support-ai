import { useState } from "react"
import { Button } from "@/components/ui/button"
import type { WidgetResponse } from "@/types/widget"
import { useUpdateWidgetConfig } from "@/hooks/useChatWidget"

interface Props {
  widget: WidgetResponse
  isAdmin: boolean
  onColorChange?: (color: string) => void
}

export function WidgetConfigForm({ widget, isAdmin, onColorChange }: Props) {
  const [name, setName] = useState(widget.name)
  const [welcomeMessage, setWelcomeMessage] = useState(widget.welcomeMessage)
  const [primaryColor, setPrimaryColor] = useState(widget.primaryColor)
  const [saveStatus, setSaveStatus] = useState<"idle" | "saving" | "saved" | "error">("idle")
  const [saveError, setSaveError] = useState<string | null>(null)

  const updateConfig = useUpdateWidgetConfig(widget.id)

  function handleColorChange(color: string) {
    setPrimaryColor(color)
    onColorChange?.(color)
  }

  async function handleSave() {
    setSaveStatus("saving")
    setSaveError(null)
    try {
      await updateConfig.mutateAsync({ name, welcomeMessage, primaryColor })
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

      {/* Brand Color — native color picker */}
      <div>
        <label className="block text-sm font-medium text-stone-700 mb-1">Brand Color</label>
        <div className="flex items-center gap-3">
          <input
            type="color"
            value={primaryColor}
            onChange={(e) => handleColorChange(e.target.value)}
            disabled={!isAdmin}
            className="h-10 w-10 rounded-md border border-stone-300 cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 p-0.5"
          />
          <span className="text-sm font-mono text-stone-500">{primaryColor}</span>
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
