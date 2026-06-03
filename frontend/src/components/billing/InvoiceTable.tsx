import { AlertCircle, CheckCircle2, Download } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useInvoices } from "@/hooks/useBilling"
import type { Invoice } from "@/types/billing"

// ─── Sub-components ──────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: Invoice["status"] }) {
  if (status === "paid") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 border border-emerald-200 px-2 py-0.5 text-xs font-medium text-emerald-700">
        <CheckCircle2 className="h-3 w-3" />
        Paid
      </span>
    )
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-red-50 border border-red-200 px-2 py-0.5 text-xs font-medium text-red-700">
      <AlertCircle className="h-3 w-3" />
      Failed
    </span>
  )
}

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <tr key={i} className="border-b last:border-0">
          <td className="px-4 py-3"><Skeleton className="h-4 w-24" /></td>
          <td className="px-4 py-3"><Skeleton className="h-4 w-40" /></td>
          <td className="px-4 py-3"><Skeleton className="h-4 w-16" /></td>
          <td className="px-4 py-3"><Skeleton className="h-5 w-14 rounded-full" /></td>
          <td className="px-4 py-3 text-right"><Skeleton className="h-4 w-14 ml-auto" /></td>
        </tr>
      ))}
    </>
  )
}

function InvoiceRow({ invoice }: { invoice: Invoice }) {
  const formattedDate = new Date(invoice.date).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  })

  return (
    <tr className="hover:bg-muted/20 transition-colors">
      <td className="px-4 py-3 text-muted-foreground tabular-nums">{formattedDate}</td>
      <td className="px-4 py-3 font-medium">{invoice.description}</td>
      <td className="px-4 py-3 tabular-nums">${invoice.amount.toFixed(2)}</td>
      <td className="px-4 py-3">
        <StatusBadge status={invoice.status} />
      </td>
      <td className="px-4 py-3 text-right">
        {invoice.pdfUrl !== null ? (
          <a
            href={invoice.pdfUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
          >
            <Download className="h-3.5 w-3.5" />
            Download
          </a>
        ) : (
          <span className="text-xs text-muted-foreground/50">—</span>
        )}
      </td>
    </tr>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

export function InvoiceTable() {
  const { data: invoices, isLoading, isError } = useInvoices()

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Payment History</h2>
          <p className="text-sm text-muted-foreground">All invoices for your account</p>
        </div>
      </div>

      <Card>
        <CardContent className="p-0">
          {isError ? (
            <div className="flex items-center justify-center gap-2 py-12 text-sm text-red-600">
              <AlertCircle className="h-4 w-4 shrink-0" />
              Could not load invoices. Please try again later.
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-muted/40">
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">Date</th>
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">Description</th>
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">Amount</th>
                    <th className="px-4 py-3 text-left font-medium text-muted-foreground">Status</th>
                    <th className="px-4 py-3 text-right font-medium text-muted-foreground">Invoice</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {isLoading ? (
                    <SkeletonRows />
                  ) : (
                    invoices?.map((invoice) => (
                      <InvoiceRow key={invoice.id} invoice={invoice} />
                    ))
                  )}
                </tbody>
              </table>

              {!isLoading && invoices?.length === 0 && (
                <div className="py-12 text-center text-sm text-muted-foreground">
                  No invoices yet
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
