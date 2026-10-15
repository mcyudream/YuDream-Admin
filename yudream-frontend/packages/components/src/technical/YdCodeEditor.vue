<script setup lang="ts">
import type { MonacoCodeEditor, MonacoDisposable, MonacoEditorModule } from './code-editor-monaco'
import type { YdCodeEditorCompletion, YdCodeEditorLanguage } from './code-editor.types'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { canFormatCodeEditor, formatCodeEditorJson, resolveCodeEditorInteractivity, resolveYdEditorFieldAttrs, resolveYdMonacoLanguageId, resolveYdMonacoThemeName } from './code-editor-model'

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
const mount = ref<HTMLElement | null>(null)
const isDark = ref(false)
const isFocused = ref(false)
const hasContent = ref(Boolean(props.modelValue))
const placeholderInset = ref('8px')

const editorInteractivity = computed(() => resolveCodeEditorInteractivity(props.readonly, props.disabled))
const isReadOnly = computed(() => !editorInteractivity.value.editable)
const fieldAttrs = computed(() => resolveYdEditorFieldAttrs({ id: props.id, invalid: props.ariaInvalid, describedby: props.ariaDescribedby, error: props.error }))
const errorId = computed(() => fieldAttrs.value.errorId)
const showPlaceholder = computed(() => Boolean(props.placeholder) && !hasContent.value && !isFocused.value)

const YD_MONACO_TRIGGER_CHARACTERS = ['"', ':', '.', '/', '$', '{', '=', '<', ' ']

let monacoModule: MonacoEditorModule | null = null
let editor: MonacoCodeEditor | null = null
let completionDisposable: MonacoDisposable | null = null
let editorObserver: MutationObserver | undefined
let themeObserver: MutationObserver | undefined
let applyingExternalValue = false
let mountToken = 0

function writeFieldAttrs(target: HTMLElement) {
  target.setAttribute('aria-label', props.ariaLabel)
  target.setAttribute('aria-invalid', String(fieldAttrs.value.invalid))
  target.setAttribute('aria-readonly', editorInteractivity.value.ariaReadonly)
  target.setAttribute('aria-disabled', editorInteractivity.value.ariaDisabled)
  target.setAttribute('aria-multiline', 'true')
  if (fieldAttrs.value.describedby) {
    target.setAttribute('aria-describedby', fieldAttrs.value.describedby)
  }
  else {
    target.removeAttribute('aria-describedby')
  }
}

// 无障碍属性要落到 Monaco 真正的可编辑元素，容器保留一份同步状态。
// Monaco 按浏览器能力在 EditContext(div.native-edit-context) 与 textarea(.inputarea) 两套输入实现间切换。
function syncAccessibility() {
  const container = root.value
  if (!container) {
    return
  }
  writeFieldAttrs(container)
  if (props.disabled) {
    container.setAttribute('tabindex', '-1')
  }
  else if (container.getAttribute('tabindex') === '-1') {
    container.removeAttribute('tabindex')
  }
  const input = container.querySelector<HTMLElement>('.native-edit-context, .inputarea')
  if (!input) {
    return
  }
  writeFieldAttrs(input)
  input.setAttribute('tabindex', props.disabled ? '-1' : '0')
  if (fieldAttrs.value.id) {
    input.setAttribute('id', fieldAttrs.value.id)
  }
  else {
    input.removeAttribute('id')
  }
}

function syncPlaceholderInset() {
  const margin = root.value?.querySelector<HTMLElement>('.margin')
  placeholderInset.value = `${(margin?.offsetWidth ?? 0) + 8}px`
}

function syncTheme() {
  isDark.value = document.documentElement.classList.contains('dark')
}

function applyTheme() {
  if (!monacoModule) {
    return
  }
  monacoModule.applyYdMonacoTheme(isDark.value)
}

function disposeCompletionProvider() {
  completionDisposable?.dispose()
  completionDisposable = null
}

function registerCompletionProvider() {
  disposeCompletionProvider()
  const module = monacoModule
  const instance = editor
  if (!module || !instance || !props.completions.length) {
    return
  }
  const { monaco: api, resolveYdMonacoCompletionKind } = module
  completionDisposable = api.languages.registerCompletionItemProvider(resolveYdMonacoLanguageId(props.language), {
    triggerCharacters: YD_MONACO_TRIGGER_CHARACTERS,
    provideCompletionItems(model, position) {
      if (model !== instance.getModel()) {
        return { suggestions: [] }
      }
      const word = model.getWordUntilPosition(position)
      return {
        suggestions: props.completions.map(item => ({
          label: item.label,
          kind: resolveYdMonacoCompletionKind(item.type),
          insertText: item.apply || item.label,
          detail: item.detail,
          documentation: item.info,
          range: {
            startLineNumber: position.lineNumber,
            endLineNumber: position.lineNumber,
            startColumn: word.startColumn,
            endColumn: word.endColumn,
          },
        })),
      }
    },
  })
}

async function mountEditor() {
  const container = mount.value
  if (!container) {
    return
  }
  const token = ++mountToken
  const [monacoEntry, workers] = await Promise.all([
    import('./code-editor-monaco'),
    import('./code-editor-workers'),
  ])
  if (token !== mountToken || mount.value !== container) {
    return
  }
  workers.ensureYdMonacoWorkers()
  monacoModule = monacoEntry
  monacoModule.applyYdMonacoTheme(isDark.value)
  const instance = monacoModule.monaco.editor.create(container, {
    value: props.modelValue,
    language: resolveYdMonacoLanguageId(props.language),
    theme: resolveYdMonacoThemeName(isDark.value),
    readOnly: isReadOnly.value,
    domReadOnly: isReadOnly.value,
    lineNumbers: props.lineNumbers ? 'on' : 'off',
    glyphMargin: false,
    folding: false,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    automaticLayout: true,
    tabSize: 2,
    fontSize: 12,
    lineHeight: 20,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    padding: { top: 8, bottom: 8 },
    ariaLabel: props.ariaLabel,
    renderWhitespace: 'selection',
    fixedOverflowWidgets: true,
    scrollbar: { verticalScrollbarSize: 8, horizontalScrollbarSize: 8 },
    quickSuggestions: props.completions.length > 0,
    suggestOnTriggerCharacters: props.completions.length > 0,
  })
  editor = instance
  // Monaco 默认换行符随操作系统变化（Windows 为 CRLF），统一成 LF 才能与格式化结果和宿主文案跨平台一致。
  instance.getModel()?.setEOL(monacoModule.monaco.editor.EndOfLineSequence.LF)
  instance.onDidChangeModelContent(() => {
    const value = instance.getValue()
    hasContent.value = value.length > 0
    if (!applyingExternalValue) {
      emit('update:modelValue', value)
    }
  })
  instance.onDidFocusEditorText(() => {
    isFocused.value = true
    emit('focus')
  })
  instance.onDidBlurEditorText(() => {
    isFocused.value = false
    emit('blur')
  })
  registerCompletionProvider()
  syncPlaceholderInset()
  syncAccessibility()
  requestAnimationFrame(syncPlaceholderInset)
}

function focus() {
  if (props.disabled) {
    return
  }
  editor?.focus()
}

function blur() {
  const active = document.activeElement
  if (active && root.value?.contains(active)) {
    ;(active as HTMLElement).blur()
  }
}

function format(): boolean {
  const instance = editor
  const model = instance?.getModel()
  if (!instance || !model || !canFormatCodeEditor(props.language, props.readonly, props.disabled)) {
    return false
  }
  const formatted = formatCodeEditorJson(instance.getValue())
  if (formatted === undefined) {
    emit('formatError')
    return false
  }
  instance.executeEdits('yd-format', [{ range: model.getFullModelRange(), text: formatted, forceMoveMarkers: true }])
  return true
}

function resetToDefault() {
  if (isReadOnly.value) {
    return
  }
  emit('update:modelValue', props.defaultValue)
}

onMounted(() => {
  syncTheme()
  themeObserver = new MutationObserver(syncTheme)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  if (root.value) {
    editorObserver = new MutationObserver(syncAccessibility)
    editorObserver.observe(root.value, { childList: true, subtree: true })
  }
  void mountEditor()
})

watch(() => props.modelValue, (value) => {
  const instance = editor
  if (!instance) {
    return
  }
  const next = value || ''
  if (instance.getValue() === next) {
    return
  }
  applyingExternalValue = true
  try {
    instance.setValue(next)
  }
  finally {
    applyingExternalValue = false
  }
  hasContent.value = next.length > 0
})

watch(isDark, applyTheme)

watch(() => props.language, (language) => {
  const api = monacoModule?.monaco
  const model = editor?.getModel()
  if (!api || !model) {
    return
  }
  api.editor.setModelLanguage(model, resolveYdMonacoLanguageId(language))
  registerCompletionProvider()
})

watch([() => props.readonly, () => props.disabled], () => {
  const readOnly = isReadOnly.value
  editor?.updateOptions({ readOnly, domReadOnly: readOnly })
  syncAccessibility()
})

watch(() => props.lineNumbers, (lineNumbers) => {
  editor?.updateOptions({ lineNumbers: lineNumbers ? 'on' : 'off' })
  syncPlaceholderInset()
  requestAnimationFrame(syncPlaceholderInset)
})

watch(() => props.completions, (completions) => {
  editor?.updateOptions({
    quickSuggestions: completions.length > 0,
    suggestOnTriggerCharacters: completions.length > 0,
  })
  registerCompletionProvider()
}, { deep: true })

watch([() => props.id, () => props.ariaLabel, () => props.error, () => props.ariaInvalid, () => props.ariaDescribedby], syncAccessibility)

onBeforeUnmount(() => {
  mountToken += 1
  editorObserver?.disconnect()
  themeObserver?.disconnect()
  disposeCompletionProvider()
  editor?.dispose()
  editor = null
  monacoModule = null
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
    >
      <div ref="mount" class="yd-code-editor__input" />
      <span v-if="showPlaceholder" class="yd-code-editor__placeholder" :style="{ paddingLeft: placeholderInset }">{{ placeholder }}</span>
    </div>
    <p v-if="error" :id="errorId" class="yd-code-editor__error" role="alert">
      {{ error }}
    </p>
  </div>
</template>

<style>
/* 表单内容区是 flex 行，组件必须显式占满整行，否则 Monaco 会按最小内容宽度塌缩。 */
.yd-code-editor-shell { width: 100%; min-width: 0; }
.yd-code-editor { position: relative; width: 100%; min-width: 0; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.yd-code-editor__input { height: 100%; min-height: 0; }
.yd-code-editor .monaco-editor { border-radius: inherit; }
.yd-code-editor:focus-within { border-color: rgb(var(--primary-6)); }
.yd-code-editor--disabled { opacity: 0.68; }
.yd-code-editor--invalid { border-color: rgb(var(--danger-6)); }
.yd-code-editor__placeholder {
  position: absolute;
  top: 0;
  left: 0;
  max-width: 100%;
  padding-top: 8px;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: pre;
  pointer-events: none;
}
.yd-code-editor__error { margin: 4px 0 0; color: rgb(var(--danger-6)); font-size: 12px; }
</style>
