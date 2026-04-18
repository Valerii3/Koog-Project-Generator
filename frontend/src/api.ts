import type { GenerateRequest, OptionsResponse, PreviewResponse } from './types'

export async function fetchOptions(): Promise<OptionsResponse> {
  const res = await fetch('/api/options')
  if (!res.ok) throw new Error('Failed to load options')
  return res.json()
}

export async function generateProject(req: GenerateRequest): Promise<Blob> {
  const res = await fetch('/api/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`Generation failed: ${text}`)
  }
  return res.blob()
}

export async function previewProject(req: GenerateRequest): Promise<PreviewResponse> {
  const res = await fetch('/api/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`Preview failed: ${text}`)
  }
  return res.json()
}

export function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
