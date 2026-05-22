import { useState } from "react"
import { useSearch } from "@/hooks/useSearch"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Search } from "lucide-react"
import { cn } from "@/lib/utils"
import type { SearchResult } from "@/types/search"

const ScoreBadge = ({ score }: { score: number }) => (
  <span
    className={cn(
      "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium tabular-nums",
      score >= 0.7
        ? "bg-green-100 text-green-700"
        : "bg-amber-100 text-amber-700"
    )}
  >
    {score.toFixed(2)}
  </span>
)

export const SearchResultItem = ({ result }: { result: SearchResult }) => (
  <div className="rounded-lg border border-stone-200 bg-white p-4 space-y-2">
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs text-stone-500 truncate">{result.documentName}</span>
      <ScoreBadge score={result.score} />
    </div>
    <p className="text-sm text-stone-700 leading-relaxed line-clamp-4">{result.content}</p>
  </div>
)

export const EmptySearchState = () => (
  <div className="flex items-center justify-center py-8">
    <p className="text-sm text-stone-400">No results for this query.</p>
  </div>
)

interface SearchSectionProps {
  readyCount: number
}

export function SearchSection({ readyCount }: SearchSectionProps) {
  const [query, setQuery] = useState("")
  const { mutate: search, data: results, isPending, isSuccess } = useSearch()

  if (readyCount === 0) return null

  const handleSearch = () => {
    const trimmed = query.trim()
    if (!trimmed) return
    search(trimmed)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") handleSearch()
  }

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-base font-medium text-stone-900">Search Knowledge Base</h2>
        <p className="mt-0.5 text-sm text-stone-500">Query your indexed documents.</p>
      </div>

      <div className="flex gap-2">
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask a question or enter keywords…"
          className="focus-visible:ring-amber-400"
          disabled={isPending}
        />
        <Button
          onClick={handleSearch}
          disabled={isPending || !query.trim()}
          className="bg-amber-500 hover:bg-amber-600 text-stone-900 font-medium shrink-0"
        >
          <Search className="mr-2 h-4 w-4" />
          {isPending ? "Searching…" : "Search"}
        </Button>
      </div>

      {isSuccess && (
        <div className="space-y-2">
          {results && results.length > 0 ? (
            results.map((r) => <SearchResultItem key={r.chunkId} result={r} />)
          ) : (
            <EmptySearchState />
          )}
        </div>
      )}
    </div>
  )
}
