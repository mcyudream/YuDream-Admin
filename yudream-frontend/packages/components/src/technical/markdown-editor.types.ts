export interface YdMarkdownImage {
  url: string
  alt?: string
  title?: string
}

export type YdMarkdownImageUploadHandler = (
  files: File[],
  callback: (images: YdMarkdownImage[]) => void,
) => void | Promise<void>

export interface YdMarkdownLinkClick {
  href: string
  anchor: HTMLAnchorElement
  event: MouseEvent
}

export type YdMarkdownToolbar = 'bold' | 'underline' | 'italic' | 'strikeThrough' | 'title' | 'sub' | 'sup' | 'quote' | 'unorderedList' | 'orderedList' | 'task' | 'codeRow' | 'code' | 'link' | 'image' | 'table' | 'mermaid' | 'katex' | 'revoke' | 'next' | 'save' | 'prettier' | 'pageFullscreen' | 'fullscreen' | 'preview' | 'previewOnly' | 'htmlPreview' | 'catalog' | 'github' | '-' | '='
export interface YdMarkdownHeading {
  text: string
  level: 1 | 2 | 3 | 4 | 5 | 6
  line: number
  active?: boolean
}
export type YdMarkdownInsert = (selectedText: string) => {
  targetValue: string
  select?: boolean
  deviationStart?: number
  deviationEnd?: number
}

export interface YdMarkdownEditorInstance {
  focus: () => void
  blur: () => void
  insert: (generate: YdMarkdownInsert) => void
  element: Readonly<import('vue').Ref<HTMLElement | null>>
}

export function resolveYdMarkdownEditorTheme(isDark: boolean): 'dark' | 'light' {
  return isDark ? 'dark' : 'light'
}
