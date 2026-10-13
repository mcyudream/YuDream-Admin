export interface YdMarkdownImage {
  url: string
  alt?: string
  title?: string
}

export type YdMarkdownImageUploadHandler = (
  files: File[],
  callback: (images: YdMarkdownImage[]) => void,
) => void | Promise<void>

export interface YdMarkdownEditorInstance {
  focus: () => void
  blur: () => void
  element: Readonly<import('vue').Ref<HTMLElement | null>>
}

export function resolveYdMarkdownEditorTheme(isDark: boolean): 'dark' | 'light' {
  return isDark ? 'dark' : 'light'
}
