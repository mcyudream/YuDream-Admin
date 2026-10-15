<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import type { YdPickerTimePart } from '../picker/picker-model'
import dayjs from 'dayjs'
import { computed, ref, watch } from 'vue'
import FaButton from '../button/index.vue'
import { parsePickerDayjs, pickerNowTime, pickerTimeWithDay } from '../picker/picker-model'
import PickerTrigger from '../picker/PickerTrigger.vue'
import TimeColumns from '../picker/TimeColumns.vue'
import FaPopover from '../popover/index.vue'
import '../picker/picker.css'

defineOptions({
  name: 'YdTimePicker',
  inheritAttrs: false,
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

const emit = defineEmits<{
  change: [value: string | string[] | undefined]
  clear: []
  popupVisibleChange: [visible: boolean]
}>()

const value = defineModel<string | (string | undefined)[] | undefined>()

const open = ref(false)
const startParts = ref<YdPickerTimePart>()
const endParts = ref<YdPickerTimePart>()

function partsOf(raw: string | undefined): YdPickerTimePart | undefined {
  const parsed = typeof raw === 'string' && raw ? parsePickerDayjs(raw, props.format) : undefined
  return parsed?.isValid() ? { hour: parsed.hour(), minute: parsed.minute(), second: parsed.second() } : undefined
}

const committedStart = computed(() => partsOf(props.range ? (Array.isArray(value.value) ? value.value[0] : undefined) : value.value as string | undefined))
const committedEnd = computed(() => partsOf(props.range && Array.isArray(value.value) ? value.value[1] : undefined))
const displayStart = computed(() => open.value ? startParts.value : committedStart.value)
const displayEnd = computed(() => open.value ? endParts.value : committedEnd.value)
const startTimeString = computed(() => timeString(displayStart.value))
const endTimeString = computed(() => timeString(displayEnd.value))

function timeString(parts: YdPickerTimePart | undefined): string {
  if (!parts) {
    return ''
  }
  return `${String(parts.hour).padStart(2, '0')}:${String(parts.minute).padStart(2, '0')}:${String(parts.second).padStart(2, '0')}`
}

function formatParts(parts: YdPickerTimePart): string {
  return pickerTimeWithDay(dayjs(), parts).format(props.format)
}

const displayText = computed(() => props.range
  ? [displayStart.value ? formatParts(displayStart.value) : '', displayEnd.value ? formatParts(displayEnd.value) : '']
  : displayStart.value ? formatParts(displayStart.value) : '')

function valueOf(parts: YdPickerTimePart | undefined): string | undefined {
  return parts ? formatParts(parts) : undefined
}

function commit() {
  if (props.range) {
    const next = [valueOf(displayStart.value), valueOf(displayEnd.value)] as string[]
    value.value = next
    emit('change', next)
    return true
  }
  const next = valueOf(displayStart.value)
  if (next === undefined) {
    return false
  }
  value.value = next
  emit('change', next)
  return true
}

function confirmPending() {
  if (!commit()) {
    return
  }
  open.value = false
}

function clear() {
  value.value = undefined
  emit('change', undefined)
  emit('clear')
}

function commitTyped(text: string, index: number) {
  const input = text.trim()
  if (!input) {
    if (index === 0 && !props.range) {
      clear()
    }
    return
  }
  const parts = partsOf(input)
  if (!parts) {
    return
  }
  if (props.range) {
    if (index === 0) {
      startParts.value = parts
    }
    else {
      endParts.value = parts
    }
    commit()
  }
  else {
    startParts.value = parts
    commit()
    open.value = false
  }
}

watch(open, (visible) => {
  if (visible) {
    startParts.value = committedStart.value ?? pickerNowTime()
    endParts.value = committedEnd.value ?? pickerNowTime()
  }
  emit('popupVisibleChange', visible)
})

watch(() => props.disabled, (disabled) => {
  if (disabled) {
    open.value = false
  }
})
</script>

<template>
  <FaPopover
    :open="open && !disabled"
    align="start"
    :container="popupContainer"
    class="p-0 border-0 min-w-0 shadow-none z-3000"
    @update:open="open = !disabled && $event"
  >
    <PickerTrigger
      v-bind="$attrs"
      range
      :text="displayText"
      :placeholder="placeholder ?? (range ? ['开始时间', '结束时间'] : '请选择时间')"
      :disabled="disabled"
      :readonly="readonly"
      :clearable="allowClear"
      :error="error"
      :class="props.class"
      @clear="clear"
      @commit="commitTyped"
    />
    <template #panel>
      <div class="yd-picker-panel yd-surface">
        <div class="yd-picker-panel-main">
          <div class="yd-picker-time">
            <div class="yd-picker-time-group">
              <span v-if="range" class="yd-picker-time-label">开始时间</span>
              <TimeColumns
                :model-value="startTimeString"
                :format="format"
                :use12-hours="use12Hours"
                :step="step"
                :disabled-hours="disabledHours"
                :disabled-minutes="disabledMinutes"
                :disabled-seconds="disabledSeconds"
                :hide-disabled-options="hideDisabledOptions"
                :disabled="disabled || readonly"
                @update:model-value="(next: string) => startParts = partsOf(next)"
              />
            </div>
            <div v-if="range" class="yd-picker-time-group">
              <span class="yd-picker-time-label">结束时间</span>
              <TimeColumns
                :model-value="endTimeString"
                :format="format"
                :use12-hours="use12Hours"
                :step="step"
                :disabled-hours="disabledHours"
                :disabled-minutes="disabledMinutes"
                :disabled-seconds="disabledSeconds"
                :hide-disabled-options="hideDisabledOptions"
                :disabled="disabled || readonly"
                @update:model-value="(next: string) => endParts = partsOf(next)"
              />
            </div>
          </div>
        </div>
        <div class="yd-picker-footer">
          <FaButton type="button" size="sm" :disabled="disabled || !displayStart || (range && !displayEnd)" @click="confirmPending">
            确定
          </FaButton>
        </div>
      </div>
    </template>
  </FaPopover>
</template>
