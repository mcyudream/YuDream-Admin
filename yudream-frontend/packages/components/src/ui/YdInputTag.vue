<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import type { YdInputTagItem, YdInputTagValue, YdUiSize } from './types'
import { InputTag as AInputTag } from '@arco-design/web-vue'
import { computed, onMounted, onUpdated, useAttrs, useTemplateRef } from 'vue'

defineOptions({ name: 'YdInputTag', inheritAttrs: false })
const props = withDefaults(defineProps<{
  placeholder?: string
  disabled?: boolean
  readonly?: boolean
  error?: boolean
  allowClear?: boolean
  size?: YdUiSize
  maxTagCount?: number
  uniqueValue?: boolean
  tagNowrap?: boolean
  retainInputValue?: boolean | { create?: boolean, blur?: boolean }
  formatTag?: (data: YdInputTagItem) => string
  class?: HTMLAttributes['class']
}>(), { disabled: undefined, allowClear: true, uniqueValue: true, maxTagCount: 0 })
const emits = defineEmits<{
  change: [value: YdInputTagValue[], event: Event]
  inputValueChange: [value: string, event: Event]
  pressEnter: [value: string, event: KeyboardEvent]
  remove: [value: string | number, event: Event]
  clear: [event: MouseEvent]
  focus: [event: FocusEvent]
  blur: [event: FocusEvent]
}>()
const value = defineModel<YdInputTagValue[]>({ default: () => [] })
const inputValue = defineModel<string>('inputValue', { default: '' })
const inputRef = useTemplateRef<InstanceType<typeof AInputTag>>('inputRef')
const attrs = useAttrs()
const inputAttributeNames = ['id', 'name', 'aria-label', 'aria-labelledby', 'aria-describedby', 'aria-invalid', 'aria-required', 'autocomplete', 'tabindex'] as const
// The underlying engine only forwards input events to its native input.
// Keep labelable/accessibility attributes off its outer span and bind them to the input.
function wrapperAttrs() {
  return Object.fromEntries(Object.entries(attrs).filter(([name]) => !inputAttributeNames.includes(name as typeof inputAttributeNames[number])))
}
function syncInputAttributes() {
  const input = inputRef.value?.inputRef
  if (!input) {
    return
  }
  for (const name of inputAttributeNames) {
    const attribute = attrs[name]
    if (attribute === undefined || attribute === null) {
      input.removeAttribute(name)
    }
    else {
      input.setAttribute(name, String(attribute))
    }
  }
}
onMounted(syncInputAttributes)
onUpdated(syncInputAttributes)
// Arco's raw tag has optional value; our public model requires stable identity.
const formatter = computed(() => props.formatTag ? (data: { value?: string | number, label?: string, closable?: boolean }) => props.formatTag?.({ ...data, value: data.value ?? '' }) ?? '' : undefined)
defineExpose({ focus: () => inputRef.value?.focus(), blur: () => inputRef.value?.blur() })
</script>

<template>
  <AInputTag
    v-bind="{ ...wrapperAttrs(), ...props }"
    ref="inputRef"
    v-model="value"
    v-model:input-value="inputValue"
    :format-tag="formatter"
    class="yd-input-tag" :class="[props.class]"
    @change="(value, event) => emits('change', value as YdInputTagValue[], event)"
    @input-value-change="(value, event) => emits('inputValueChange', value, event)"
    @press-enter="(value, event) => emits('pressEnter', value, event)"
    @remove="(value, event) => emits('remove', value, event)"
    @clear="event => emits('clear', event)"
    @focus="event => emits('focus', event)"
    @blur="event => emits('blur', event)"
  >
    <template v-if="$slots.prefix" #prefix>
      <slot name="prefix" />
    </template>
    <template v-if="$slots.suffix" #suffix>
      <slot name="suffix" />
    </template>
    <template v-if="$slots.tag" #tag="{ data }">
      <slot name="tag" :data="data as YdInputTagItem" />
    </template>
  </AInputTag>
</template>

<style src="./ui.css"></style>
