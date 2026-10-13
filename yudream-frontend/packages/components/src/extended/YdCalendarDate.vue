<script setup lang="ts">
import type { DateValue } from '@internationalized/date'
import type { YdCalendarProps, YdCalendarValue } from './calendar'
import { useForwardProps } from 'reka-ui'
import { getCurrentInstance } from 'vue'
import YdCalendarPanel from './YdCalendarPanel.vue'

const props = withDefaults(defineProps<YdCalendarProps>(), {
  disabled: undefined,
  readonly: undefined,
  multiple: undefined,
  fixedWeeks: undefined,
  preventDeselect: undefined,
  pagedNavigation: undefined,
  allowNonContiguousRanges: undefined,
  picker: undefined,
  open: undefined,
  closeOnSelect: undefined,
})
defineEmits<{ 'update:modelValue': [value: YdCalendarValue], 'update:placeholder': [value: DateValue], 'update:open': [value: boolean], 'change': [value: YdCalendarValue], 'complete': [value: YdCalendarValue] }>()
const forwardProps = useForwardProps(props)
const instance = getCurrentInstance()
function isModelControlled() {
  return !!(instance?.vnode.props && ('modelValue' in instance.vnode.props || 'model-value' in instance.vnode.props))
}
</script>

<template>
  <YdCalendarPanel v-bind="forwardProps" :model-controlled="isModelControlled()" mode="date" :range="false" @update:model-value="$emit('update:modelValue', $event)" @update:placeholder="$emit('update:placeholder', $event)" @update:open="$emit('update:open', $event)" @change="$emit('change', $event)" @complete="$emit('complete', $event)">
    <template v-for="(_, name) in $slots" #[name]="scope">
      <slot :name="name" v-bind="scope" />
    </template>
  </YdCalendarPanel>
</template>
