import { useState, type FormEvent } from "react"
import axios from "axios"
import { Loader2, CheckCircle2, XCircle, AlertTriangle } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { cn } from "@/lib/utils"
import api from "@/lib/api"

type Status = "idle" | "loading" | "success" | "cors-blocked" | "api-error"

type ResultState = {
  status: Status
  httpStatus?: number
  body?: unknown
  errorMessage?: string
}

const API_URL = import.meta.env.VITE_API_URL

export function CorsTestPage() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [result, setResult] = useState<ResultState>({ status: "idle" })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setResult({ status: "loading" })
    try {
      const response = await api.post("/auth/login", { email, password })
      setResult({ status: "success", httpStatus: response.status, body: response.data })
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        if (err.response) {
          setResult({ status: "api-error", httpStatus: err.response.status, body: err.response.data })
        } else {
          setResult({ status: "cors-blocked", errorMessage: err.message })
        }
      } else {
        setResult({ status: "cors-blocked", errorMessage: err instanceof Error ? err.message : String(err) })
      }
    }
  }

  const isLoading = result.status === "loading"

  return (
    <div className="min-h-screen bg-background flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <Card>
          <CardHeader className="border-b border-zinc-100">
            <CardTitle>CORS Verification</CardTitle>
            <CardDescription>
              Test cross-origin call to <code className="font-mono">POST /auth/login</code>
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 pt-6">
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="email">Email</Label>
                <Input id="email" type="email" placeholder="owner@acme.com"
                  value={email} onChange={(e) => setEmail(e.target.value)}
                  disabled={isLoading} autoComplete="off" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="password">Password</Label>
                <Input id="password" type="password" placeholder="password"
                  value={password} onChange={(e) => setPassword(e.target.value)}
                  disabled={isLoading} autoComplete="off" />
              </div>
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? (
                  <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Sending...</>
                ) : "Send Request"}
              </Button>
            </form>
            <ResultPanel result={result} />
          </CardContent>
        </Card>
        <p className="mt-4 text-center text-xs text-zinc-400">
          Backend: <code className="font-mono">{API_URL}</code>
        </p>
      </div>
    </div>
  )
}

function ResultPanel({ result }: { result: ResultState }) {
  if (result.status === "idle" || result.status === "loading") return null
  if (result.status === "success") {
    return (
      <Alert variant="success" icon={<CheckCircle2 className="h-4 w-4" />}>
        <p className="font-semibold">CORS OK — {result.httpStatus} OK</p>
        <pre className="mt-2 overflow-auto rounded bg-white/60 p-2 text-xs">
          {JSON.stringify(result.body, null, 2)}
        </pre>
      </Alert>
    )
  }
  if (result.status === "api-error") {
    return (
      <Alert variant="warning" icon={<AlertTriangle className="h-4 w-4" />}>
        <p className="font-semibold">CORS OK — but {result.httpStatus} returned</p>
        <p className="text-xs">(This means CORS is working — the server just rejected the credentials.)</p>
        <pre className="mt-2 overflow-auto rounded bg-white/60 p-2 text-xs">
          {JSON.stringify(result.body, null, 2)}
        </pre>
      </Alert>
    )
  }
  return (
    <Alert variant="error" icon={<XCircle className="h-4 w-4" />}>
      <p className="font-semibold">CORS BLOCKED</p>
      <p className="text-xs mt-1">Network Error — request was blocked before reaching the server. Check:</p>
      <ul className="list-disc pl-4 text-xs mt-1 space-y-0.5">
        <li>Backend running on <code className="font-mono">http://localhost:8081</code>?</li>
        <li>CORS origins include <code className="font-mono">http://localhost:5173</code>?</li>
        <li>Spring profile is <code className="font-mono">dev</code>?</li>
      </ul>
      {result.errorMessage && <p className="text-xs mt-2 font-mono">{result.errorMessage}</p>}
    </Alert>
  )
}

type AlertVariant = "success" | "warning" | "error"

function Alert({ variant, icon, children }: {
  variant: AlertVariant
  icon: React.ReactNode
  children: React.ReactNode
}) {
  const variants: Record<AlertVariant, string> = {
    success: "border-l-4 border-green-500 bg-green-50 text-green-800",
    warning: "border-l-4 border-amber-500 bg-amber-50 text-amber-800",
    error: "border-l-4 border-red-500 bg-red-50 text-red-800",
  }
  return (
    <div className={cn("rounded-md p-4 text-sm", variants[variant])}>
      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex-shrink-0">{icon}</span>
        <div className="flex-1 space-y-1">{children}</div>
      </div>
    </div>
  )
}
