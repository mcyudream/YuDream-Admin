export interface CodeEditorDocumentView {
  state: {
    doc: {
      length: number
      toString: () => string
    }
  }
  dispatch: (transaction: { changes: { from: number, to: number, insert: string } }) => void
  destroy: () => void
}

export function resolveYdEditorFieldAttrs(input: { id?: string, invalid?: boolean | string, describedby?: string, error?: string }) {
  const errorId = input.id && input.error ? `${input.id}-error` : undefined
  return {
    id: input.id,
    invalid: input.invalid === true || input.invalid === 'true' || Boolean(input.error),
    describedby: [input.describedby, errorId].filter(Boolean).join(' ') || undefined,
    errorId,
  }
}

export const ydCodeEditorThemeStyles = {
  '&': { color: 'var(--color-text-1)', backgroundColor: 'var(--color-bg-1)' },
  '.cm-gutters': { backgroundColor: 'var(--color-fill-1)', color: 'var(--color-text-3)' },
  '.cm-activeLineGutter, .cm-activeLine': { backgroundColor: 'var(--color-fill-2)' },
  '.cm-cursor': { borderLeftColor: 'rgb(var(--primary-6))' },
} as const

export function applyExternalCodeEditorValue(view: CodeEditorDocumentView, value: string): boolean {
  const next = value || ''
  const current = view.state.doc.toString()
  if (next === current) {
    return false
  }
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: next } })
  return true
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

export function destroyCodeEditorView<T extends { destroy: () => void }>(view: T | null): null {
  view?.destroy()
  return null
}
