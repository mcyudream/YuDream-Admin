<script setup lang="ts">
import { closeBrackets } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { json } from '@codemirror/lang-json'
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { EditorState } from '@codemirror/state'
import { drawSelection, dropCursor, EditorView, highlightActiveLine, highlightActiveLineGutter, keymap, lineNumbers } from '@codemirror/view'
import { formatJson, isJsonObject } from '../config/agent-json'

const props = defineProps<{
  modelValue: string
  defaultValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const el = ref<HTMLElement>()
let view: EditorView | null = null
let applyingExternalValue = false

const valid = computed(() => isJsonObject(props.modelValue))

const editorTheme = EditorView.theme({
  '&': {
    minHeight: '160px',
    border: '1px solid var(--color-border-2)',
    borderRadius: '6px',
    overflow: 'hidden',
    background: 'var(--color-bg-1)',
  },
  '&.cm-focused': {
    outline: 'none',
    borderColor: 'rgb(var(--primary-6))',
  },
  '.cm-scroller': {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    fontSize: '12px',
    lineHeight: '1.6',
  },
  '.cm-gutters': {
    border: '0',
    background: 'var(--color-fill-1)',
    color: 'var(--color-text-3)',
  },
  '.cm-activeLineGutter, .cm-activeLine': {
    background: 'var(--color-fill-2)',
  },
})

function extensions() {
  return [
    lineNumbers(),
    highlightActiveLineGutter(),
    history(),
    drawSelection(),
    dropCursor(),
    closeBrackets(),
    json(),
    syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
    highlightActiveLine(),
    keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap]),
    editorTheme,
    EditorView.updateListener.of((update) => {
      if (update.docChanged && !applyingExternalValue) {
        emit('update:modelValue', update.state.doc.toString())
      }
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
    state: EditorState.create({ doc: props.modelValue || '', extensions: extensions() }),
  })
}

function format() {
  emit('update:modelValue', formatJson(props.modelValue))
}

function restoreDefault() {
  emit('update:modelValue', props.defaultValue)
}

onMounted(mountEditor)

watch(() => props.modelValue, (value) => {
  if (!view || value === view.state.doc.toString()) {
    return
  }
  applyingExternalValue = true
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value || '' } })
  applyingExternalValue = false
})

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>

<template>
  <div class="agent-json-editor">
    <div ref="el" />
    <div class="editor-footer">
      <small :class="valid ? 'valid' : 'invalid'">{{ valid ? 'JSON object 有效' : '必须是有效的 JSON object' }}</small>
      <div class="flex gap-2">
        <FaButton size="sm" variant="ghost" @click="restoreDefault">恢复默认</FaButton>
        <FaButton size="sm" variant="outline" @click="format">格式化 JSON</FaButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-json-editor { display: grid; gap: 6px; }.editor-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.editor-footer small { font-size: 10px; }.valid { color: rgb(var(--success-6)); }.invalid { color: rgb(var(--danger-6)); }
</style>
