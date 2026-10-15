<script setup lang="ts" generic="T extends string | number">
import { computed } from 'vue'

export interface YdSegmentedOption<T = string | number> {
  label: string
  value: T
  disabled?: boolean
}
const props = withDefaults(defineProps<{
  options: YdSegmentedOption<T>[]
  disabled?: boolean
  block?: boolean
  size?: 'sm' | 'md' | 'lg'
  ariaLabel?: string
}>(), { size: 'md', ariaLabel: '选项切换' })
const emit = defineEmits<{ change: [value: T] }>()
const model = defineModel<T>()
const id = computed(() => `yd-segmented-${Math.random().toString(36).slice(2, 9)}`)
function select(option: YdSegmentedOption<T>) {
  if (props.disabled || option.disabled || Object.is(model.value, option.value)) {
    return
  }
  model.value = option.value
  emit('change', option.value)
}
function onKeydown(event: KeyboardEvent, index: number) {
  if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) {
    return
  }
  event.preventDefault()
  const enabled = props.options.map((option, i) => ({ option, i })).filter(entry => !props.disabled && !entry.option.disabled)
  const current = enabled.findIndex(entry => entry.i === index)
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? enabled.length - 1 : (current + (event.key === 'ArrowRight' ? 1 : -1) + enabled.length) % enabled.length
  const target = enabled[next]
  if (target) {
    select(target.option)
    document.getElementById(`${id.value}-${target.i}`)?.focus()
  }
}
</script>

<template>
  <div :id="id" role="radiogroup" :aria-label="ariaLabel" class="yd-segmented" :class="[`yd-segmented--${size}`, { 'yd-segmented--block': block, 'yd-segmented--disabled': disabled }]">
    <button v-for="(option, index) in options" :id="`${id}-${index}`" :key="String(option.value)" type="button" role="radio" :aria-checked="Object.is(model, option.value)" :disabled="disabled || option.disabled" :tabindex="Object.is(model, option.value) || (!model && index === 0) ? 0 : -1" class="yd-segmented__option" @click="select(option)" @keydown="onKeydown($event, index)">
      <slot name="option" :option="option" :selected="Object.is(model, option.value)">
        {{ option.label }}
      </slot>
    </button>
  </div>
</template>

<style scoped>
.yd-segmented { display: inline-flex; max-width: 100%; gap: 2px; padding: 3px; background: oklch(var(--muted)); border: 1px solid oklch(var(--border)); border-radius: var(--yd-control-radius); }
.yd-segmented--block { display: flex; width: 100%; }
.yd-segmented__option { flex: 1 1 auto; min-width: 0; padding: 0.4rem 0.75rem; color: oklch(var(--muted-foreground)); background: transparent; border: 0; border-radius: calc(var(--yd-control-radius) - 2px); cursor: pointer; }
.yd-segmented__option[aria-checked="true"] { color: oklch(var(--foreground)); background: oklch(var(--background)); box-shadow: 0 1px 2px oklch(var(--foreground) / 0.12); }
.yd-segmented__option:focus-visible { outline: 2px solid oklch(var(--ring)); outline-offset: 1px; }
.yd-segmented__option:disabled { cursor: not-allowed; opacity: 0.5; }
.yd-segmented--sm .yd-segmented__option { padding: 0.25rem 0.5rem; font-size: 0.75rem; }
.yd-segmented--lg .yd-segmented__option { padding: 0.55rem 1rem; }
.yd-segmented--disabled { opacity: 0.6; }
</style>
