import { useState, useEffect, useRef } from "react"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Progress } from "@/components/ui/progress"
import { FileText } from "lucide-react"
import { cn } from "@/lib/utils"
import { validateFile } from "@/lib/documentApi"
import { useUploadDocument } from "@/hooks/useDocuments"

interface UploadModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

type Phase = "idle" | "uploading" | "error"

const FileDropzone = ({
  onFile,
}: {
  onFile: (file: File) => void
}) => {
  const [dragActive, setDragActive] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragActive(true)
  }

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    if (e.currentTarget.contains(e.relatedTarget as Node)) return
    setDragActive(false)
  }

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragActive(false)
    const dropped = e.dataTransfer.files[0]
    if (dropped) onFile(dropped)
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0]
    if (selected) onFile(selected)
  }

  return (
    <div
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed p-8 transition-colors",
        dragActive ? "border-amber-500 bg-amber-50" : "border-stone-300 bg-stone-50",
      )}
    >
      <FileText className="h-10 w-10 text-stone-400" />
      <p className="text-sm text-stone-600">Drag &amp; drop here</p>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.txt,.md"
        className="hidden"
        onChange={handleInputChange}
      />
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => inputRef.current?.click()}
      >
        or browse
      </Button>
    </div>
  )
}

export function UploadModal({ open, onOpenChange }: UploadModalProps) {
  const [file, setFile] = useState<File | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [progress, setProgress] = useState(0)
  const [phase, setPhase] = useState<Phase>("idle")
  const [uploadError, setUploadError] = useState<string | null>(null)

  const mutation = useUploadDocument()

  useEffect(() => {
    if (!open) {
      setFile(null)
      setValidationError(null)
      setProgress(0)
      setPhase("idle")
      setUploadError(null)
    }
  }, [open])

  const handleFile = (selected: File) => {
    const result = validateFile(selected)
    if (result.ok) {
      setFile(selected)
      setValidationError(null)
    } else {
      setFile(null)
      setValidationError(result.error)
    }
  }

  const handleOpenChange = (nextOpen: boolean) => {
    if (phase === "uploading") return
    onOpenChange(nextOpen)
  }

  const handleUpload = () => {
    if (!file) return
    setPhase("uploading")
    setProgress(0)
    mutation.mutate(
      { file, onProgress: setProgress },
      {
        onSuccess: () => onOpenChange(false),
        onError: (err) => {
          setPhase("error")
          setUploadError(err instanceof Error ? err.message : "Upload failed. Please try again.")
        },
      },
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Upload Document</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <FileDropzone onFile={handleFile} />

          {file && phase === "idle" && (
            <p className="text-sm text-stone-600 truncate">
              Selected: <span className="font-medium">{file.name}</span>
            </p>
          )}

          {validationError && (
            <p className="text-sm text-red-600">{validationError}</p>
          )}

          {phase === "uploading" && (
            <div className="space-y-2">
              <p className="text-sm text-stone-600 truncate">Uploading: {file?.name}</p>
              <Progress value={progress} />
              <p className="text-right text-xs text-stone-500">{progress}%</p>
            </div>
          )}

          {phase === "error" && uploadError && (
            <p className="text-sm text-red-600">{uploadError}</p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            disabled={phase === "uploading"}
            onClick={() => onOpenChange(false)}
          >
            Cancel
          </Button>
          <Button
            className="bg-amber-500 hover:bg-amber-600 text-stone-900"
            disabled={!file || !!validationError || phase === "uploading"}
            onClick={handleUpload}
          >
            Upload
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
