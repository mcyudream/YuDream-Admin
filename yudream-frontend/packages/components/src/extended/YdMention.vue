<script setup lang="ts">
import { PopoverAnchor, PopoverContent, PopoverPortal, PopoverRoot } from 'reka-ui'
import { computed, nextTick, ref, useId, watch } from 'vue'
import { YdInput, YdTextarea } from '../adapters/index'
import './extended.css'

export interface YdMentionOption {
  value: string
  label?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<{
  items: YdMentionOption[]
  trigger?: string
  inputAs?: 'input' | 'textarea'
  disabled?: boolean
  readonly?: boolean
  placeholder?: string
  rows?: number
  label?: string
  emptyText?: string
  filter?: (query: string, option: YdMentionOption) => boolean
}>(), { trigger: '@', inputAs: 'textarea', rows: 4, placeholder: '输入 @ 提及', emptyText: '没有匹配对象' })
const emit = defineEmits<{
  change: [text: string]
  search: [query: string]
  select: [option: YdMentionOption]
}>()
const text = defineModel<string>({ default: '' })
const values = defineModel<string[]>('value', { default: () => [] })
const popupId = useId()
const editorId = useId()
const editorRef = ref<HTMLElement>()
const open = ref(false)
const query = ref('')
const active = ref(-1)
const measure = ref<{
  start: number
  end: number
}>()
let composing = false
function mentionLabel(item: YdMentionOption) {
  const label = item.label || item.value
  return props.items.filter(other => (other.label || other.value) === label).length > 1 ? `${label}(${item.value})` : label
}
const candidates = computed(() => props.items.filter(item => props.filter?.(query.value, item) ?? (`${mentionLabel(item)} ${item.value}`).toLocaleLowerCase().includes(query.value.toLocaleLowerCase())))
const enabledIndexes = computed(() => candidates.value.flatMap((item, index) => item.disabled ? [] : [index]))
watch(candidates, () => {
  active.value = enabledIndexes.value[0] ?? -1
})
watch(() => [props.disabled, props.readonly], ([disabled, readonly]) => {
  if (disabled || readonly) {
    open.value = false
  }
})
watch([text, () => props.items, () => props.trigger], ([content]) => {
  const present = props.items.filter((item) => {
    if (item.disabled) {
      return false
    }
    const token = props.trigger + mentionLabel(item)
    let start = content.indexOf(token)
    while (start !== -1) {
      const before = content[start - 1]
      const after = content[start + token.length]
      if ((!before || /[\s([（【，,。.!！?？:：;；]/.test(before)) && (!after || /[\s)\]）】，,。.!！?？:：;；]/.test(after))) {
        return true
      }
      start = content.indexOf(token, start + token.length)
    }
    return false
  }).map(item => item.value)
  values.value = [...new Set(present)]
}, { immediate: true })
function editor() {
  return editorRef.value?.querySelector<HTMLInputElement | HTMLTextAreaElement>('input,textarea')
}
function measureCursor() {
  if (composing || props.disabled || props.readonly || !props.trigger) {
    open.value = false
    return
  }
  const element = editor()
  if (!element || element.selectionStart === null) {
    return
  }
  const cursor = element.selectionStart
  const head = element.value.slice(0, cursor)
  const start = head.lastIndexOf(props.trigger)
  const before = head[start - 1]
  const term = start < 0 ? '' : head.slice(start + props.trigger.length)
  if (start < 0 || (before && !/[\s([（【，,。.!！?？:：;；]/.test(before)) || /\s/.test(term) || element.selectionEnd !== cursor) {
    open.value = false
    return
  }
  measure.value = { start, end: cursor }
  query.value = term
  active.value = enabledIndexes.value[0] ?? -1
  open.value = true
  emit('search', term)
}
function input(event: Event) {
  text.value = (event.target as HTMLInputElement).value
  emit('change', text.value)
  measureCursor()
}
async function select(item: YdMentionOption) {
  if (item.disabled || props.disabled || props.readonly || !measure.value) {
    return
  }
  const { start, end } = measure.value
  const token = props.trigger + mentionLabel(item)
  const tail = text.value.slice(end)
  const split = !tail || !/^\s/.test(tail) ? ' ' : ''
  text.value = text.value.slice(0, start) + token + split + tail
  open.value = false
  measure.value = undefined
  emit('change', text.value)
  emit('select', item)
  await nextTick()
  const element = editor()
  element?.focus()
  element?.setSelectionRange(start + token.length + split.length, start + token.length + split.length)
}
function keydown(event: KeyboardEvent) {
  if (composing || event.isComposing || !open.value) {
    return
  }
  if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
    event.preventDefault()
    const indexes = enabledIndexes.value
    const current = indexes.indexOf(active.value)
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? indexes.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + indexes.length) % indexes.length
    active.value = indexes[next] ?? -1
    void nextTick(() => document.getElementById(`${popupId}-${active.value}`)?.scrollIntoView({ block: 'nearest' }))
  }
  else if (event.key === 'Enter' && active.value >= 0) {
    event.preventDefault()
    void select(candidates.value[active.value])
  }
  else if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    open.value = false
  }
}
function keyup(event: KeyboardEvent) {
  if (!open.value && ['ArrowLeft', 'ArrowRight'].includes(event.key)) {
    measureCursor()
  }
}
function compositionStart() {
  composing = true
}
function compositionEnd() {
  composing = false
  measureCursor()
}
defineExpose({ focus: () => editor()?.focus(), blur: () => editor()?.blur() })
</script>

<template>
  <div ref="editorRef" class="yd-mention">
    <label v-if="label" :for="editorId" class="yd-mention-label">{{ label }}</label>
    <PopoverRoot :open="open && !disabled && !readonly" @update:open="open = $event">
      <PopoverAnchor as-child>
        <div class="yd-mention-editor">
          <component :is="inputAs === 'textarea' ? YdTextarea : YdInput" :id="editorId" v-model="text" :disabled="disabled" :readonly="readonly" :placeholder="placeholder" :rows="inputAs === 'textarea' ? rows : undefined" role="combobox" aria-autocomplete="list" :aria-expanded="open" :aria-controls="open ? popupId : undefined" :aria-activedescendant="open && active >= 0 ? `${popupId}-${active}` : undefined" @input="input" @keydown="keydown" @keyup="keyup" @click="measureCursor" @compositionstart="compositionStart" @compositionend="compositionEnd" />
        </div>
      </PopoverAnchor>
      <PopoverPortal>
        <PopoverContent :id="popupId" role="listbox" align="start" :side-offset="6" class="yd-collection-popup yd-mention-popup" @open-auto-focus="$event.preventDefault()" @close-auto-focus="$event.preventDefault()">
          <div class="yd-collection-viewport">
            <button v-for="(item, index) in candidates" :id="`${popupId}-${index}`" :key="item.value" type="button" role="option" :aria-selected="index === active" :disabled="item.disabled" class="yd-collection-option" :data-highlighted="index === active || undefined" tabindex="-1" @pointerdown.prevent @pointermove="!item.disabled && (active = index)" @click="select(item)">
              <slot name="option" :option="item">
                <span>{{ mentionLabel(item) }}</span><span v-if="item.label" class="yd-mention-description">{{ item.value }}</span>
              </slot>
            </button>
            <div v-if="!candidates.length" class="yd-collection-empty">
              <slot name="empty">
                {{ emptyText }}
              </slot>
            </div>
          </div>
        </PopoverContent>
      </PopoverPortal>
    </PopoverRoot>
  </div>
</template>
