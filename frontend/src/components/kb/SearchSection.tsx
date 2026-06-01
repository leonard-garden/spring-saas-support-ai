import { useState } from "react"
import { useSearch } from "@/hooks/useSearch"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Search, FileText } from "lucide-react"
import { cn } from "@/lib/utils"
import type { SearchResult, SearchSource } from "@/types/search"

const HIGH_SCORE_THRESHOLD = 0.7

const ScoreBadge = ({ score }: { score: number }) => (
  <span
    className={cn(
      "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium tabular-nums shrink-0",
      score >= HIGH_SCORE_THRESHOLD
        ? "bg-green-100 text-green-700"
        : "bg-amber-100 text-amber-700"
    )}
  >
    {score.toFixed(2)}
  </span>
)

const SourceBadge = ({ source }: { source: SearchSource }) => (
  <span
    className={cn(
      "inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium shrink-0",
      source === "VECTOR"
        ? "bg-blue-50 text-blue-600"
        : "bg-stone-100 text-stone-500"
    )}
  >
    {source === "VECTOR" ? "Vector" : "Full-text"}
  </span>
)

export const SearchResultItem = ({ result }: { result: SearchResult }) => (
  <div className="rounded-lg border border-stone-200 bg-white p-4 space-y-3 hover:border-stone-300 transition-colors">
    <div className="flex items-start justify-between gap-3">
      <div className="flex items-center gap-1.5 min-w-0">
        <FileText className="h-3.5 w-3.5 text-stone-400 shrink-0" />
        <span className="text-xs font-medium text-stone-700 truncate">{result.documentName}</span>
        <span className="text-xs text-stone-400 shrink-0">#{result.chunkIndex + 1}</span>
      </div>
      <div className="flex items-center gap-1.5 shrink-0">
        <SourceBadge source={result.source} />
        <ScoreBadge score={result.score} />
      </div>
    </div>
    <p className="text-sm text-stone-600 leading-relaxed line-clamp-4">{result.content}</p>
  </div>
)

const SearchResultSkeleton = () => (
  <div className="rounded-lg border border-stone-200 bg-white p-4 space-y-3 animate-pulse">
    <div className="flex items-center justify-between gap-3">
      <div className="flex items-center gap-1.5 flex-1">
        <div className="h-3.5 w-3.5 rounded bg-stone-200" />
        <div className="h-3 w-32 rounded bg-stone-200" />
      </div>
      <div className="flex gap-1.5">
        <div className="h-4 w-14 rounded bg-stone-200" />
        <div className="h-4 w-8 rounded-full bg-stone-200" />
      </div>
    </div>
    <div className="space-y-1.5">
      <div className="h-3 w-full rounded bg-stone-100" />
      <div className="h-3 w-full rounded bg-stone-100" />
      <div className="h-3 w-3/4 rounded bg-stone-100" />
    </div>
  </div>
)

export const EmptySearchState = () => (
  <div className="flex flex-col items-center justify-center py-10 gap-2">
    <Search className="h-8 w-8 text-stone-300" />
    <p className="text-sm text-stone-400">No results found for this query.</p>
    <p className="text-xs text-stone-300">Try different keywords or a broader question.</p>
  </div>
)

interface SearchSectionProps {
  readyCount: number
}

export function SearchSection({ readyCount }: SearchSectionProps) {
  const [query, setQuery] = useState("")
  const { mutate: search, data: results, isPending, isSuccess, isError } = useSearch()

  if (readyCount === 0) return null

  const handleSearch = () => {
    if (isPending) return
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

      {isError && (
        <p className="text-sm text-red-500">Search failed. Please try again.</p>
      )}

      {isPending && (
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <SearchResultSkeleton key={i} />
          ))}
        </div>
      )}

      {isSuccess && !isPending && (
        <div className="space-y-2">
          {results && results.length > 0 ? (
            <>
              <p className="text-xs text-stone-400 font-medium">
                {results.length} result{results.length !== 1 ? "s" : ""}
              </p>
              {results.map((r) => <SearchResultItem key={r.chunkId} result={r} />)}
            </>
          ) : (
            <EmptySearchState />
          )}
        </div>
      )}
    </div>
  )
}
