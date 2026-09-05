<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { TimePicker as ATimePicker } from '@arco-design/web-vue'
import { computed } from 'vue'
import { cn } from '#utils'

defineOptions({
  name: 'YdTimePicker',
})

const props = withDefaults(defineProps<{
  range?: boolean
  format?: string
  placeholder?: string | string[]
  disabled?: boolean
  readonly?: boolean
  error?: boolean
  allowClear?: boolean
  use12Hours?: boolean
  step?: {
    hour?: number
    minute?: number
    second?: number
  }
  disabledHours?: () => number[]
  disabledMinutes?: (selectedHour?: number) => number[]
  disabledSeconds?: (selectedHour?: number, selectedMinute?: number) => number[]
  hideDisabledOptions?: boolean
  popupContainer?: string | HTMLElement
  class?: HTMLAttributes['class']
}>(), {
  format: 'HH:mm:ss',
  allowClear: true,
})

const emits = defineEmits<{
  change: [value: string | string[] | undefined]
  clear: []
  popupVisibleChange: [visible: boolean]
}>()

const value = defineModel<string | (string | undefined)[] | undefined>()

type ArcoTimeValue = Date | string | number

const innerValue = computed<ArcoTimeValue | ArcoTimeValue[] | undefined>({
  get: () => value.value as unknown as ArcoTimeValue | ArcoTimeValue[] | undefined,
  set: v => value.value = v as string | (string | undefined)[] | undefined,
})

function onChange(v: string | (string | undefined)[] | undefined) {
  emits('change', v as string | string[] | undefined)
}
</script>

<template>
  <ATimePicker
    v-model="innerValue"
    :type="props.range ? 'time-range' : 'time'"
    :format="props.format"
    :placeholder="props.placeholder ?? (props.range ? ['开始时间', '结束时间'] : '请选择时间')"
    :disabled="props.disabled"
    :readonly="props.readonly"
    :error="props.error"
    :allow-clear="props.allowClear"
    :use12-hours="props.use12Hours"
    :step="props.step"
    :disabled-hours="props.disabledHours"
    :disabled-minutes="props.disabledMinutes"
    :disabled-seconds="props.disabledSeconds"
    :hide-disabled-options="props.hideDisabledOptions"
    :popup-container="props.popupContainer"
    :class="cn('yd-picker', props.class)"
    @change="onChange"
    @clear="emits('clear')"
    @popup-visible-change="(visible: boolean) => emits('popupVisibleChange', visible)"
  />
</template>

<style src="../picker-styles.css"></style>
