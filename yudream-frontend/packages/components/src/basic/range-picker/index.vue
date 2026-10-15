<script setup lang="ts">
import type { DateValue } from '@internationalized/date'
import type { HTMLAttributes } from 'vue'
import type { YdCalendarValue } from '../../extended/calendar'
import type { YdPickerDisabledTime, YdPickerMode, YdPickerShortcut, YdPickerTimePart, YdPickerValueFormat } from '../picker/picker-model'
import { computed, ref, watch } from 'vue'
import { isDateRange } from '../../extended/calendar'
import YdCalendarPanel from '../../extended/YdCalendarPanel.vue'
import FaButton from '../button/index.vue'
import { parsePickerDayjs, pickerCalendarValue, pickerClockTokens, pickerDateValue, pickerDayjs, pickerDefaultDisplayFormat, pickerDefaultValueFormat, pickerDisabledMatchers, pickerDisplayText, pickerJsDate, pickerNowTime, pickerParseTime, pickerRangeBinding, pickerShortcutItems, pickerTimeFormat, pickerTimeWithDay } from '../picker/picker-model'
import PickerTrigger from '../picker/PickerTrigger.vue'
import TimeColumns from '../picker/TimeColumns.vue'
import FaPopover from '../popover/index.vue'
import '../picker/picker.css'

defineOptions({
  name: 'YdRangePicker',
  inheritAttrs: false,
})

const props = withDefaults(defineProps<{
  mode?: YdPickerMode
  showTime?: boolean
  valueFormat?: YdPickerValueFormat
  format?: string
  placeholder?: string[]
  disabled?: boolean
  readonly?: boolean
  error?: boolean
  allowClear?: boolean
  dayStartOfWeek?: 0 | 1 | 2 | 3 | 4 | 5 | 6
  disabledDate?: (current?: Date) => boolean
  disabledTime?: (current?: Date, type?: 'start' | 'end') => YdPickerDisabledTime
  shortcuts?: YdPickerShortcut[]
  shortcutsPosition?: 'left' | 'bottom' | 'right'
  popupContainer?: string | HTMLElement
  class?: HTMLAttributes['class']
}>(), {
  mode: 'date',
  showTime: false,
  allowClear: true,
})

const emit = defineEmits<{
  change: [value: (string | number)[] | undefined]
  clear: []
  ok: [value: (string | number)[] | undefined]
  popupVisibleChange: [visible: boolean]
}>()

const value = defineModel<(string | number)[] | undefined>()

const open = ref(false)
const pending = ref<YdCalendarValue>()
const startTime = ref<YdPickerTimePart>(pickerNowTime())
const endTime = ref<YdPickerTimePart>(pickerNowTime())

const withTime = computed(() => props.showTime && (props.mode === 'date' || props.mode === 'week'))
const displayFormat = computed(() => props.format || pickerDefaultDisplayFormat(props.mode, withTime.value))
const bindingFormat = computed(() => props.valueFormat || pickerDefaultValueFormat(props.mode, withTime.value))
const timeFormat = computed(() => pickerTimeFormat(displayFormat.value))
const timeTokens = computed(() => pickerClockTokens(displayFormat.value))
const weekStartsOn = computed(() => props.dayStartOfWeek ?? 1)
const committedValue = computed<YdCalendarValue>(() => pickerCalendarValue(value.value, bindingFormat.value, { mode: props.mode, range: true, withTime: withTime.value }))
const panelValue = computed<YdCalendarValue>(() => pending.value ?? committedValue.value)
const matchers = computed(() => pickerDisabledMatchers({ mode: props.mode, weekStartsOn: weekStartsOn.value, disabledDate: props.disabledDate }))
const panelShortcuts = computed(() => pickerShortcutItems(props.shortcuts, { valueFormat: bindingFormat.value, mode: props.mode, withTime: withTime.value, range: true }))
const startDate = computed(() => pending.value && isDateRange(pending.value) ? pending.value.start : (isDateRange(committedValue.value) ? committedValue.value.start : undefined))
const endDate = computed(() => pending.value && isDateRange(pending.value) ? pending.value.end : (isDateRange(committedValue.value) ? committedValue.value.end : undefined))
const disabledTimeStart = computed<YdPickerDisabledTime>(() => withTime.value ? props.disabledTime?.(startDate.value ? pickerJsDate(startDate.value) : undefined, 'start') ?? {} : {})
const disabledTimeEnd = computed<YdPickerDisabledTime>(() => withTime.value ? props.disabledTime?.(endDate.value ? pickerJsDate(endDate.value) : undefined, 'end') ?? {} : {})
const startTimeString = computed(() => timeString(startTime.value))
const endTimeString = computed(() => timeString(endTime.value))
const canConfirm = computed(() => {
  const source = pending.value
  return isDateRange(source) && !!source.start && !!source.end
})

function timeString(time: YdPickerTimePart): string {
  return `${String(time.hour).padStart(2, '0')}:${String(time.minute).padStart(2, '0')}:${String(time.second).padStart(2, '0')}`
}

function endpointText(day: DateValue | undefined, time: YdPickerTimePart | undefined): string {
  if (!day) {
    return ''
  }
  return pickerTimeWithDay(pickerDayjs(day), withTime.value ? time : undefined).format(displayFormat.value)
}

const displayText = computed<string[]>(() => {
  const source = pending.value ?? committedValue.value
  if (isDateRange(source)) {
    return [endpointText(source.start, startTime.value), endpointText(source.end, endTime.value)]
  }
  const raw = Array.isArray(value.value) ? value.value : []
  return [
    raw[0] === undefined ? '' : pickerDisplayText(raw[0], bindingFormat.value, displayFormat.value, props.mode),
    raw[1] === undefined ? '' : pickerDisplayText(raw[1], bindingFormat.value, displayFormat.value, props.mode),
  ]
})

function commit(withOk: boolean) {
  const next = pickerRangeBinding(pending.value ?? committedValue.value, bindingFormat.value, withTime.value ? startTime.value : undefined, withTime.value ? endTime.value : undefined) as (string | number)[] | undefined
  if (next === undefined) {
    return false
  }
  value.value = next
  emit('change', next)
  if (withOk) {
    emit('ok', next)
  }
  return true
}

function onPanelValue(next: YdCalendarValue) {
  pending.value = next
  if (!withTime.value && isDateRange(next) && next.start && next.end) {
    commit(false)
  }
}

function confirmPending() {
  if (!canConfirm.value) {
    return
  }
  commit(true)
  pending.value = undefined
  open.value = false
}

function clear() {
  value.value = undefined
  pending.value = undefined
  emit('change', undefined)
  emit('clear')
}

function commitTyped(text: string, index: number) {
  const input = text.trim()
  if (!input) {
    if (index === 0 && !value.value?.length) {
      clear()
    }
    return
  }
  const parsed = parsePickerDayjs(input, props.format || displayFormat.value, props.mode)
  if (!parsed || props.disabledDate?.(parsed.toDate()) === true) {
    return
  }
  if (withTime.value && timeTokens.value.hour) {
    const time = { hour: parsed.hour(), minute: parsed.minute(), second: parsed.second() }
    if (index === 0) {
      startTime.value = time
    }
    else {
      endTime.value = time
    }
  }
  const current = pending.value && isDateRange(pending.value) ? pending.value : { start: startDate.value, end: endDate.value }
  const date = pickerDateValue(parsed, withTime.value)
  pending.value = index === 0 ? { start: date, end: current.end } : { start: current.start, end: date }
  if (!withTime.value && pending.value && isDateRange(pending.value) && pending.value.start && pending.value.end) {
    commit(false)
    pending.value = undefined
  }
}

watch(open, (visible) => {
  if (visible) {
    const committed = committedValue.value
    if (isDateRange(committed) && withTime.value) {
      const start = committed.start && 'hour' in committed.start ? committed.start as { hour: number, minute: number, second: number } : undefined
      const end = committed.end && 'hour' in committed.end ? committed.end as { hour: number, minute: number, second: number } : undefined
      startTime.value = start ? { hour: start.hour, minute: start.minute, second: start.second } : pickerNowTime()
      endTime.value = end ? { hour: end.hour, minute: end.minute, second: end.second } : pickerNowTime()
    }
    else if (withTime.value) {
      startTime.value = pickerNowTime()
      endTime.value = pickerNowTime()
    }
  }
  pending.value = undefined
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
      :placeholder="placeholder ?? ['开始日期', '结束日期']"
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
          <YdCalendarPanel
            range
            :model-value="panelValue"
            :mode="mode"
            :week-starts-on="weekStartsOn"
            :surface="false"
            :disabled="disabled"
            :readonly="readonly"
            :shortcuts="panelShortcuts"
            :shortcuts-position="shortcutsPosition ?? 'top'"
            :is-date-disabled="matchers.isDateDisabled"
            :is-month-disabled="matchers.isMonthDisabled"
            :is-quarter-disabled="matchers.isQuarterDisabled"
            :is-year-disabled="matchers.isYearDisabled"
            :open="open"
            :close-on-select="withTime ? false : undefined"
            @update:model-value="onPanelValue"
            @update:open="open = $event"
          />
          <div v-if="withTime" class="yd-picker-time">
            <div class="yd-picker-time-group">
              <span class="yd-picker-time-label">开始时间</span>
              <TimeColumns
                :model-value="startTimeString"
                :format="timeFormat"
                :disabled-hours="disabledTimeStart.disabledHours"
                :disabled-minutes="disabledTimeStart.disabledMinutes"
                :disabled-seconds="disabledTimeStart.disabledSeconds"
                :disabled="disabled || readonly"
                @update:model-value="(next: string) => startTime = pickerParseTime(next, startTime)"
              />
            </div>
            <div class="yd-picker-time-group">
              <span class="yd-picker-time-label">结束时间</span>
              <TimeColumns
                :model-value="endTimeString"
                :format="timeFormat"
                :disabled-hours="disabledTimeEnd.disabledHours"
                :disabled-minutes="disabledTimeEnd.disabledMinutes"
                :disabled-seconds="disabledTimeEnd.disabledSeconds"
                :disabled="disabled || readonly"
                @update:model-value="(next: string) => endTime = pickerParseTime(next, endTime)"
              />
            </div>
          </div>
        </div>
        <div v-if="withTime" class="yd-picker-footer">
          <FaButton type="button" size="sm" :disabled="disabled || !canConfirm" @click="confirmPending">
            确定
          </FaButton>
        </div>
      </div>
    </template>
  </FaPopover>
</template>
