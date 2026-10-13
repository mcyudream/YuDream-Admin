export type YdCodeEditorLanguage = 'css' | 'html' | 'javascript' | 'json' | 'text'

export interface YdCodeEditorCompletion {
  label: string
  type?: 'class' | 'constant' | 'enum' | 'function' | 'interface' | 'keyword' | 'namespace' | 'operator' | 'property' | 'text' | 'type' | 'variable'
  apply?: string
  detail?: string
  info?: string
}

export interface YdCodeEditorInstance {
  focus: () => void
  blur: () => void
  format: () => boolean
  resetToDefault: () => void
  element: Readonly<import('vue').Ref<HTMLElement | null>>
}
