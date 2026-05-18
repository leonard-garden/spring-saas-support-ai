import { describe, it, expect } from "vitest"
import { validateFile, MAX_FILE_SIZE, ALLOWED_EXTENSIONS } from "./documentApi"

const makeFile = (name: string, sizeBytes: number, type = "application/pdf") =>
  new File([new ArrayBuffer(sizeBytes)], name, { type })

describe("validateFile", () => {
  it("accepts a valid PDF file under 10 MB", () => {
    const file = makeFile("doc.pdf", 1024 * 1024)
    expect(validateFile(file)).toEqual({ ok: true })
  })

  it("accepts a valid TXT file", () => {
    const file = makeFile("notes.txt", 500, "text/plain")
    expect(validateFile(file)).toEqual({ ok: true })
  })

  it("accepts a valid MD file", () => {
    const file = makeFile("readme.md", 2048, "text/markdown")
    expect(validateFile(file)).toEqual({ ok: true })
  })

  it("accepts a file exactly at the 10 MB boundary", () => {
    const file = makeFile("boundary.pdf", MAX_FILE_SIZE)
    expect(validateFile(file)).toEqual({ ok: true })
  })

  it("rejects a file over 10 MB and mentions size", () => {
    const file = makeFile("large.pdf", MAX_FILE_SIZE + 1)
    const result = validateFile(file)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error.toLowerCase()).toContain("size")
    }
  })

  it("rejects a wrong extension and mentions type", () => {
    const file = makeFile("virus.exe", 1024, "application/octet-stream")
    const result = validateFile(file)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error.toLowerCase()).toMatch(/type|extension|allowed/)
    }
  })

  it("accepts a PDF with uppercase extension (.PDF)", () => {
    const file = makeFile("REPORT.PDF", 1024 * 100)
    expect(validateFile(file)).toEqual({ ok: true })
  })

  it("handles a file with a valid extension but empty content", () => {
    const file = makeFile("empty.txt", 0, "text/plain")
    const result = validateFile(file)
    expect(result.ok).toBe(true)
  })
})

describe("constants", () => {
  it("MAX_FILE_SIZE is 10 MB", () => {
    expect(MAX_FILE_SIZE).toBe(10 * 1024 * 1024)
  })

  it("ALLOWED_EXTENSIONS includes pdf, txt, md", () => {
    expect(ALLOWED_EXTENSIONS).toContain(".pdf")
    expect(ALLOWED_EXTENSIONS).toContain(".txt")
    expect(ALLOWED_EXTENSIONS).toContain(".md")
  })
})
