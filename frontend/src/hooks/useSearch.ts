import { useMutation } from "@tanstack/react-query"
import { searchKb } from "@/lib/searchApi"
import type { SearchResult } from "@/types/search"

export function useSearch() {
  return useMutation<SearchResult[], Error, string>({
    mutationFn: (query: string) => searchKb({ query, topK: 5 }),
  })
}
