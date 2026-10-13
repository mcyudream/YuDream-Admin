<script setup lang="ts">
import { ComboboxAnchor, ComboboxContent, ComboboxEmpty, ComboboxInput, ComboboxItem, ComboboxItemIndicator, ComboboxPortal, ComboboxRoot, ComboboxTrigger, ComboboxViewport } from 'reka-ui'
import { computed, ref, watch } from 'vue'
import YdIcon from '../basic/icon/index.vue'
import { equalYdMultiSelectValues, normalizeYdMultiSelectValue, removeYdMultiSelectValue, resolveYdMultiSelectInputAttrs, resolveYdMultiSelectInputState, sliceYdMultiSelectTags } from './multi-select-model'
import './extended.css'

defineOptions({ name: 'YdMultiSelect', inheritAttrs: false })

export interface YdMultiSelectOption {
  label: string
  value: string
  disabled?: boolean
}
export interface YdMultiSelectInstance {
  focus: () => void
  blur: () => void
  element: Readonly<import('vue').Ref<HTMLElement | null>>
}
const props = withDefaults(defineProps<{
  options: YdMultiSelectOption[]
  disabled?: boolean
  allowSearch?: boolean
  placeholder?: string
  emptyText?: string
  maxTagCount?: number
  id?: string
  ariaInvalid?: boolean | string
  ariaDescribedby?: string
  ariaLabel?: string
}>(), { disabled: false, allowSearch: true, placeholder: '请选择', emptyText: '没有匹配项' })
const emit = defineEmits<{
  change: [value: string[]]
  search: [value: string]
}>()
const value = defineModel<string[]>({ default: () => [] })
const root = ref<HTMLElement | null>(null)
const query = ref('')
const open = ref(false)
const visibleOptions = computed(() => props.options.filter(option => !query.value || option.label.toLocaleLowerCase().includes(query.value.toLocaleLowerCase())))
const tags = computed(() => value.value.map(id => props.options.find(option => option.value === id) ?? { value: id, label: id }))
const visibleTagState = computed(() => sliceYdMultiSelectTags(tags.value, props.maxTagCount))
const visibleTags = computed(() => visibleTagState.value.visible)
const hiddenCount = computed(() => visibleTagState.value.hiddenCount)
const inputAttrs = computed(() => resolveYdMultiSelectInputAttrs({ id: props.id, invalid: props.ariaInvalid, describedby: props.ariaDescribedby, label: props.ariaLabel }))
const inputState = computed(() => resolveYdMultiSelectInputState(props.allowSearch, props.disabled))

function update(next: unknown) {
  const normalized = normalizeYdMultiSelectValue(next, props.options, value.value)
  if (!normalized) {
    return
  }
  if (equalYdMultiSelectValues(normalized, value.value)) {
    return
  }
  value.value = normalized
  emit('change', value.value)
}
function remove(id: string) {
  if (props.disabled) {
    return
  }
  const next = removeYdMultiSelectValue(value.value, id)
  if (next.length === value.value.length) {
    return
  }
  value.value = next
  emit('change', next)
}
function search(next: string) {
  query.value = next
  emit('search', next)
}
watch(open, (next) => {
  if (!next) {
    query.value = ''
  }
})
watch(() => props.disabled, (disabled) => {
  if (disabled) {
    open.value = false
  }
})

function setOpen(next: boolean) {
  if (!props.disabled) {
    open.value = next
  }
}

function focus() {
  root.value?.querySelector<HTMLInputElement>('input')?.focus()
}

function blur() {
  root.value?.querySelector<HTMLInputElement>('input')?.blur()
}

defineExpose({ focus, blur, element: root })
</script>

<template>
  <div ref="root" class="yd-multi-select-root">
    <ComboboxRoot :model-value="value" :open="open" :disabled="disabled" multiple ignore-filter open-on-focus open-on-click @update:model-value="update" @update:open="setOpen">
      <ComboboxAnchor class="yd-collection-control yd-multi-select" :data-disabled="disabled || undefined">
        <span v-for="option in visibleTags" :key="option.value" class="yd-multi-select__tag">
          {{ option.label }}
          <button v-if="!disabled" type="button" :aria-label="`移除${option.label}`" @click.stop="remove(option.value)"><YdIcon name="morph:x" /></button>
        </span>
        <span v-if="hiddenCount" class="yd-multi-select__more">+{{ hiddenCount }}</span>
        <ComboboxInput v-bind="inputAttrs" :placeholder="value.length ? '' : placeholder" :readonly="inputState.readonly" :disabled="inputState.disabled" class="yd-multi-select__input" @update:model-value="search" />
        <ComboboxTrigger class="yd-collection-icon" aria-label="展开选项">
          <YdIcon name="morph:chevron-down" />
        </ComboboxTrigger>
      </ComboboxAnchor>
      <ComboboxPortal>
        <ComboboxContent position="popper" align="start" :side-offset="6" class="yd-collection-popup">
          <ComboboxViewport class="yd-collection-viewport">
            <ComboboxEmpty class="yd-collection-empty">
              <slot name="empty">
                {{ emptyText }}
              </slot>
            </ComboboxEmpty>
            <ComboboxItem v-for="option in visibleOptions" :key="option.value" :value="option.value" :disabled="option.disabled" class="yd-collection-option">
              <span><slot name="option" :option="option">{{ option.label }}</slot></span><ComboboxItemIndicator><YdIcon name="morph:check" /></ComboboxItemIndicator>
            </ComboboxItem>
          </ComboboxViewport>
        </ComboboxContent>
      </ComboboxPortal>
    </ComboboxRoot>
  </div>
</template>

<style>
.yd-multi-select { flex-wrap: wrap; gap: 4px; min-height: var(--yd-control-height); padding: 5px 8px; }
.yd-multi-select__tag { display: inline-flex; align-items: center; gap: 4px; border-radius: 999px; padding: 2px 7px; background: var(--color-fill-2); color: var(--color-text-1); font-size: .875em; }
.yd-multi-select__tag button { display: inline-flex; border: 0; background: transparent; color: inherit; cursor: pointer; }
.yd-multi-select__more { color: var(--color-text-3); font-size: .875em; }
.yd-multi-select__input { flex: 1; min-width: 4rem; border: 0; outline: 0; background: transparent; color: inherit; }
.yd-multi-select-root { min-width: 0; }
</style>
