import axios from "axios"

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
  // Setting true requires the backend to whitelist exact origin (which it does), but
  // adds preflight complexity that the Bearer flow does not need.
  withCredentials: false,
})

export default api
