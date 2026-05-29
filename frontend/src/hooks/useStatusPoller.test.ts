import { renderHook, act, waitFor } from "@testing-library/react"
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { createElement } from "react"
import { useStatusPoller } from "./useStatusPoller"
import * as documentApi from "@/lib/documentApi"
import type { DocumentResponse } from "@/types/document"

vi.mock("@/lib/documentApi")

const mockDoc = (status: DocumentResponse["status"], errorMessage: string | null = null): DocumentResponse => ({
  id: "doc-1",
  filename: "test.pdf",
  contentType: "application/pdf",
  sizeBytes: 1024,
  chunkCount: null,
  status,
  errorMessage,
  createdAt: "2024-01-01T00:00:00Z",
})

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client }, children)
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe("useStatusPoller", () => {
  it("returns null and does not fetch when status is READY", () => {
    const getDocument = vi.mocked(documentApi.getDocument)
    const { result } = renderHook(() => useStatusPoller("doc-1", "READY"), {
      wrapper: makeWrapper(),
    })
    expect(result.current).toBeNull()
    expect(getDocument).not.toHaveBeenCalled()
  })

  it("returns null and does not fetch when status is FAILED", () => {
    const getDocument = vi.mocked(documentApi.getDocument)
    const { result } = renderHook(() => useStatusPoller("doc-1", "FAILED"), {
      wrapper: makeWrapper(),
    })
    expect(result.current).toBeNull()
    expect(getDocument).not.toHaveBeenCalled()
  })

  it("fetches immediately when status is PROCESSING", async () => {
    const getDocument = vi.mocked(documentApi.getDocument)
    getDocument.mockResolvedValue(mockDoc("PROCESSING"))

    const { result } = renderHook(() => useStatusPoller("doc-1", "PROCESSING"), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => expect(result.current).not.toBeNull())
    expect(getDocument).toHaveBeenCalledWith("doc-1")
  })

  it("fetches immediately when status is PENDING", async () => {
    const getDocument = vi.mocked(documentApi.getDocument)
    getDocument.mockResolvedValue(mockDoc("PENDING"))

    const { result } = renderHook(() => useStatusPoller("doc-1", "PENDING"), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => expect(result.current).not.toBeNull())
    expect(getDocument).toHaveBeenCalledWith("doc-1")
  })

  it("polls again after 3s while still PROCESSING", async () => {
    // shouldAdvanceTime keeps wall-clock running so waitFor works;
    // vi.advanceTimersByTime fires React Query's refetch interval manually.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const getDocument = vi.mocked(documentApi.getDocument)
    getDocument.mockResolvedValue(mockDoc("PROCESSING"))

    renderHook(() => useStatusPoller("doc-1", "PROCESSING"), { wrapper: makeWrapper() })

    await waitFor(() => expect(getDocument).toHaveBeenCalledTimes(1))

    await act(async () => {
      vi.advanceTimersByTime(3000)
    })

    await waitFor(() => expect(getDocument).toHaveBeenCalledTimes(2))

    vi.useRealTimers()
  })

  it("invalidates the documents list query when PROCESSING transitions to READY", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const getDocument = vi.mocked(documentApi.getDocument)
    getDocument
      .mockResolvedValueOnce(mockDoc("PROCESSING"))
      .mockResolvedValueOnce(mockDoc("READY"))

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(client, "invalidateQueries")
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client }, children)

    renderHook(() => useStatusPoller("doc-1", "PROCESSING"), { wrapper })

    await waitFor(() => expect(getDocument).toHaveBeenCalledTimes(1))

    await act(async () => {
      vi.advanceTimersByTime(3000)
    })

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith(
        expect.objectContaining({ queryKey: ["documents"] }),
      ),
    )

    vi.useRealTimers()
  })

  it("invalidates the documents list query when PENDING transitions to READY", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const getDocument = vi.mocked(documentApi.getDocument)
    getDocument
      .mockResolvedValueOnce(mockDoc("PENDING"))
      .mockResolvedValueOnce(mockDoc("READY"))

    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const invalidate = vi.spyOn(client, "invalidateQueries")
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client }, children)

    renderHook(() => useStatusPoller("doc-1", "PENDING"), { wrapper })

    await waitFor(() => expect(getDocument).toHaveBeenCalledTimes(1))

    await act(async () => {
      vi.advanceTimersByTime(3000)
    })

    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith(
        expect.objectContaining({ queryKey: ["documents"] }),
      ),
    )

    vi.useRealTimers()
  })
})
