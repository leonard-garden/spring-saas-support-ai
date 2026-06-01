import { useState } from "react"
import { Copy, Check } from "lucide-react"
import { Button } from "@/components/ui/button"

interface Props {
  snippet: string
}

export function EmbedCodeBlock({ snippet }: Props) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(snippet)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-stone-600">
        Copy and paste this script tag into the <code className="text-xs bg-stone-100 px-1 py-0.5 rounded">&lt;body&gt;</code> of your website.
      </p>
      <div className="relative rounded-lg bg-stone-900 p-4">
        <pre className="text-sm text-stone-200 font-mono whitespace-pre-wrap break-all">{snippet}</pre>
        <Button
          size="sm"
          variant="ghost"
          onClick={handleCopy}
          className="absolute top-2 right-2 text-stone-400 hover:text-white hover:bg-stone-700"
        >
          {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
          <span className="ml-1 text-xs">{copied ? "Copied!" : "Copy Script"}</span>
        </Button>
      </div>
    </div>
  )
}
