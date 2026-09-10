export type CanvasToolAsset = {
  kind: 'css' | 'html' | 'js'
  content: string
}

export function orderedAddHtmlAssets(payload: Record<string, unknown>): CanvasToolAsset[] {
  return [
    { kind: 'html' as const, content: text(payload.htmlContent) },
    { kind: 'css' as const, content: text(payload.cssContent) },
    { kind: 'js' as const, content: text(payload.jsContent) },
  ].filter(item => item.content)
}

function text(value: unknown) {
  return value == null ? '' : String(value).trim()
}
