<script setup lang="ts">
import type { DisabledTimeProps, ShortcutType } from '@arco-design/web-vue/es/date-picker/interface'
import type { HTMLAttributes } from 'vue'
import { RangePicker as ARangePicker } from '@arco-design/web-vue'
import { computed } from 'vue'
import { cn } from '#utils'

defineOptions({
  name: 'YdRangePicker',
})

const props = withDefaults(defineProps<{
  mode?: 'date' | 'week' | 'month' | 'quarter' | 'year'
  showTime?: boolean
  valueFormat?: string
  format?: string
  placeholder?: string[]
  disabled?: boolean
  readonly?: boolean
  error?: boolean
  allowClear?: boolean
  dayStartOfWeek?: 0 | 1
  disabledDate?: (current?: Date) => boolean
  disabledTime?: (current: Date, type: 'start' | 'end') => DisabledTimeProps
  shortcuts?: ShortcutType[]
  shortcutsPosition?: 'left' | 'bottom' | 'right'
  popupContainer?: string | HTMLElement
  class?: HTMLAttributes['class']
}>(), {
  mode: 'date',
  allowClear: true,
})

const emits = defineEmits<{
  change: [value: (string | number)[] | undefined]
  clear: []
  ok: [value: (string | number)[] | undefined]
  popupVisibleChange: [visible: boolean]
}>()

const value = defineModel<(string | number)[] | undefined>()

const mergedValueFormat = computed(() => props.valueFormat ?? (props.showTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'))

function onChange(v: (string | number | Date | undefined)[] | undefined) {
  emits('change', v as (string | number)[] | undefined)
}

function onOk(v: (string | number | Date | undefined)[] | undefined) {
  emits('ok', v as (string | number)[] | undefined)
}
</script>

<template>
  <ARangePicker
    v-model="value"
    :mode="props.mode"
    :show-time="props.showTime"
    :value-format="mergedValueFormat"
    :format="props.format"
    :placeholder="props.placeholder ?? ['开始日期', '结束日期']"
    :disabled="props.disabled"
    :readonly="props.readonly"
    :error="props.error"
    :allow-clear="props.allowClear"
    :day-start-of-week="props.dayStartOfWeek"
    :disabled-date="props.disabledDate"
    :disabled-time="props.disabledTime"
    :shortcuts="props.shortcuts"
    :shortcuts-position="props.shortcutsPosition"
    :popup-container="props.popupContainer"
    :class="cn('yd-picker', props.class)"
    @change="onChange"
    @ok="onOk"
    @clear="emits('clear')"
    @popup-visible-change="(visible: boolean) => emits('popupVisibleChange', visible)"
  />
</template>

<style src="../picker-styles.css"></style>
