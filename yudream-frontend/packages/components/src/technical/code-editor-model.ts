import type { YdCodeEditorLanguage } from './code-editor.types'

export const YD_MONACO_THEME_DARK = 'yd-dark'
export const YD_MONACO_THEME_LIGHT = 'yd-light'

export interface YdMonacoThemePalette {
  background: string
  foreground: string
  gutterBackground: string
  lineNumber: string
  lineHighlight: string
  cursor: string
  selection: string
  inactiveSelection: string
  selectionHighlight: string
  findMatch: string
  whitespace: string
}

const YD_HEX_COLOR_RE = /^#(?:[0-9a-f]{6}|[0-9a-f]{8})$/i

export function resolveYdEditorFieldAttrs(input: { id?: string, invalid?: boolean | string, describedby?: string, error?: string }) {
  const errorId = input.id && input.error ? `${input.id}-error` : undefined
  return {
    id: input.id,
    invalid: input.invalid === true || input.invalid === 'true' || Boolean(input.error),
    describedby: [input.describedby, errorId].filter(Boolean).join(' ') || undefined,
    errorId,
  }
}

export function formatCodeEditorJson(value: string): string | undefined {
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  }
  catch {
    return undefined
  }
}

export function canFormatCodeEditor(language: string, readonly: boolean, disabled: boolean): boolean {
  return language === 'json' && !readonly && !disabled
}

export function resolveCodeEditorInteractivity(readonly: boolean, disabled: boolean) {
  return {
    editable: !readonly && !disabled,
    ariaReadonly: String(readonly),
    ariaDisabled: String(disabled),
  }
}

export function resolveYdMonacoLanguageId(language: YdCodeEditorLanguage): string {
  return language === 'text' ? 'plaintext' : language
}

export function resolveYdMonacoThemeName(isDark: boolean): 'yd-dark' | 'yd-light' {
  return isDark ? YD_MONACO_THEME_DARK : YD_MONACO_THEME_LIGHT
}

// Monaco 只接受实色字符串，透明度通过 8 位十六进制表达。
export function withYdColorAlpha(hex: string, alpha: number): string {
  if (!YD_HEX_COLOR_RE.test(hex)) {
    return hex
  }
  const channel = Math.round(Math.min(Math.max(alpha, 0), 1) * 255)
  return `${hex.toLowerCase().slice(0, 7)}${channel.toString(16).padStart(2, '0')}`
}

export function buildYdMonacoThemeColors(palette: YdMonacoThemePalette) {
  return {
    'editor.background': palette.background,
    'editor.foreground': palette.foreground,
    'editorGutter.background': palette.gutterBackground,
    'editorLineNumber.foreground': palette.lineNumber,
    'editor.lineHighlightBackground': palette.lineHighlight,
    'editorCursor.foreground': palette.cursor,
    // 不设置 editor.selectionForeground：选区保留 token 前景色，选中文字不再被刷成浅色。
    'editor.selectionBackground': palette.selection,
    'editor.inactiveSelectionBackground': palette.inactiveSelection,
    'editor.selectionHighlightBackground': palette.selectionHighlight,
    'editor.findMatchBackground': palette.findMatch,
    'editorWhitespace.foreground': palette.whitespace,
  }
}
