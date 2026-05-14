import path from "path"
import { defineConfig } from "vitest/config"
import react from "@vitejs/plugin-react"

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  define: {
    "import.meta.env.VITE_API_URL": JSON.stringify("http://localhost:8080"),
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    // Node 25 enables experimental Web Storage (localStorage) by default.
    // This conflicts with jsdom's localStorage implementation, breaking tests.
    // Disable it so jsdom's localStorage is the only one in scope.
    environmentOptions: {
      jsdom: {},
    },
    pool: "forks",
    poolOptions: {
      forks: {
        execArgv: ["--no-experimental-webstorage"],
      },
    },
  },
})
