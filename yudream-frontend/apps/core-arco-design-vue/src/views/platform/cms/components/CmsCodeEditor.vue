<script setup lang="ts">
import type { Completion, CompletionContext, CompletionResult } from '@codemirror/autocomplete'
import { autocompletion, closeBrackets } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { css } from '@codemirror/lang-css'
import { html } from '@codemirror/lang-html'
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { EditorState } from '@codemirror/state'
import { drawSelection, dropCursor, EditorView, highlightActiveLine, highlightActiveLineGutter, keymap, lineNumbers } from '@codemirror/view'

interface CodeCompletionItem {
  label: string
  type?: Completion['type']
  apply?: string
  detail?: string
  info?: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  language?: 'html' | 'css'
  disabled?: boolean
  completions?: CodeCompletionItem[]
}>(), {
  language: 'html',
  completions: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const el = ref<HTMLElement>()
let view: EditorView | null = null
let applyingExternalValue = false

const editorTheme = EditorView.theme({
  '&': {
    height: '100%',
    minHeight: '0',
    borderRadius: '8px',
    overflow: 'hidden',
    border: '1px solid #dbe2ea',
    background: '#0f172a',
    color: '#e5edf7',
  },
  '&.cm-focused': {
    outline: 'none',
    borderColor: '#0f766e',
    boxShadow: '0 0 0 3px rgba(15, 118, 110, 0.12)',
  },
  '.cm-scroller': {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    fontSize: '12px',
    lineHeight: '1.7',
  },
  '.cm-gutters': {
    border: '0',
    background: '#111827',
    color: '#64748b',
  },
  '.cm-activeLineGutter': {
    background: '#1f2937',
    color: '#cbd5e1',
  },
  '.cm-activeLine': {
    background: 'rgba(30, 41, 59, 0.72)',
  },
  '.cm-cursor': {
    borderLeftColor: '#5eead4',
  },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground': {
    background: 'rgba(45, 212, 191, 0.26)',
  },
  '.cm-tooltip': {
    border: '1px solid #cbd5e1',
    borderRadius: '8px',
    overflow: 'hidden',
    boxShadow: '0 12px 28px rgba(15, 23, 42, 0.18)',
  },
  '.cm-tooltip-autocomplete': {
    background: '#ffffff',
    color: '#0f172a',
  },
  '.cm-tooltip-autocomplete ul li[aria-selected]': {
    background: '#ecfdf5',
    color: '#0f766e',
  },
}, { dark: true })

function languageExtension() {
  return props.language === 'css'
    ? css()
    : html({ autoCloseTags: true, matchClosingTags: true })
}

function completionSource(context: CompletionContext): CompletionResult | null {
  const before = context.matchBefore(/[\w{}<./:"'=-]+$/)
  if (!before && !context.explicit) {
    return null
  }
  return {
    from: before?.from ?? context.pos,
    options: (props.completions || []).map(item => ({
      label: item.label,
      type: item.type || 'keyword',
      apply: item.apply || item.label,
      detail: item.detail,
      info: item.info,
    })),
    validFor: /^[\w{}<./:"'=-]*$/,
  }
}

function extensions() {
  return [
    lineNumbers(),
    highlightActiveLineGutter(),
    history(),
    drawSelection(),
    dropCursor(),
    closeBrackets(),
    languageExtension(),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    autocompletion({
      activateOnTyping: true,
      defaultKeymap: true,
      override: [completionSource],
    }),
    EditorView.editable.of(!props.disabled),
    EditorState.readOnly.of(Boolean(props.disabled)),
    highlightActiveLine(),
    keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap]),
    editorTheme,
    EditorView.updateListener.of((update) => {
      if (!update.docChanged || applyingExternalValue) {
        return
      }
      emit('update:modelValue', update.state.doc.toString())
    }),
  ]
}

function mountEditor() {
  if (!el.value) {
    return
  }
  view?.destroy()
  view = new EditorView({
    parent: el.value,
    state: EditorState.create({
      doc: props.modelValue || '',
      extensions: extensions(),
    }),
  })
}

onMounted(mountEditor)

watch(() => props.modelValue, (value) => {
  if (!view || value === view.state.doc.toString()) {
    return
  }
  applyingExternalValue = true
  view.dispatch({
    changes: {
      from: 0,
      to: view.state.doc.length,
      insert: value || '',
    },
  })
  applyingExternalValue = false
})

watch([
  () => props.language,
  () => props.disabled,
  () => props.completions,
], mountEditor, { deep: true })

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>

<template>
  <div ref="el" class="cms-code-editor" />
</template>

<style scoped>
.cms-code-editor {
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>
