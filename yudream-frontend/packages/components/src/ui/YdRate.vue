<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { formItemInjectionKey } from '@arco-design/web-vue/es/form/context'
import { computed, inject, ref, useTemplateRef } from 'vue'
import { YdIcon } from '../adapters'
import { getYdRatePointerValue, normalizeYdRate } from './rate'

defineOptions({ name: 'YdRate', inheritAttrs: false })
const props = withDefaults(defineProps<{
  count?: number
  allowHalf?: boolean
  allowClear?: boolean
  disabled?: boolean
  readonly?: boolean
  ariaLabel?: string
  class?: HTMLAttributes['class']
}>(), { count: 5, disabled: undefined, ariaLabel: '评分' })
const emits = defineEmits<{ change: [value: number], hoverChange: [value: number], focus: [event: FocusEvent], blur: [event: FocusEvent] }>()
const value = defineModel<number>({ default: 0 })
const context = inject(formItemInjectionKey)
const disabled = computed(() => props.disabled ?? context?.disabled ?? false)
const count = computed(() => Math.max(1, Math.floor(Number.isFinite(props.count) ? props.count : 5)))
const hoverValue = ref<number | undefined>()
const displayValue = computed(() => hoverValue.value ?? normalizeYdRate(value.value, count.value, props.allowHalf))
const inputRef = useTemplateRef<HTMLInputElement>('inputRef')

function update(next: number) {
  if (disabled.value || props.readonly) {
    return
  }
  const normalized = normalizeYdRate(next, count.value, props.allowHalf)
  if (normalized !== value.value) {
    value.value = normalized
    emits('change', normalized)
    context?.eventHandlers.onChange?.()
  }
}
function pointerValue(index: number, event: MouseEvent) {
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  return getYdRatePointerValue(index, event.clientX, bounds.left, bounds.width, props.allowHalf)
}
function select(index: number, event: MouseEvent) {
  const next = pointerValue(index, event)
  update(props.allowClear && next === value.value ? 0 : next)
  inputRef.value?.focus()
}
function hover(index: number, event: MouseEvent) {
  if (!disabled.value && !props.readonly) {
    hoverValue.value = pointerValue(index, event)
    emits('hoverChange', hoverValue.value)
  }
}
function resetHover() {
  if (hoverValue.value !== undefined) {
    hoverValue.value = undefined
    emits('hoverChange', 0)
  }
}
function keydown(event: KeyboardEvent) {
  if (props.readonly || disabled.value) {
    if (['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown', 'Delete', 'Backspace'].includes(event.key)) {
      event.preventDefault()
    }
  }
  else if (props.allowClear && ['Delete', 'Backspace'].includes(event.key)) {
    event.preventDefault()
    update(0)
  }
}
defineExpose({ focus: () => inputRef.value?.focus(), blur: () => inputRef.value?.blur() })
</script>

<template>
  <span class="yd-rate" :class="[props.class]" :data-disabled="disabled" :data-readonly="props.readonly" @mouseleave="resetHover">
    <input
      v-bind="$attrs"
      ref="inputRef"
      type="range"
      class="yd-rate-input"
      :min="0"
      :max="count"
      :step="props.allowHalf ? 0.5 : 1"
      :value="value"
      :disabled="disabled"
      :aria-label="props.ariaLabel"
      :aria-readonly="props.readonly"
      :aria-valuetext="`${value} / ${count}`"
      @input="event => update(Number((event.target as HTMLInputElement).value))"
      @keydown="keydown"
      @focus="event => { emits('focus', event); context?.eventHandlers.onFocus?.() }"
      @blur="event => { emits('blur', event); context?.eventHandlers.onBlur?.() }"
    >
    <button
      v-for="(_, index) in count"
      :key="index"
      type="button"
      class="yd-rate-star"
      :disabled="disabled || props.readonly"
      tabindex="-1"
      aria-hidden="true"
      :aria-label="`选择 ${index + 1} 分${props.allowHalf ? '，左半区选择半分' : ''}`"
      @click="select(index, $event)"
      @mousemove="hover(index, $event)"
    >
      <YdIcon name="morph:star" class="yd-rate-outline" />
      <span class="yd-rate-fill" :style="{ width: `${Math.min(1, Math.max(0, displayValue - index)) * 100}%` }"><YdIcon name="morph:star" /></span>
    </button>
  </span>
</template>

<style src="./ui.css"></style>
