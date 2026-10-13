<script setup lang="ts">
import { computed, useTemplateRef } from 'vue'
import { resetYdFileInput, resolveYdFilePickerTriggerAttrs, toYdFileArray } from './file-picker-model'

defineOptions({ name: 'YdFilePicker', inheritAttrs: false })
const props = withDefaults(defineProps<{
  accept?: string
  multiple?: boolean
  directory?: boolean
  disabled?: boolean
  label?: string
  id?: string
  ariaLabel?: string
  ariaInvalid?: boolean | string
  ariaDescribedby?: string
}>(), { multiple: false, disabled: false, label: '选择文件' })
const emit = defineEmits<{ select: [files: File[]] }>()
const inputRef = useTemplateRef<HTMLInputElement>('inputRef')
const triggerAttrs = computed(() => resolveYdFilePickerTriggerAttrs({
  id: props.id,
  label: props.label,
  ariaLabel: props.ariaLabel,
  invalid: props.ariaInvalid,
  describedby: props.ariaDescribedby,
}))

function open() {
  if (!props.disabled) {
    inputRef.value?.click()
  }
}
function focus() {
  if (props.disabled) {
    return
  }
  const trigger = inputRef.value?.parentElement?.querySelector<HTMLElement>('button:not([disabled]), [tabindex]:not([tabindex="-1"])')
  trigger?.focus()
}
function blur() {
  const trigger = inputRef.value?.parentElement?.querySelector<HTMLElement>('button, [tabindex]:not([tabindex="-1"])')
  trigger?.blur()
}
function reset() {
  resetYdFileInput(inputRef.value)
}
function onChange(event: Event) {
  const input = event.currentTarget as HTMLInputElement
  emit('select', toYdFileArray(input.files))
}

defineExpose({ open, reset, focus, blur, element: inputRef })
</script>

<template>
  <span class="yd-file-picker" v-bind="$attrs">
    <input
      ref="inputRef"
      class="yd-file-picker__input"
      type="file"
      :accept="props.accept"
      :multiple="props.multiple"
      :webkitdirectory="props.directory || undefined"
      :disabled="props.disabled"
      tabindex="-1"
      aria-hidden="true"
      @change="onChange"
    >
    <slot :open="open" :reset="reset" :disabled="props.disabled" :label="props.label" :trigger-attrs="triggerAttrs">
      <button v-bind="triggerAttrs" type="button" :disabled="props.disabled" @click="open">{{ props.label }}</button>
    </slot>
  </span>
</template>

<style>
.yd-file-picker { display: inline-flex; }
.yd-file-picker__input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; clip-path: inset(50%); }
</style>
