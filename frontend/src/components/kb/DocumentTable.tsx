import { useState, useEffect } from "react"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Loader2, FileText, Trash2, AlertCircle } from "lucide-react"
import { cn } from "@/lib/utils"
import type { DocumentResponse, DocumentStatus } from "@/types/document"
import { useStatusPoller } from "@/hooks/useStatusPoller"

interface DocumentTableProps {
  documents: DocumentResponse[]
  isAdmin: boolean
  onDelete: (id: string) => void
  deletingId?: string | null
}

const StatusBadge = ({ status }: { status: DocumentStatus }) => {
  if (status === "READY") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-green-100 text-green-700">
        READY
      </span>
    )
  }
  if (status === "PROCESSING") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-amber-100 text-amber-700">
        <Loader2 className="h-3 w-3 animate-spin" />
        PROCESSING
      </span>
    )
  }
  if (status === "PENDING") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-stone-100 text-stone-500">
        PENDING
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-red-100 text-red-600">
      <AlertCircle className="h-3 w-3" />
      FAILED
    </span>
  )
}

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(0)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const DocumentRow = ({
  doc,
  isAdmin,
  onDelete,
  deletingId,
}: {
  doc: DocumentResponse
  isAdmin: boolean
  onDelete: (id: string) => void
  deletingId?: string | null
}) => {
  const live = useStatusPoller(doc.id, doc.status)
  const effectiveDoc = live ?? doc
  const [confirmState, setConfirmState] = useState<"idle" | "confirming">("idle")

  useEffect(() => {
    if (confirmState !== "confirming") return
    const timer = setTimeout(() => setConfirmState("idle"), 5000)
    return () => clearTimeout(timer)
  }, [confirmState])

  const isDeleting = deletingId === doc.id
  const isProcessing = effectiveDoc.status === "PROCESSING"
  const isFailed = effectiveDoc.status === "FAILED"

  return (
    <>
      <TableRow className={cn(isFailed && "bg-red-50")}>
        <TableCell className="font-medium text-stone-800">{effectiveDoc.filename}</TableCell>
        <TableCell className="text-stone-600">{effectiveDoc.contentType}</TableCell>
        <TableCell className="text-stone-600">{formatFileSize(effectiveDoc.sizeBytes)}</TableCell>
        <TableCell className="text-stone-600">
          {effectiveDoc.chunkCount !== null ? effectiveDoc.chunkCount : "—"}
        </TableCell>
        <TableCell>
          <StatusBadge status={effectiveDoc.status} />
        </TableCell>
        <TableCell className="text-stone-600">
          {new Date(effectiveDoc.createdAt).toLocaleDateString("en-US", {
            month: "short",
            day: "numeric",
            year: "numeric",
          })}
        </TableCell>
        <TableCell>
          {isAdmin && (
            <>
              {confirmState === "idle" ? (
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 w-7 p-0 text-stone-400 hover:text-red-600"
                  disabled={isProcessing || isDeleting}
                  onClick={() => setConfirmState("confirming")}
                  aria-label="Delete document"
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              ) : (
                <div className="flex items-center gap-1.5 whitespace-nowrap">
                  <span className="text-xs text-stone-500">Delete?</span>
                  <Button
                    size="sm"
                    variant="destructive"
                    className="h-6 px-2.5 text-xs"
                    disabled={isDeleting}
                    onClick={() => {
                      setConfirmState("idle")
                      onDelete(doc.id)
                    }}
                  >
                    Confirm
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="h-6 w-6 p-0 text-stone-400 hover:text-stone-700"
                    onClick={() => setConfirmState("idle")}
                    aria-label="Cancel delete"
                  >
                    ✕
                  </Button>
                </div>
              )}
            </>
          )}
        </TableCell>
      </TableRow>
      {isFailed && effectiveDoc.errorMessage && (
        <TableRow className="bg-red-50 hover:bg-red-50">
          <TableCell colSpan={7} className="py-1 text-xs text-red-600">
            <span className="font-medium">Error:</span> {effectiveDoc.errorMessage}
          </TableCell>
        </TableRow>
      )}
    </>
  )
}

export const EmptyState = () => (
  <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
    <FileText className="h-10 w-10 text-stone-300" />
    <p className="text-base font-medium text-stone-700">No documents yet</p>
    <p className="text-sm text-stone-400">Upload a PDF, TXT, or Markdown file to get started.</p>
  </div>
)

export const DocumentTable = ({ documents, isAdmin, onDelete, deletingId }: DocumentTableProps) => (
  <Table>
    <TableHeader>
      <TableRow>
        <TableHead>Name</TableHead>
        <TableHead>Type</TableHead>
        <TableHead>Size</TableHead>
        <TableHead>Chunks</TableHead>
        <TableHead>Status</TableHead>
        <TableHead>Uploaded</TableHead>
        <TableHead className="w-24">Actions</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      {documents.map((doc) => (
        <DocumentRow
          key={doc.id}
          doc={doc}
          isAdmin={isAdmin}
          onDelete={onDelete}
          deletingId={deletingId}
        />
      ))}
    </TableBody>
  </Table>
)
