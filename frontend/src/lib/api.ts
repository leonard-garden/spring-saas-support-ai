import axios from "axios"
import type { AxiosError, InternalAxiosRequestConfig } from "axios"
import {
  getAccessToken,
  setAccessToken,
  getRefreshToken,
  setRefreshToken,
  clearTokens,
} from "./tokenStorage"

const baseURL = import.meta.env.VITE_API_URL

if (!baseURL) {
  // Fail loud at module load if env var missing — saves debugging time
  // eslint-disable-next-line no-console
  console.error(
    "VITE_API_URL is not defined. Copy frontend/.env.development.example to frontend/.env.development."
  )
}

export const api = axios.create({
  baseURL,
  headers: { "Content-Type": "application/json" },
  // withCredentials intentionally false — backend uses Bearer tokens, not cookies.
  withCredentials: false,
})

interface RefreshResponse {
  accessToken: string
  refreshToken: string
}

let refreshPromise: Promise<string> | null = null

async function doRefresh(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error("No refresh token")
  // Use bare axios (not api) — must NOT go through api's response interceptor,
  // otherwise a refresh-endpoint 401 would recurse back into this function.
  const { data } = await axios.post<RefreshResponse>(`${baseURL}/auth/refresh`, { refreshToken })
  if (!data?.accessToken || !data?.refreshToken) throw new Error("Malformed refresh response")
  setAccessToken(data.accessToken)
  setRefreshToken(data.refreshToken)
  return data.accessToken
}

api.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig | undefined
    if (!original || error.response?.status !== 401 || original._retry) {
      return Promise.reject(error)
    }
    original._retry = true
    try {
      if (!refreshPromise) {
        refreshPromise = doRefresh().finally(() => {
          refreshPromise = null
        })
      }
      const newToken = await refreshPromise
      original.headers.Authorization = `Bearer ${newToken}`
      return api(original)
    } catch (refreshErr) {
      clearTokens()
      return Promise.reject(refreshErr)
    }
  }
)

export default api
