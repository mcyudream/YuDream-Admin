import type { DateValue } from '@internationalized/date'
import { endOfMonth, endOfYear, parseTime, startOfMonth, startOfYear, toCalendarDate } from '@internationalized/date'

export type YdCalendarMode = 'date' | 'week' | 'month' | 'quarter' | 'year'
export type YdCalendarWeekStart = 0 | 1 | 2 | 3 | 4 | 5 | 6

export interface YdDateRange { start: DateValue | undefined, end: DateValue | undefined }
export type YdCalendarValue = DateValue | DateValue[] | YdDateRange | undefined
export interface YdCalendarShortcut { text: string, value: YdCalendarValue | (() => YdCalendarValue), disabled?: boolean }
export interface YdCalendarProps {
  modelValue?: YdCalendarValue
  placeholder?: DateValue
  defaultPlaceholder?: DateValue
  minValue?: DateValue
  maxValue?: DateValue
  disabled?: boolean
  readonly?: boolean
  multiple?: boolean
  locale?: string
  dir?: 'ltr' | 'rtl'
  weekStartsOn?: 0 | 1 | 2 | 3 | 4 | 5 | 6
  fixedWeeks?: boolean
  numberOfMonths?: number
  yearsPerPage?: number
  preventDeselect?: boolean
  pagedNavigation?: boolean
  isDateDisabled?: (date: DateValue) => boolean
  isDateUnavailable?: (date: DateValue) => boolean
  isMonthDisabled?: (date: DateValue) => boolean
  isMonthUnavailable?: (date: DateValue) => boolean
  isQuarterDisabled?: (date: DateValue) => boolean
  isQuarterUnavailable?: (date: DateValue) => boolean
  isYearDisabled?: (date: DateValue) => boolean
  isYearUnavailable?: (date: DateValue) => boolean
  allowNonContiguousRanges?: boolean
  maximumDays?: number
  maximumMonths?: number
  maximumYears?: number
  picker?: boolean
  open?: boolean
  pickerPlaceholder?: string
  closeOnSelect?: boolean
  shortcuts?: YdCalendarShortcut[]
  formatValue?: (value: YdCalendarValue) => string
}

export function isDateRange(value: YdCalendarValue): value is YdDateRange {
  return !!value && !Array.isArray(value) && 'start' in value
}

export function calendarLabel(value: YdCalendarValue): string {
  if (!value) {
    return ''
  }
  if (Array.isArray(value)) {
    return value.length > 1 ? `已选 ${value.length} 项` : value[0]?.toString() || ''
  }
  if (isDateRange(value)) {
    return value.start || value.end ? `${value.start || '…'} 至 ${value.end || '…'}` : ''
  }
  return value.toString()
}

/** Sunday-based weekday index (0 = 周日), derived from the calendar's Julian day. */
export function calendarWeekDay(date: DateValue): number {
  const day = toCalendarDate(date)
  return ((day.calendar.toJulianDay(day) % 7) + 1) % 7
}

export function calendarWeekStart(date: DateValue, weekStartsOn: YdCalendarWeekStart = 1): DateValue {
  const day = toCalendarDate(date)
  const offset = (calendarWeekDay(day) - weekStartsOn + 7) % 7
  return offset ? day.subtract({ days: offset }) : day
}

export function calendarWeekEnd(date: DateValue, weekStartsOn: YdCalendarWeekStart = 1): DateValue {
  return calendarWeekStart(date, weekStartsOn).add({ days: 6 })
}

export function calendarQuarterStart(date: DateValue): DateValue {
  const day = toCalendarDate(date)
  return day.set({ month: Math.floor((day.month - 1) / 3) * 3 + 1, day: 1 })
}

/** Return the period represented by a cell, independently of a stored time. */
export function calendarPeriod(date: DateValue, mode: YdCalendarMode, weekStartsOn: YdCalendarWeekStart = 1): DateValue {
  const day = toCalendarDate(date)
  return mode === 'week'
    ? calendarWeekStart(day, weekStartsOn)
    : mode === 'quarter'
      ? calendarQuarterStart(day)
      : mode === 'month'
        ? startOfMonth(day)
        : mode === 'year'
          ? startOfYear(day)
          : day
}

function calendarPeriodEnd(date: DateValue, mode: YdCalendarMode, weekStartsOn: YdCalendarWeekStart = 1): DateValue {
  const first = calendarPeriod(date, mode, weekStartsOn)
  return mode === 'week'
    ? first.add({ days: 6 })
    : mode === 'quarter'
      ? endOfMonth(first.add({ months: 2 }))
      : mode === 'month'
        ? endOfMonth(first)
        : mode === 'year'
          ? endOfYear(first)
          : first
}

export function firstCalendarDate(value: YdCalendarValue): DateValue | undefined {
  return Array.isArray(value) ? value[0] : isDateRange(value) ? value.start || value.end : value
}

export function normalizeCalendarValue(value: YdCalendarValue, mode: YdCalendarMode, weekStartsOn: YdCalendarWeekStart = 1): YdCalendarValue {
  if (mode === 'date' || !value) {
    return value
  }
  if (Array.isArray(value)) {
    return value.map(date => calendarPeriod(date, mode, weekStartsOn))
  }
  if (isDateRange(value)) {
    return { start: value.start && calendarPeriod(value.start, mode, weekStartsOn), end: value.end && calendarPeriod(value.end, mode, weekStartsOn) }
  }
  return calendarPeriod(value, mode, weekStartsOn)
}

export function normalizeCalendarTime(time: string | undefined, fallback = '00:00:00'): string {
  try {
    return parseTime(time || fallback).toString()
  }
  catch {
    try {
      return parseTime(fallback).toString()
    }
    catch {
      return '00:00:00'
    }
  }
}

export function calendarTime(value: YdCalendarValue, fallback?: string): string {
  if (value && !Array.isArray(value) && !isDateRange(value) && 'hour' in value) {
    return normalizeCalendarTime(`${String(value.hour).padStart(2, '0')}:${String(value.minute).padStart(2, '0')}:${String(value.second).padStart(2, '0')}`)
  }
  return normalizeCalendarTime(fallback)
}

function calendarDayCount(start: DateValue, end: DateValue): number {
  return end.calendar.toJulianDay(end) - start.calendar.toJulianDay(start)
}

/** Number of periods (days, weeks, months, quarters or years) the range covers. */
export function calendarRangeSize(start: DateValue, end: DateValue, mode: YdCalendarMode, weekStartsOn: YdCalendarWeekStart = 1): number {
  const cursor = calendarPeriod(start, mode, weekStartsOn)
  const last = calendarPeriod(end, mode, weekStartsOn)
  if (cursor.compare(last) > 0) {
    return 0
  }
  if (mode === 'date') {
    return calendarDayCount(cursor, last) + 1
  }
  if (mode === 'week') {
    return Math.floor(calendarDayCount(cursor, last) / 7) + 1
  }
  let size = 1
  let current = cursor
  const step = mode === 'quarter' ? { months: 3 } : mode === 'month' ? { months: 1 } : { years: 1 }
  while (current.compare(last) < 0) {
    current = current.add(step)
    size++
  }
  return size
}

function calendarPeriodStep(mode: YdCalendarMode): { days?: number, months?: number, years?: number } {
  return mode === 'date' ? { days: 1 } : mode === 'week' ? { days: 7 } : mode === 'month' ? { months: 1 } : mode === 'quarter' ? { months: 3 } : { years: 1 }
}

function calendarDisabledMatcher(mode: YdCalendarMode, props: YdCalendarProps) {
  if (mode === 'date' || mode === 'week') {
    return { disabled: props.isDateDisabled, unavailable: props.isDateUnavailable }
  }
  if (mode === 'month') {
    return { disabled: props.isMonthDisabled, unavailable: props.isMonthUnavailable }
  }
  if (mode === 'quarter') {
    return { disabled: props.isQuarterDisabled, unavailable: props.isQuarterUnavailable }
  }
  return { disabled: props.isYearDisabled, unavailable: props.isYearUnavailable }
}

export function calendarDateBlocked(date: DateValue, mode: YdCalendarMode, props: YdCalendarProps, includeUnavailable = true): boolean {
  const first = calendarPeriod(date, mode, props.weekStartsOn)
  const last = calendarPeriodEnd(date, mode, props.weekStartsOn)
  if ((props.minValue && last.compare(toCalendarDate(props.minValue)) < 0)
    || (props.maxValue && first.compare(toCalendarDate(props.maxValue)) > 0)) {
    return true
  }
  const { disabled, unavailable } = calendarDisabledMatcher(mode, props)
  return !!(disabled?.(first) || (includeUnavailable && unavailable?.(first)))
}

/** Apply the same rules to clicked cells, keyboard selections and shortcuts. Limits are inclusive. */
export function calendarValueAllowed(value: YdCalendarValue, mode: YdCalendarMode, props: YdCalendarProps, range = false): boolean {
  if (props.disabled || props.readonly) {
    return false
  }
  if (!value) {
    return true
  }
  if (isDateRange(value)) {
    if (!range) {
      return false
    }
    if ((value.start && calendarDateBlocked(value.start, mode, props)) || (value.end && calendarDateBlocked(value.end, mode, props))) {
      return false
    }
    if (!value.start || !value.end) {
      return true
    }
    const first = calendarPeriod(value.start, mode, props.weekStartsOn)
    const last = calendarPeriod(value.end, mode, props.weekStartsOn)
    if (first.compare(last) > 0) {
      return false
    }
    const maximum = mode === 'date' || mode === 'week' ? props.maximumDays : mode === 'month' ? props.maximumMonths : mode === 'quarter' ? undefined : props.maximumYears
    if (maximum !== undefined && (!Number.isFinite(maximum) || maximum < 1 || calendarRangeSize(first, last, mode, props.weekStartsOn) > Math.floor(maximum))) {
      return false
    }
    const { disabled, unavailable } = calendarDisabledMatcher(mode, props)
    if (!disabled && (!unavailable || props.allowNonContiguousRanges)) {
      return true
    }
    const step = calendarPeriodStep(mode)
    let cursor = first
    while (cursor.compare(last) <= 0) {
      if (calendarDateBlocked(cursor, mode, props, !props.allowNonContiguousRanges)) {
        return false
      }
      cursor = cursor.add(step)
    }
    return true
  }
  if (range) {
    return false
  }
  return (Array.isArray(value) ? value : [value]).every(date => !calendarDateBlocked(date, mode, props))
}
