import type { languages } from 'monaco-editor/editor.js'
import type { YdMonacoThemePalette } from './code-editor-model'
import type { YdCodeEditorCompletion } from './code-editor.types'
import * as monaco from 'monaco-editor/editor.js'
import { buildYdMonacoThemeColors, resolveYdMonacoThemeName, withYdColorAlpha } from './code-editor-model'
// 显式带 .js：monaco 0.57 的 exports 只暴露 `./*.js` 与 `./*`，`languages/features/**` 这类深层子路径在 Vite 5（VitePress 文档站）里只有 .js 形式能解析。
import 'monaco-editor/features/register.all.js'
import 'monaco-editor/languages/definitions/css/register.js'
import 'monaco-editor/languages/definitions/html/register.js'
import 'monaco-editor/languages/definitions/javascript/register.js'
import 'monaco-editor/languages/features/css/register.js'
import 'monaco-editor/languages/features/html/register.js'
import 'monaco-editor/languages/features/json/register.js'
import 'monaco-editor/languages/features/typescript/register.js'

export { monaco }

export type MonacoApi = typeof monaco
export type MonacoCodeEditor = ReturnType<MonacoApi['editor']['create']>
export interface MonacoDisposable { dispose: () => void }
export type MonacoEditorModule = typeof import('./code-editor-monaco')

interface YdMonacoFallbackColors {
  background: string
  foreground: string
  gutterBackground: string
  lineNumber: string
  lineHighlight: string
  primary: string
}

const YD_CANVAS_COLOR_SENTINEL = '#010203'
const YD_SHORT_HEX_COLOR_RE = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/
const YD_LONG_HEX_COLOR_RE = /^#[0-9a-f]{6}(?:[0-9a-f]{2})?$/
const YD_RGB_COLOR_RE = /^rgba?\(([^)]+)\)$/

// 宿主 CSS 变量不可解析时的兜底实色，取自 Arco 明暗默认 token。
const YD_MONACO_FALLBACK_COLORS: Record<'light' | 'dark', YdMonacoFallbackColors> = {
  light: { background: '#ffffff', foreground: '#1d2129', gutterBackground: '#f7f8fa', lineNumber: '#86909c', lineHighlight: '#f2f3f5', primary: '#165dff' },
  dark: { background: '#17171a', foreground: '#ffffffe6', gutterBackground: '#ffffff14', lineNumber: '#ffffff80', lineHighlight: '#ffffff1f', primary: '#3c7eff' },
}

function toYdColorChannelHex(value: number): string {
  return Math.min(Math.max(Math.round(value), 0), 255).toString(16).padStart(2, '0')
}

function toYdHexColorFromChannels(channels: string): string | undefined {
  const parts = channels.split(/[\s,/]+/).filter(Boolean)
  if (parts.length < 3) {
    return undefined
  }
  const [red, green, blue] = parts.slice(0, 3).map(part => part.endsWith('%') ? Number.parseFloat(part) * 2.55 : Number.parseFloat(part))
  if (![red, green, blue].every(Number.isFinite)) {
    return undefined
  }
  const alphaPart = parts[3]
  const alpha = alphaPart === undefined ? 1 : (alphaPart.endsWith('%') ? Number.parseFloat(alphaPart) / 100 : Number.parseFloat(alphaPart))
  const hex = `#${toYdColorChannelHex(red)}${toYdColorChannelHex(green)}${toYdColorChannelHex(blue)}`
  return !Number.isFinite(alpha) || alpha >= 1 ? hex : `${hex}${toYdColorChannelHex(alpha * 255)}`
}

function toYdHexColor(value: string): string | undefined {
  const trimmed = value.trim().toLowerCase()
  if (YD_LONG_HEX_COLOR_RE.test(trimmed)) {
    return trimmed
  }
  const short = YD_SHORT_HEX_COLOR_RE.exec(trimmed)
  if (short) {
    return `#${short[1]}${short[1]}${short[2]}${short[2]}${short[3]}${short[3]}`
  }
  const channels = YD_RGB_COLOR_RE.exec(trimmed)
  return channels ? toYdHexColorFromChannels(channels[1]) : undefined
}

function normalizeYdCssColor(value: string): string | undefined {
  const hex = toYdHexColor(value)
  if (hex) {
    return hex
  }
  const context = document.createElement('canvas').getContext('2d')
  if (!context) {
    return undefined
  }
  context.fillStyle = YD_CANVAS_COLOR_SENTINEL
  context.fillStyle = value
  // 非法色值会被忽略，fillStyle 停留在哨兵值上。
  if (context.fillStyle === YD_CANVAS_COLOR_SENTINEL) {
    return undefined
  }
  const serialized = toYdHexColor(String(context.fillStyle))
  if (serialized) {
    return serialized
  }
  // oklch()/color() 等非 sRGB 表示交给画布栅格化成实色。
  context.fillRect(0, 0, 1, 1)
  const [red, green, blue, alpha] = context.getImageData(0, 0, 1, 1).data
  const rgbaHex = `#${toYdColorChannelHex(red)}${toYdColorChannelHex(green)}${toYdColorChannelHex(blue)}`
  return alpha >= 255 ? rgbaHex : `${rgbaHex}${toYdColorChannelHex(alpha)}`
}

function resolveYdCssColor(expression: string, fallback: string): string {
  if (typeof document === 'undefined' || !document.body) {
    return fallback
  }
  const probe = document.createElement('span')
  probe.style.display = 'none'
  probe.style.color = expression
  if (!probe.style.color) {
    return fallback
  }
  document.body.appendChild(probe)
  const computed = getComputedStyle(probe).color
  probe.remove()
  return toYdHexColor(computed) ?? normalizeYdCssColor(computed) ?? fallback
}

export function resolveYdMonacoPalette(isDark: boolean): YdMonacoThemePalette {
  const fallback = YD_MONACO_FALLBACK_COLORS[isDark ? 'dark' : 'light']
  const primary = resolveYdCssColor('rgb(var(--primary-6))', fallback.primary)
  const lineNumber = resolveYdCssColor('var(--color-text-3)', fallback.lineNumber)
  return {
    background: resolveYdCssColor('var(--color-bg-1)', fallback.background),
    foreground: resolveYdCssColor('var(--color-text-1)', fallback.foreground),
    gutterBackground: resolveYdCssColor('var(--color-fill-1)', fallback.gutterBackground),
    lineNumber,
    lineHighlight: resolveYdCssColor('var(--color-fill-2)', fallback.lineHighlight),
    cursor: primary,
    selection: withYdColorAlpha(primary, 0.35),
    inactiveSelection: withYdColorAlpha(primary, 0.2),
    selectionHighlight: withYdColorAlpha(primary, 0.25),
    findMatch: withYdColorAlpha(primary, 0.4),
    whitespace: withYdColorAlpha(lineNumber, 0.5),
  }
}

export function applyYdMonacoTheme(isDark: boolean): string {
  const name = resolveYdMonacoThemeName(isDark)
  monaco.editor.defineTheme(name, {
    base: isDark ? 'vs-dark' : 'vs',
    inherit: true,
    rules: [],
    colors: buildYdMonacoThemeColors(resolveYdMonacoPalette(isDark)),
  })
  monaco.editor.setTheme(name)
  return name
}

export function resolveYdMonacoCompletionKind(kind: YdCodeEditorCompletion['type']): languages.CompletionItemKind {
  const { CompletionItemKind } = monaco.languages
  switch (kind) {
    case 'function': return CompletionItemKind.Function
    case 'variable': return CompletionItemKind.Variable
    case 'class': return CompletionItemKind.Class
    case 'property': return CompletionItemKind.Property
    case 'constant': return CompletionItemKind.Constant
    case 'interface': return CompletionItemKind.Interface
    case 'type': return CompletionItemKind.TypeParameter
    case 'operator': return CompletionItemKind.Operator
    case 'namespace': return CompletionItemKind.Module
    case 'enum': return CompletionItemKind.Enum
    case 'keyword': return CompletionItemKind.Keyword
    default: return CompletionItemKind.Text
  }
}
