import { describe, it, expect, vi, beforeEach } from "vitest"
import { searchKb } from "./searchApi"
import * as apiModule from "./api"

vi.mock("./api", () => ({
  api: {
    post: vi.fn(),
  },
}))

const mockPost = () => vi.mocked(apiModule.api.post)

beforeEach(() => {
  vi.resetAllMocks()
})

const makeResult = (overrides = {}) => ({
  chunkId: "chunk-1",
  content: "Test content",
  score: 0.85,
  documentId: "doc-1",
  documentName: "test.pdf",
  chunkIndex: 0,
  source: "VECTOR" as const,
  ...overrides,
})

describe("searchKb", () => {
  it("returns SearchResult[] on success", async () => {
    const results = [makeResult(), makeResult({ chunkId: "chunk-2", score: 0.6 })]
    mockPost().mockResolvedValue({ data: { success: true, data: results, error: null } })

    const response = await searchKb({ query: "test query", topK: 5 })

    expect(mockPost()).toHaveBeenCalledWith("/kb/search", { query: "test query", topK: 5 })
    expect(response).toEqual(results)
  })

  it("throws with envelope.error when data is null", async () => {
    mockPost().mockResolvedValue({ data: { success: false, data: null, error: "KB not found" } })

    await expect(searchKb({ query: "test" })).rejects.toThrow("KB not found")
  })

  it("throws 'Empty response' when data is null and error is also null", async () => {
    mockPost().mockResolvedValue({ data: { success: false, data: null, error: null } })

    await expect(searchKb({ query: "test" })).rejects.toThrow("Empty response")
  })

  it("returns empty array when search produces no results", async () => {
    mockPost().mockResolvedValue({ data: { success: true, data: [], error: null } })

    const response = await searchKb({ query: "no match" })

    expect(response).toEqual([])
  })

  it("forwards network errors from the api client", async () => {
    mockPost().mockRejectedValue(new Error("Network Error"))

    await expect(searchKb({ query: "test" })).rejects.toThrow("Network Error")
  })
})
