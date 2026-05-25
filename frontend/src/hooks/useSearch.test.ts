import { renderHook, act, waitFor } from "@testing-library/react"
import { describe, it, expect, vi, beforeEach } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { createElement } from "react"
import { useSearch } from "./useSearch"
import * as searchApi from "@/lib/searchApi"
import type { SearchResult } from "@/types/search"

vi.mock("@/lib/searchApi")

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client }, children)
}

const makeResult = (overrides: Partial<SearchResult> = {}): SearchResult => ({
  chunkId: "chunk-1",
  content: "Test content",
  score: 0.85,
  documentId: "doc-1",
  documentName: "test.pdf",
  chunkIndex: 0,
  source: "VECTOR",
  ...overrides,
})

beforeEach(() => {
  vi.resetAllMocks()
})

describe("useSearch", () => {
  it("starts in idle state with no data", () => {
    const { result } = renderHook(() => useSearch(), { wrapper: makeWrapper() })

    expect(result.current.data).toBeUndefined()
    expect(result.current.isPending).toBe(false)
    expect(result.current.isSuccess).toBe(false)
    expect(result.current.isError).toBe(false)
  })

  it("resolves with SearchResult[] on successful mutation", async () => {
    const results = [makeResult(), makeResult({ chunkId: "chunk-2", score: 0.6 })]
    vi.mocked(searchApi.searchKb).mockResolvedValue(results)

    const { result } = renderHook(() => useSearch(), { wrapper: makeWrapper() })

    act(() => { result.current.mutate("test query") })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toEqual(results)
    expect(searchApi.searchKb).toHaveBeenCalledWith({ query: "test query", topK: 5 })
  })

  it("exposes isError when the API call fails", async () => {
    vi.mocked(searchApi.searchKb).mockRejectedValue(new Error("Search failed"))

    const { result } = renderHook(() => useSearch(), { wrapper: makeWrapper() })

    act(() => { result.current.mutate("failing query") })

    await waitFor(() => expect(result.current.isError).toBe(true))

    expect(result.current.data).toBeUndefined()
    expect(result.current.error?.message).toBe("Search failed")
  })

  it("returns empty array when search yields no results", async () => {
    vi.mocked(searchApi.searchKb).mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper: makeWrapper() })

    act(() => { result.current.mutate("no match") })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data).toEqual([])
  })
})
