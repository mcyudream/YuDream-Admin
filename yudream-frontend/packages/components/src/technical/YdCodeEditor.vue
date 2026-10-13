<script setup lang="ts">
import type { Completion, CompletionContext, CompletionResult } from '@codemirror/autocomplete'
import type { YdCodeEditorCompletion, YdCodeEditorLanguage } from './code-editor.types'
import { autocompletion } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { css } from '@codemirror/lang-css'
import { html } from '@codemirror/lang-html'
import { javascript } from '@codemirror/lang-javascript'
import { json } from '@codemirror/lang-json'
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { Compartment, EditorState } from '@codemirror/state'
import { placeholder as codeMirrorPlaceholder, drawSelection, dropCursor, EditorView, highlightActiveLine, highlightActiveLineGutter, keymap, lineNumbers } from '@codemirror/view'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { applyExternalCodeEditorValue, canFormatCodeEditor, destroyCodeEditorView, formatCodeEditorJson, resolveCodeEditorInteractivity, resolveYdEditorFieldAttrs, ydCodeEditorThemeStyles } from './code-editor-model'

defineOptions({ name: 'YdCodeEditor' })

const props = withDefaults(defineProps<{
  modelValue?: string
  defaultValue?: string
  language?: YdCodeEditorLanguage
  completions?: YdCodeEditorCompletion[]
  placeholder?: string
  height?: string
  readonly?: boolean
  disabled?: boolean
  lineNumbers?: boolean
  error?: string
  id?: string
  ariaLabel?: string
  ariaDescribedby?: string
  ariaInvalid?: boolean | string
}>(), {
  modelValue: '',
  defaultValue: '',
  language: 'text',
  completions: () => [],
  height: '320px',
  readonly: false,
  disabled: false,
  lineNumbers: true,
  error: '',
  ariaLabel: '代码编辑器',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'focus': []
  'blur': []
  'formatError': []
}>()

const root = ref<HTMLElement | null>(null)
const editorInteractivity = computed(() => resolveCodeEditorInteractivity(props.readonly, props.disabled))
const isReadOnly = computed(() => !editorInteractivity.value.editable)
const fieldAttrs = computed(() => resolveYdEditorFieldAttrs({ id: props.id, invalid: props.ariaInvalid, describedby: props.ariaDescribedby, error: props.error }))
const errorId = computed(() => fieldAttrs.value.errorId)
const languageConfig = new Compartment()
const editableConfig = new Compartment()
const linesConfig = new Compartment()
const completionsConfig = new Compartment()
const placeholderConfig = new Compartment()
const editorTheme = EditorView.theme({
  ...ydCodeEditorThemeStyles,
  '&': {
    height: '100%',
    minHeight: '0',
  },
  '&.cm-focused': {
    outline: '2px solid rgb(var(--primary-6))',
    outlineOffset: '-2px',
  },
  '.cm-scroller': {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    fontSize: '12px',
    lineHeight: '1.65',
  },
  '.cm-gutters': { border: '0', ...ydCodeEditorThemeStyles['.cm-gutters'] },
})

let view: EditorView | null = null
let editorObserver: MutationObserver | undefined
let applyingExternalValue = false

function getLanguageExtension(language: YdCodeEditorLanguage) {
  switch (language) {
    case 'css': return css()
    case 'html': return html({ autoCloseTags: true, matchClosingTags: true })
    case 'javascript': return javascript()
    case 'json': return json()
    default: return []
  }
}

function completionSource(context: CompletionContext): CompletionResult | null {
  const before = context.matchBefore(/[\w${}<./:"'=-]+$/)
  if (!before && !context.explicit) {
    return null
  }
  return {
    from: before?.from ?? context.pos,
    options: props.completions.map(item => ({
      label: item.label,
      type: (item.type || 'keyword') as Completion['type'],
      apply: item.apply || item.label,
      detail: item.detail,
      info: item.info,
    })),
    validFor: /^[\w${}<./:"'=-]*$/,
  }
}

function editableExtensions() {
  return [EditorState.readOnly.of(isReadOnly.value), EditorView.editable.of(!isReadOnly.value)]
}

function lineNumberExtension() {
  return props.lineNumbers ? lineNumbers() : []
}

function completionExtensions() {
  return props.completions.length
    ? autocompletion({ activateOnTyping: true, defaultKeymap: true, override: [completionSource] })
    : []
}

function placeholderExtension() {
  return props.placeholder ? codeMirrorPlaceholder(props.placeholder) : []
}

function updateAccessibility() {
  const content = root.value?.querySelector<HTMLElement>('.cm-content')
  if (!content) {
    return
  }
  content.setAttribute('aria-label', props.ariaLabel)
  content.setAttribute('aria-invalid', String(fieldAttrs.value.invalid))
  content.setAttribute('aria-readonly', editorInteractivity.value.ariaReadonly)
  content.setAttribute('aria-disabled', editorInteractivity.value.ariaDisabled)
  content.setAttribute('tabindex', props.disabled ? '-1' : '0')
  content.setAttribute('aria-multiline', 'true')
  if (fieldAttrs.value.id) {
    content.setAttribute('id', fieldAttrs.value.id)
  }
  else {
    content.removeAttribute('id')
  }
  if (fieldAttrs.value.describedby) {
    content.setAttribute('aria-describedby', fieldAttrs.value.describedby)
  }
  else {
    content.removeAttribute('aria-describedby')
  }
}

function mountEditor() {
  if (!root.value) {
    return
  }
  view = destroyCodeEditorView(view)
  view = new EditorView({
    parent: root.value,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        highlightActiveLineGutter(),
        history(),
        drawSelection(),
        dropCursor(),
        syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
        highlightActiveLine(),
        keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap]),
        EditorView.lineWrapping,
        languageConfig.of(getLanguageExtension(props.language)),
        editableConfig.of(editableExtensions()),
        linesConfig.of(lineNumberExtension()),
        completionsConfig.of(completionExtensions()),
        placeholderConfig.of(placeholderExtension()),
        editorTheme,
        EditorView.contentAttributes.of({
          'aria-label': props.ariaLabel,
          'aria-multiline': 'true',
          'aria-readonly': editorInteractivity.value.ariaReadonly,
          'aria-disabled': editorInteractivity.value.ariaDisabled,
        }),
        EditorView.domEventHandlers({
          focus: () => {
            emit('focus')
            return false
          },
          blur: () => {
            emit('blur')
            return false
          },
        }),
        EditorView.updateListener.of((update) => {
          if (update.docChanged && !applyingExternalValue) {
            emit('update:modelValue', update.state.doc.toString())
          }
        }),
      ],
    }),
  })
  updateAccessibility()
}

function focus() {
  if (props.disabled) {
    return
  }
  view?.focus()
}

function blur() {
  const active = document.activeElement
  if (active && root.value?.contains(active)) {
    ;(active as HTMLElement).blur()
  }
}

function format(): boolean {
  if (!canFormatCodeEditor(props.language, props.readonly, props.disabled) || !view) {
    return false
  }
  const formatted = formatCodeEditorJson(view.state.doc.toString())
  if (formatted === undefined) {
    emit('formatError')
    return false
  }
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: formatted } })
  return true
}

function resetToDefault() {
  if (isReadOnly.value) {
    return
  }
  emit('update:modelValue', props.defaultValue)
}

onMounted(() => {
  mountEditor()
  if (root.value) {
    editorObserver = new MutationObserver(updateAccessibility)
    editorObserver.observe(root.value, { childList: true, subtree: true })
  }
})

watch(() => props.modelValue, (value) => {
  if (!view) {
    return
  }
  applyingExternalValue = true
  try {
    applyExternalCodeEditorValue(view, value)
  }
  finally {
    applyingExternalValue = false
  }
})

watch(() => props.language, value => view?.dispatch({ effects: languageConfig.reconfigure(getLanguageExtension(value)) }))
watch([() => props.readonly, () => props.disabled], () => {
  view?.dispatch({ effects: editableConfig.reconfigure(editableExtensions()) })
  updateAccessibility()
})
watch(() => props.lineNumbers, () => view?.dispatch({ effects: linesConfig.reconfigure(lineNumberExtension()) }))
watch(() => props.completions, () => view?.dispatch({ effects: completionsConfig.reconfigure(completionExtensions()) }), { deep: true })
watch(() => props.placeholder, () => view?.dispatch({ effects: placeholderConfig.reconfigure(placeholderExtension()) }))
watch([() => props.id, () => props.ariaLabel, () => props.error, () => props.ariaInvalid, () => props.ariaDescribedby], updateAccessibility)

onBeforeUnmount(() => {
  editorObserver?.disconnect()
  view = destroyCodeEditorView(view)
})

defineExpose({ focus, blur, format, resetToDefault, element: root })
</script>

<template>
  <div class="yd-code-editor-shell">
    <div
      ref="root"
      class="yd-code-editor"
      :class="{ 'yd-code-editor--disabled': disabled, 'yd-code-editor--invalid': Boolean(error) }"
      :style="{ height }"
    />
    <p v-if="error" :id="errorId" class="yd-code-editor__error" role="alert">
      {{ error }}
    </p>
  </div>
</template>

<style>
.yd-code-editor-shell { min-width: 0; }
.yd-code-editor { min-width: 0; overflow: auto; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.yd-code-editor:focus-within { border-color: rgb(var(--primary-6)); }
.yd-code-editor--disabled { opacity: 0.68; }
.yd-code-editor--invalid { border-color: rgb(var(--danger-6)); }
.yd-code-editor__error { margin: 4px 0 0; color: rgb(var(--danger-6)); font-size: 12px; }
</style>
