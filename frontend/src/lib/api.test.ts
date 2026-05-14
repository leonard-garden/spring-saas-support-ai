import { it, expect, beforeEach } from "vitest"
import axios from "axios"
import MockAdapter from "axios-mock-adapter"
import { api } from "./api"
import {
  clearTokens,
  getAccessToken,
  setAccessToken,
  setRefreshToken,
} from "./tokenStorage"

const REFRESH_URL = "http://localhost:8080/auth/refresh"

const mock = new MockAdapter(api)
const axiosMock = new MockAdapter(axios)

beforeEach(() => {
  clearTokens()
  mock.reset()
  axiosMock.reset()
})

// T1: Request interceptor adds Authorization header when accessToken is non-null
it("T1: adds Authorization header when accessToken is set", async () => {
  setAccessToken("test-token")
  let capturedAuth: string | undefined

  mock.onGet("/ping").reply((config) => {
    capturedAuth = config.headers?.["Authorization"] as string | undefined
    return [200, {}]
  })

  await api.get("/ping")
  expect(capturedAuth).toBe("Bearer test-token")
})

// T2: Request interceptor omits header when accessToken is null
it("T2: omits Authorization header when accessToken is null", async () => {
  let capturedAuth: string | undefined | null

  mock.onGet("/ping").reply((config) => {
    capturedAuth = config.headers?.["Authorization"] as string | undefined
    return [200, {}]
  })

  await api.get("/ping")
  expect(capturedAuth).toBeUndefined()
})

// T3: On 401 — calls /auth/refresh, stores new tokens, retries original request
it("T3: on 401 calls /auth/refresh, stores tokens, and retries", async () => {
  setRefreshToken("old-refresh")
  setAccessToken("old-access")

  mock.onGet("/protected").replyOnce(401).onGet("/protected").reply(200, { ok: true })

  axiosMock.onPost(REFRESH_URL).reply(200, {
    accessToken: "new-access",
    refreshToken: "new-refresh",
  })

  const res = await api.get("/protected")

  expect(res.data).toEqual({ ok: true })
  expect(getAccessToken()).toBe("new-access")
})

// T4: Two concurrent 401s produce only ONE /auth/refresh call (refresh lock)
it("T4: concurrent 401s produce only one /auth/refresh call", async () => {
  setRefreshToken("old-refresh")

  let resolveRefresh!: (v: [number, object]) => void
  let refreshCallCount = 0

  const refreshGate = new Promise<[number, object]>((r) => {
    resolveRefresh = r
  })

  axiosMock.onPost(REFRESH_URL).reply(() => {
    refreshCallCount++
    return refreshGate
  })

  mock
    .onGet("/a")
    .replyOnce(401)
    .onGet("/a")
    .reply(200, { from: "a" })

  mock
    .onGet("/b")
    .replyOnce(401)
    .onGet("/b")
    .reply(200, { from: "b" })

  const p = Promise.all([api.get("/a"), api.get("/b")])

  // allow microtasks to drain so both 401s hit the interceptor
  await new Promise((r) => setTimeout(r, 0))

  resolveRefresh([200, { accessToken: "new-access", refreshToken: "new-refresh" }])

  await p

  expect(refreshCallCount).toBe(1)
})

// T5: On refresh failure — calls clearAuth() and rejects original request
it("T5: on refresh failure calls clearAuth and rejects", async () => {
  setRefreshToken("old-refresh")
  setAccessToken("old-access")

  mock.onGet("/protected").replyOnce(401)
  axiosMock.onPost(REFRESH_URL).reply(401, { error: "invalid_refresh" })

  await expect(api.get("/protected")).rejects.toBeDefined()
  expect(getAccessToken()).toBeNull()
})

// T6: Requests with _retry=true are not retried again
it("T6: requests with _retry=true are not retried", async () => {
  setRefreshToken("old-refresh")

  let refreshCallCount = 0
  axiosMock.onPost(REFRESH_URL).reply(() => {
    refreshCallCount++
    return [200, { accessToken: "new", refreshToken: "newR" }]
  })

  // Simulate a request that already has _retry set — the interceptor should
  // pass-through the 401 without calling refresh again.
  // We do this by issuing a normal 401, letting it retry once, then on the
  // retried request returning another 401. The interceptor must NOT loop.
  mock
    .onGet("/once")
    .replyOnce(401)  // first call → 401 → triggers refresh + retry
    .onGet("/once")
    .replyOnce(401)  // retried call → 401 → _retry=true, must reject immediately

  await expect(api.get("/once")).rejects.toBeDefined()
  // refresh was called exactly once (for the first 401), not again for the second
  expect(refreshCallCount).toBe(1)
})
