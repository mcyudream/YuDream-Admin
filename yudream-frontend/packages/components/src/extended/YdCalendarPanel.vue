<script setup lang="ts">
import type { DateValue } from '@internationalized/date'
import type { Component } from 'vue'
import type { YdCalendarMode, YdCalendarProps, YdCalendarValue, YdCalendarWeekStart } from './calendar'
import { getLocalTimeZone, parseTime, toCalendarDate, toCalendarDateTime, today } from '@internationalized/date'
import * as Reka from 'reka-ui'
import { computed, getCurrentInstance, ref, shallowRef, watch } from 'vue'
import FaButton from '../basic/button/index.vue'
import { calendarDateBlocked, calendarLabel, calendarPeriod, calendarQuarterStart, calendarTime, calendarValueAllowed, calendarWeekEnd, calendarWeekStart, firstCalendarDate, isDateRange, normalizeCalendarTime, normalizeCalendarValue } from './calendar'
import CalendarFrame from './CalendarFrame.vue'
import './extended.css'

const props = withDefaults(defineProps<YdCalendarProps & { mode?: YdCalendarMode, range?: boolean, drillDown?: boolean, withTime?: boolean, defaultTime?: string, modelControlled?: boolean, surface?: boolean, shortcutsPosition?: 'left' | 'bottom' | 'right' | 'top' }>(), {
  mode: 'date',
  locale: 'zh-CN',
  weekStartsOn: 1,
  fixedWeeks: true,
  defaultTime: '00:00:00',
  surface: true,
  shortcutsPosition: 'top',
  disabled: undefined,
  readonly: undefined,
  multiple: undefined,
  preventDeselect: undefined,
  pagedNavigation: undefined,
  allowNonContiguousRanges: undefined,
  picker: undefined,
  open: undefined,
  closeOnSelect: undefined,
  modelControlled: undefined,
})
const emit = defineEmits<{ 'update:modelValue': [value: YdCalendarValue], 'update:placeholder': [value: DateValue], 'update:open': [value: boolean], 'change': [value: YdCalendarValue], 'complete': [value: YdCalendarValue] }>()
const instance = getCurrentInstance()
const controlled = computed(() => props.modelControlled ?? !!(instance?.vnode.props && ('modelValue' in instance.vnode.props || 'model-value' in instance.vnode.props)))
const internalValue = shallowRef<YdCalendarValue>(props.modelValue)
const value = computed(() => controlled.value ? props.modelValue : internalValue.value)
const internalAnchor = shallowRef<DateValue>(props.placeholder || firstCalendarDate(value.value) || props.defaultPlaceholder || today(getLocalTimeZone()))
const anchor = computed(() => props.placeholder || internalAnchor.value)
const internalOpen = ref(false)
const open = computed({ get: () => !props.disabled && (props.open ?? internalOpen.value), set: (next: boolean) => {
  const updated = !props.disabled && next
  if (props.open === undefined) {
    internalOpen.value = updated
  }
  emit('update:open', updated)
} })
const view = ref(props.mode)
const rootRevision = ref(0)
const time = ref(calendarTime(value.value, props.defaultTime))
watch(() => props.modelValue, (next) => {
  internalValue.value = next
  const selected = firstCalendarDate(next)
  if (selected && !props.placeholder) {
    internalAnchor.value = selected
  }
  time.value = calendarTime(next, props.defaultTime)
})
watch(() => props.placeholder, (next) => {
  if (next) {
    internalAnchor.value = next
  }
})
watch(() => props.defaultTime, () => {
  time.value = calendarTime(value.value, props.defaultTime)
})
watch(() => props.mode, (v) => {
  view.value = v
})
watch(() => props.disabled, (disabled) => {
  if (disabled && (internalOpen.value || props.open)) {
    open.value = false
  }
})
const multiple = computed(() => !props.range && (props.multiple ?? Array.isArray(value.value)))
const weekStartsOn = computed<YdCalendarWeekStart>(() => props.weekStartsOn ?? 1)
const selectingPeriod = computed(() => props.drillDown && view.value !== 'date' && view.value !== 'week')
const family = computed(() => view.value === 'date' || view.value === 'week' ? (props.range ? 'RangeCalendar' : 'Calendar') : view.value === 'month' ? (props.range && !selectingPeriod.value ? 'MonthRangePicker' : 'MonthPicker') : (props.range && !selectingPeriod.value ? 'YearRangePicker' : 'YearPicker'))
function primitive(suffix: string): Component {
  return (Reka as unknown as Record<string, Component>)[family.value + suffix]
}
const rootProps = computed(() => {
  const selectedValue = selectingPeriod.value
    ? anchor.value
    : props.range
      ? isDateRange(value.value) ? value.value : undefined
      : isDateRange(value.value)
        ? undefined
        : multiple.value
          ? value.value && !Array.isArray(value.value) ? [value.value] : value.value
          : Array.isArray(value.value) ? value.value[0] : value.value
  const common = {
    modelValue: selectedValue ?? null,
    placeholder: anchor.value,
    locale: props.locale,
    dir: props.dir,
    minValue: props.minValue,
    maxValue: props.maxValue,
    disabled: props.disabled,
    readonly: props.readonly && !selectingPeriod.value,
    initialFocus: !!props.picker && open.value,
    preventDeselect: props.preventDeselect ?? !multiple.value,
    ...(!props.range || selectingPeriod.value ? { multiple: !selectingPeriod.value && multiple.value } : { allowNonContiguousRanges: props.allowNonContiguousRanges }),
  }
  // Read current props inside callbacks: the date family's state retains its initial matcher.
  if (view.value === 'date' || view.value === 'week') {
    return { ...common, weekStartsOn: props.weekStartsOn, fixedWeeks: props.fixedWeeks, numberOfMonths: props.numberOfMonths ?? (props.range ? 2 : 1), pagedNavigation: props.pagedNavigation, isDateDisabled: (date: DateValue) => calendarDateBlocked(date, view.value, props, false), isDateUnavailable: (date: DateValue) => !!props.isDateUnavailable?.(date), ...(props.range ? { maximumDays: props.maximumDays } : {}) }
  }
  if (view.value === 'month') {
    return { ...common, isMonthDisabled: (date: DateValue) => calendarDateBlocked(date, 'month', props, false), isMonthUnavailable: (date: DateValue) => !!props.isMonthUnavailable?.(date), ...(props.range && !selectingPeriod.value ? { maximumMonths: props.maximumMonths } : {}) }
  }
  return { ...common, yearsPerPage: props.yearsPerPage, isYearDisabled: (date: DateValue) => calendarDateBlocked(date, 'year', props, false), isYearUnavailable: (date: DateValue) => !!props.isYearUnavailable?.(date), ...(props.range && !selectingPeriod.value ? { maximumYears: props.maximumYears } : {}) }
})
function updateAnchor(date: DateValue) {
  internalAnchor.value = date
  emit('update:placeholder', date)
}
function update(next: YdCalendarValue): boolean {
  if (props.disabled) {
    return false
  }
  if (selectingPeriod.value && next && !Array.isArray(next) && !isDateRange(next)) {
    if (!calendarValueAllowed(next, view.value, { ...props, readonly: false })) {
      return false
    }
    updateAnchor(next)
    view.value = view.value === 'year' ? 'month' : 'date'
    return true
  }
  if (props.readonly) {
    return false
  }
  next = normalizeCalendarValue(next, props.mode, weekStartsOn.value)
  if (!calendarValueAllowed(next, props.mode, props, props.range)) {
    // Range primitives keep internal endpoints. Reset them when a guarded change is rejected.
    rootRevision.value++
    return false
  }
  if (next && props.withTime && !multiple.value && !isDateRange(next) && !Array.isArray(next)) {
    time.value = normalizeCalendarTime(time.value, props.defaultTime)
    next = toCalendarDateTime(next, parseTime(time.value))
    if ((props.minValue && 'hour' in props.minValue && next.compare(props.minValue) < 0)
      || (props.maxValue && 'hour' in props.maxValue && next.compare(props.maxValue) > 0)) {
      rootRevision.value++
      return false
    }
  }
  if (!controlled.value) {
    internalValue.value = next
  }
  emit('update:modelValue', next)
  emit('change', next)
  const complete = !!next && (props.range ? isDateRange(next) && next.start && next.end : !Array.isArray(next) || next.length > 0)
  if (complete) {
    emit('complete', next)
    if (props.closeOnSelect ?? (!multiple.value && !props.withTime)) {
      open.value = false
    }
  }
  return true
}
function shortcut(next: YdCalendarValue) {
  next = normalizeCalendarValue(next, props.mode, weekStartsOn.value)
  if (!calendarValueAllowed(next, props.mode, props, props.range)) {
    return
  }
  view.value = props.mode
  if (!update(next)) {
    return
  }
  const selected = firstCalendarDate(next)
  if (selected) {
    updateAnchor(selected)
  }
}
function updateTime(event: Event) {
  const previousTime = time.value
  time.value = normalizeCalendarTime((event.target as HTMLInputElement).value, props.defaultTime)
  if (!update(value.value)) {
    time.value = previousTime
  }
}
function grids(grid: { value: DateValue, rows: DateValue[][] } | { value: DateValue, rows: DateValue[][] }[]) {
  return Array.isArray(grid) ? grid : [grid]
}
function cellProps(date: DateValue, month: DateValue) {
  return view.value === 'date' || view.value === 'week' ? { day: date, month } : view.value === 'month' ? { month: date } : { year: date }
}

/** Highlight the whole week row once a week (or week range) is selected. */
function inWeekHighlight(date: DateValue): boolean {
  if (view.value !== 'week' || multiple.value) {
    return false
  }
  const day = toCalendarDate(date)
  const current = value.value
  if (props.range) {
    if (!isDateRange(current) || !current.start) {
      return false
    }
    const first = calendarWeekStart(current.start, weekStartsOn.value)
    const last = current.end ? calendarWeekEnd(current.end, weekStartsOn.value) : calendarWeekEnd(current.start, weekStartsOn.value)
    return day.compare(first) >= 0 && day.compare(last) <= 0
  }
  if (Array.isArray(current) || isDateRange(current) || !current) {
    return false
  }
  return day.compare(calendarWeekStart(current, weekStartsOn.value)) >= 0 && day.compare(calendarWeekEnd(current, weekStartsOn.value)) <= 0
}

const quarterYear = computed(() => toCalendarDate(anchor.value).year)
const quarterPending = shallowRef<DateValue | undefined>()
watch([() => props.modelValue, () => props.mode, () => open.value], () => {
  quarterPending.value = undefined
})
function quarterPeriods(): DateValue[] {
  const year = toCalendarDate(anchor.value).year
  return [1, 2, 3, 4].map(index => calendarQuarterStart(toCalendarDate(anchor.value).set({ year, month: (index - 1) * 3 + 1, day: 1 })))
}
function quarterSelected(period: DateValue): boolean {
  const current = value.value
  const candidates = isDateRange(current) ? [current.start, current.end] : Array.isArray(current) ? current : current ? [current] : []
  return [...candidates, quarterPending.value].some(item => !!item && calendarPeriod(item, 'quarter', weekStartsOn.value).compare(period) === 0)
}
function quarterBlocked(period: DateValue): boolean {
  return calendarDateBlocked(period, 'quarter', props, false)
}
function selectQuarter(period: DateValue) {
  if (props.disabled || props.readonly || quarterBlocked(period)) {
    return
  }
  if (!props.range) {
    update(period)
    return
  }
  const start = quarterPending.value ?? (isDateRange(value.value) ? value.value.start : undefined)
  if (!start) {
    quarterPending.value = period
    updateAnchor(period)
    return
  }
  const ordered = start.compare(period) <= 0 ? { start, end: period } : { start: period, end: start }
  quarterPending.value = undefined
  if (!update(ordered)) {
    quarterPending.value = period
  }
  updateAnchor(period)
}
function shiftQuarterYear(offset: number) {
  updateAnchor(toCalendarDate(anchor.value).set({ year: toCalendarDate(anchor.value).year + offset, month: 1, day: 1 }))
}
</script>

<template>
  <CalendarFrame v-model:open="open" :picker="picker" :disabled="disabled" :label="formatValue?.(value) || calendarLabel(value) || pickerPlaceholder || '选择日期'">
    <div class="yd-calendar" :class="[{ 'yd-surface': surface }, shortcutsPosition !== 'top' && `yd-calendar--shortcuts-${shortcutsPosition}`]">
      <div v-if="shortcuts?.length" class="yd-calendar-shortcuts">
        <FaButton v-for="item in shortcuts" :key="item.text" type="button" variant="outline" size="sm" :disabled="disabled || readonly || item.disabled" @click="shortcut(typeof item.value === 'function' ? item.value() : item.value)">
          {{ item.text }}
        </FaButton>
      </div>
      <component :is="primitive('Root')" v-if="view !== 'quarter'" :key="`${family}-${rootRevision}`" v-bind="rootProps" v-slot="{ grid, weekDays, date }" @update:model-value="update" @update:placeholder="updateAnchor">
        <component :is="primitive('Header')" class="yd-calendar-nav">
          <component :is="primitive('Prev')" aria-label="上一页">
            ‹
          </component>
          <FaButton v-if="drillDown" type="button" variant="ghost" size="sm" :disabled="disabled" @click="view = view === 'date' ? 'month' : 'year'">
            {{ view === 'date' ? `${date.year}年${date.month}月` : `${date.year}年` }}
          </FaButton>
          <slot v-else name="heading" :date="date">
            <component :is="primitive('Heading')" />
          </slot>
          <component :is="primitive('Next')" aria-label="下一页">
            ›
          </component>
        </component>
        <div class="yd-calendar-months">
          <component :is="primitive('Grid')" v-for="month in grids(grid)" :key="month.value.toString()">
            <component :is="primitive('GridHead')" v-if="view === 'date' || view === 'week'">
              <component :is="primitive('GridRow')">
                <component :is="primitive('HeadCell')" v-for="day in weekDays" :key="day">
                  {{ day }}
                </component>
              </component>
            </component>
            <component :is="primitive('GridBody')">
              <component :is="primitive('GridRow')" v-for="(row, index) in month.rows" :key="index">
                <component :is="primitive('Cell')" v-for="day in row" :key="day.toString()" :date="day">
                  <component :is="primitive('CellTrigger')" v-bind="cellProps(day, month.value)" class="yd-calendar-cell" :class="{ 'yd-calendar-cell--week': inWeekHighlight(day) }" />
                </component>
              </component>
            </component>
          </component>
        </div>
      </component>
      <div v-else class="yd-calendar-quarters">
        <div class="yd-calendar-nav">
          <button type="button" aria-label="上一年" :disabled="disabled" @click="shiftQuarterYear(-1)">
            ‹
          </button>
          <span class="yd-calendar-heading">{{ quarterYear }}年</span>
          <button type="button" aria-label="下一年" :disabled="disabled" @click="shiftQuarterYear(1)">
            ›
          </button>
        </div>
        <div class="yd-calendar-quarter-grid">
          <button v-for="period in quarterPeriods()" :key="period.toString()" type="button" class="yd-calendar-cell yd-calendar-quarter" :class="{ 'yd-calendar-quarter--selected': quarterSelected(period) }" :disabled="disabled || quarterBlocked(period)" @click="selectQuarter(period)">
            {{ Math.floor((period.month - 1) / 3) + 1 }}季度
          </button>
        </div>
      </div>
      <label v-if="withTime && !multiple && !range" class="yd-row yd-muted">时间<input :value="time" type="time" step="1" class="yd-control" :disabled="disabled || readonly || !value" @change="updateTime"></label>
    </div>
  </CalendarFrame>
</template>
