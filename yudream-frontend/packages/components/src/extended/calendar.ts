import type { DateValue } from '@internationalized/date'
import { endOfMonth, endOfYear, parseTime, startOfMonth, startOfYear, toCalendarDate } from '@internationalized/date'

export type YdCalendarMode = 'date' | 'month' | 'year'

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

/** Return the period represented by a cell, independently of a stored time. */
export function calendarPeriod(date: DateValue, mode: YdCalendarMode): DateValue {
  const day = toCalendarDate(date)
  return mode === 'month' ? startOfMonth(day) : mode === 'year' ? startOfYear(day) : day
}

export function firstCalendarDate(value: YdCalendarValue): DateValue | undefined {
  return Array.isArray(value) ? value[0] : isDateRange(value) ? value.start || value.end : value
}

export function normalizeCalendarValue(value: YdCalendarValue, mode: YdCalendarMode): YdCalendarValue {
  if (mode === 'date' || !value) {
    return value
  }
  if (Array.isArray(value)) {
    return value.map(date => calendarPeriod(date, mode))
  }
  if (isDateRange(value)) {
    return { start: value.start && calendarPeriod(value.start, mode), end: value.end && calendarPeriod(value.end, mode) }
  }
  return calendarPeriod(value, mode)
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

export function calendarRangeSize(start: DateValue, end: DateValue, mode: YdCalendarMode): number {
  let cursor = calendarPeriod(start, mode)
  const last = calendarPeriod(end, mode)
  if (cursor.compare(last) > 0) {
    return 0
  }
  if (mode === 'date') {
    return last.calendar.toJulianDay(last) - cursor.calendar.toJulianDay(cursor) + 1
  }
  let size = 1
  while (cursor.compare(last) < 0) {
    cursor = cursor.add(mode === 'month' ? { months: 1 } : { years: 1 })
    size++
  }
  return size
}

export function calendarDateBlocked(date: DateValue, mode: YdCalendarMode, props: YdCalendarProps, includeUnavailable = true): boolean {
  const first = calendarPeriod(date, mode)
  const last = mode === 'month' ? endOfMonth(first) : mode === 'year' ? endOfYear(first) : first
  if ((props.minValue && last.compare(toCalendarDate(props.minValue)) < 0)
    || (props.maxValue && first.compare(toCalendarDate(props.maxValue)) > 0)) {
    return true
  }
  const disabled = mode === 'date' ? props.isDateDisabled : mode === 'month' ? props.isMonthDisabled : props.isYearDisabled
  const unavailable = mode === 'date' ? props.isDateUnavailable : mode === 'month' ? props.isMonthUnavailable : props.isYearUnavailable
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
    const first = calendarPeriod(value.start, mode)
    const last = calendarPeriod(value.end, mode)
    if (first.compare(last) > 0) {
      return false
    }
    const maximum = mode === 'date' ? props.maximumDays : mode === 'month' ? props.maximumMonths : props.maximumYears
    if (maximum !== undefined && (!Number.isFinite(maximum) || maximum < 1 || calendarRangeSize(first, last, mode) > Math.floor(maximum))) {
      return false
    }
    const disabled = mode === 'date' ? props.isDateDisabled : mode === 'month' ? props.isMonthDisabled : props.isYearDisabled
    const unavailable = mode === 'date' ? props.isDateUnavailable : mode === 'month' ? props.isMonthUnavailable : props.isYearUnavailable
    if (!disabled && (!unavailable || props.allowNonContiguousRanges)) {
      return true
    }
    let cursor = first
    while (cursor.compare(last) <= 0) {
      if (calendarDateBlocked(cursor, mode, props, !props.allowNonContiguousRanges)) {
        return false
      }
      cursor = cursor.add(mode === 'date' ? { days: 1 } : mode === 'month' ? { months: 1 } : { years: 1 })
    }
    return true
  }
  if (range) {
    return false
  }
  return (Array.isArray(value) ? value : [value]).every(date => !calendarDateBlocked(date, mode, props))
}
