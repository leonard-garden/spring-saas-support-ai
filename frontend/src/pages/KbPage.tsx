import { useState } from "react"
import { useAuthStore } from "@/store/authStore"
import { useDocuments, useDeleteDocument } from "@/hooks/useDocuments"
import { DocumentTable, EmptyState } from "@/components/kb/DocumentTable"
import { UploadModal } from "@/components/kb/UploadModal"
import { SearchSection } from "@/components/kb/SearchSection"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Upload } from "lucide-react"

export function KbPage() {
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === "ADMIN" || user?.role === "OWNER"
  const [uploadOpen, setUploadOpen] = useState(false)
  const { data: documents = [], isLoading, isError } = useDocuments()
  const deleteMutation = useDeleteDocument()
  const readyCount = documents.filter((d) => d.status === "READY").length

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-stone-900">Knowledge Base</h1>
          <p className="mt-1 text-sm text-stone-500">
            Train your AI chatbot on your documentation, FAQs, and product info.
          </p>
        </div>
        {isAdmin && (
          <Button
            className="bg-amber-500 hover:bg-amber-600 text-stone-900 font-medium"
            onClick={() => setUploadOpen(true)}
          >
            <Upload className="mr-2 h-4 w-4" />
            Upload Document
          </Button>
        )}
      </div>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <p className="text-sm text-stone-500">Loading documents…</p>
            </div>
          ) : isError ? (
            <div className="flex items-center justify-center py-16">
              <p className="text-sm text-red-500">Failed to load documents. Please refresh.</p>
            </div>
          ) : documents.length === 0 ? (
            <EmptyState />
          ) : (
            <DocumentTable
              documents={documents}
              isAdmin={isAdmin}
              onDelete={(id) => deleteMutation.mutate(id)}
              deletingId={deleteMutation.isPending ? (deleteMutation.variables ?? null) : null}
            />
          )}
        </CardContent>
      </Card>

      <SearchSection readyCount={readyCount} />

      <UploadModal open={uploadOpen} onOpenChange={setUploadOpen} />
    </div>
  )
}
