<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import type { YdCalendarValue } from '../../extended/calendar'
import type { YdPickerDisabledTime, YdPickerMode, YdPickerShortcut, YdPickerTimePart, YdPickerValueFormat } from '../picker/picker-model'
import { computed, ref, watch } from 'vue'
import YdCalendarPanel from '../../extended/YdCalendarPanel.vue'
import FaButton from '../button/index.vue'
import { parsePickerDayjs, pickerCalendarDisplayText, pickerCalendarValue, pickerClockTokens, pickerDateValue, pickerDefaultDisplayFormat, pickerDefaultValueFormat, pickerDisabledMatchers, pickerDisplayText, pickerJsDate, pickerNowTime, pickerParseTime, pickerShortcutItems, pickerSingleBinding, pickerTimeFormat } from '../picker/picker-model'
import PickerTrigger from '../picker/PickerTrigger.vue'
import TimeColumns from '../picker/TimeColumns.vue'
import FaPopover from '../popover/index.vue'
import '../picker/picker.css'

defineOptions({
  name: 'YdDatePicker',
  inheritAttrs: false,
})

const props = withDefaults(defineProps<{
  mode?: YdPickerMode
  showTime?: boolean
  valueFormat?: YdPickerValueFormat
  format?: string
  placeholder?: string
  disabled?: boolean
  readonly?: boolean
  error?: boolean
  allowClear?: boolean
  dayStartOfWeek?: 0 | 1 | 2 | 3 | 4 | 5 | 6
  disabledDate?: (current?: Date) => boolean
  disabledTime?: (current?: Date) => YdPickerDisabledTime
  shortcuts?: YdPickerShortcut[]
  shortcutsPosition?: 'left' | 'bottom' | 'right' | 'top'
  popupContainer?: string | HTMLElement
  class?: HTMLAttributes['class']
}>(), {
  mode: 'date',
  showTime: false,
  allowClear: true,
})

const emit = defineEmits<{
  change: [value: string | number | undefined]
  clear: []
  ok: [value: string | number | undefined]
  popupVisibleChange: [visible: boolean]
}>()

const value = defineModel<string | number | undefined>()

const open = ref(false)
const pending = ref<YdCalendarValue>()
const pendingTime = ref<YdPickerTimePart>(pickerNowTime())

const withTime = computed(() => props.showTime && (props.mode === 'date' || props.mode === 'week'))
const displayFormat = computed(() => props.format || pickerDefaultDisplayFormat(props.mode, withTime.value))
const bindingFormat = computed(() => props.valueFormat || pickerDefaultValueFormat(props.mode, withTime.value))
const timeFormat = computed(() => pickerTimeFormat(displayFormat.value))
const timeTokens = computed(() => pickerClockTokens(displayFormat.value))
const weekStartsOn = computed(() => props.dayStartOfWeek ?? 1)
const committedValue = computed<YdCalendarValue>(() => pickerCalendarValue(value.value, bindingFormat.value, { mode: props.mode, withTime: withTime.value }))
const panelValue = computed<YdCalendarValue>(() => pending.value ?? committedValue.value)
const matchers = computed(() => pickerDisabledMatchers({ mode: props.mode, weekStartsOn: weekStartsOn.value, disabledDate: props.disabledDate }))
const panelShortcuts = computed(() => pickerShortcutItems(props.shortcuts, { valueFormat: bindingFormat.value, mode: props.mode, withTime: withTime.value }))
const displayText = computed(() => pending.value
  ? pickerCalendarDisplayText(pending.value, displayFormat.value, withTime.value ? pendingTime.value : undefined)
  : pickerDisplayText(value.value, bindingFormat.value, displayFormat.value, props.mode))
const canConfirm = computed(() => !!pending.value)
const selectedDate = computed<Date | undefined>(() => {
  const source = pending.value ?? committedValue.value
  if (source && !Array.isArray(source) && !('start' in source)) {
    return pickerJsDate(source)
  }
  return parsePickerDayjs(value.value, bindingFormat.value, props.mode)?.toDate()
})
const disabledTime = computed<YdPickerDisabledTime>(() => withTime.value ? props.disabledTime?.(selectedDate.value) ?? {} : {})
const timeString = computed(() => `${String(pendingTime.value.hour).padStart(2, '0')}:${String(pendingTime.value.minute).padStart(2, '0')}:${String(pendingTime.value.second).padStart(2, '0')}`)

function commit(withOk: boolean) {
  const next = pickerSingleBinding(pending.value ?? committedValue.value, bindingFormat.value, withTime.value ? pendingTime.value : undefined) as string | number | undefined
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
  if (!withTime.value) {
    commit(false)
  }
}

function confirmPending() {
  if (!pending.value) {
    return
  }
  commit(true)
  pending.value = undefined
  open.value = false
}

function onTime(next: string) {
  pendingTime.value = pickerParseTime(next, pendingTime.value)
}

function clear() {
  value.value = undefined
  pending.value = undefined
  emit('change', undefined)
  emit('clear')
}

function commitTyped(text: string) {
  const input = text.trim()
  if (!input) {
    clear()
    return
  }
  const parsed = parsePickerDayjs(input, props.format || displayFormat.value, props.mode)
  if (!parsed || props.disabledDate?.(parsed.toDate()) === true) {
    return
  }
  if (withTime.value && timeTokens.value.hour) {
    pendingTime.value = { hour: parsed.hour(), minute: parsed.minute(), second: parsed.second() }
  }
  pending.value = pickerDateValue(parsed, withTime.value)
  if (!withTime.value) {
    commit(false)
    pending.value = undefined
  }
}

watch(open, (visible) => {
  if (visible) {
    const committed = committedValue.value
    const hasCommittedTime = !!committed && !Array.isArray(committed) && !('start' in committed) && 'hour' in committed
    if (hasCommittedTime) {
      pendingTime.value = { hour: (committed as { hour: number }).hour, minute: (committed as { minute: number }).minute, second: (committed as { second: number }).second }
    }
    else if (withTime.value) {
      pendingTime.value = pickerNowTime()
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
      :text="displayText"
      :placeholder="placeholder ?? '请选择日期'"
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
              <span class="yd-picker-time-label">时间</span>
              <TimeColumns
                :model-value="timeString"
                :format="timeFormat"
                :disabled-hours="disabledTime.disabledHours"
                :disabled-minutes="disabledTime.disabledMinutes"
                :disabled-seconds="disabledTime.disabledSeconds"
                :disabled="disabled || readonly"
                @update:model-value="onTime"
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
