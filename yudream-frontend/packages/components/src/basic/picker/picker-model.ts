import type { DateValue } from '@internationalized/date'
import type { Dayjs } from 'dayjs'
import type { YdCalendarShortcut, YdCalendarValue, YdCalendarWeekStart } from '../../extended/calendar'
import { CalendarDate, CalendarDateTime, endOfMonth, endOfYear, startOfYear, toCalendarDate } from '@internationalized/date'
import dayjs from 'dayjs'
import advancedFormat from 'dayjs/plugin/advancedFormat'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import isBetween from 'dayjs/plugin/isBetween'
import quarterOfYear from 'dayjs/plugin/quarterOfYear'
import weekOfYear from 'dayjs/plugin/weekOfYear'
import weekYear from 'dayjs/plugin/weekYear'
import { calendarQuarterStart, calendarWeekStart } from '../../extended/calendar'
import 'dayjs/locale/zh-cn'

// 与 Arco 日期组件保持一致：同一套 dayjs 插件与中文 locale，保证既有 format/valueFormat 可用。
dayjs.extend(customParseFormat)
dayjs.extend(isBetween)
dayjs.extend(weekOfYear)
dayjs.extend(advancedFormat)
dayjs.extend(weekYear)
dayjs.extend(quarterOfYear)
dayjs.locale('zh-cn')

export type YdPickerMode = 'date' | 'week' | 'month' | 'quarter' | 'year'
export type YdPickerValueFormat = string
export type YdPickerBindingValue = string | number | Date

export interface YdPickerTimePart {
  hour: number
  minute: number
  second: number
}

export interface YdPickerShortcut {
  label: string | number
  value: YdPickerBindingValue | YdPickerBindingValue[] | (() => YdPickerBindingValue | YdPickerBindingValue[])
  disabled?: boolean
  format?: string
}

export interface YdPickerDisabledTime {
  disabledHours?: () => number[]
  disabledMinutes?: (hour?: number) => number[]
  disabledSeconds?: (hour?: number, minute?: number) => number[]
}

export interface YdPickerRow {
  value: DateValue
  label: string
}

export function pickerDefaultDisplayFormat(mode: YdPickerMode, showTime = false): string {
  switch (mode) {
    case 'week': return 'gggg-wo'
    case 'month': return 'YYYY-MM'
    case 'quarter': return 'YYYY-[Q]Q'
    case 'year': return 'YYYY'
    default: return showTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'
  }
}

export function pickerDefaultValueFormat(mode: YdPickerMode, showTime = false): string {
  switch (mode) {
    case 'week': return 'YYYY-MM-DD'
    case 'month': return 'YYYY-MM'
    case 'quarter': return 'YYYY-MM'
    case 'year': return 'YYYY'
    default: return showTime ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'
  }
}

function pickerFormatTokens(format: string): string[] {
  return (format.match(/\[[^\]]*\]|H{1,2}|h{1,2}|m{1,2}|s{1,2}|A{1,2}|a{1,2}/g) ?? []).filter(token => !token.startsWith('['))
}

export function pickerClockTokens(format: string): { hour: boolean, minute: boolean, second: boolean, period: boolean } {
  const tokens = pickerFormatTokens(format)
  return {
    hour: tokens.some(token => /^H/i.test(token)),
    minute: tokens.some(token => token.startsWith('m')),
    second: tokens.some(token => token.startsWith('s')),
    period: tokens.some(token => /^A/i.test(token)),
  }
}

/** 时间部分格式，决定日期选择器内置时间列的粒度。 */
export function pickerTimeFormat(displayFormat: string, fallback = 'HH:mm:ss'): string {
  const tokens = pickerFormatTokens(displayFormat)
  const period = tokens.find(token => /^A/i.test(token))
  const clock = tokens.filter(token => /^[Hhms]/.test(token))
  if (!clock.length) {
    return fallback
  }
  const time = clock.join(':')
  return period ? `${time} ${period}` : time
}

function pickerWeekDayjs(value: string, valueFormat: string, mode: YdPickerMode): Dayjs | undefined {
  if (mode !== 'week' || !/w/i.test(valueFormat)) {
    return undefined
  }
  const numbers = value.match(/\d+/g) ?? []
  if (numbers.length < 2) {
    return undefined
  }
  const year = Number(numbers[0])
  const week = Number(numbers[1])
  if (!Number.isFinite(year) || !Number.isFinite(week)) {
    return undefined
  }
  const anchor = dayjs(`${year}-01-04`).week(week).startOf('week')
  return anchor.isValid() ? anchor : undefined
}

/** 绑定值 → dayjs。`timestamp` / `Date` 直读，其余按 valueFormat 严格解析后放宽，最后回退原生解析与周格式解析。 */
export function parsePickerDayjs(value: unknown, valueFormat: YdPickerValueFormat, mode: YdPickerMode = 'date'): Dayjs | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined
  }
  if (valueFormat === 'Date') {
    return value instanceof Date ? dayjs(value) : undefined
  }
  if (valueFormat === 'timestamp') {
    const stamp = typeof value === 'number' ? value : Number(value)
    return Number.isFinite(stamp) ? dayjs(stamp) : undefined
  }
  if (typeof value === 'number') {
    return dayjs(value)
  }
  if (value instanceof Date) {
    return dayjs(value)
  }
  if (typeof value !== 'string') {
    return undefined
  }
  const strict = dayjs(value, valueFormat, true)
  if (strict.isValid()) {
    return strict
  }
  // 周格式无法用 dayjs 解析；先按年+周号换算，避免宽松解析把周号误当日期。
  const weekly = pickerWeekDayjs(value, valueFormat, mode)
  if (weekly) {
    return weekly
  }
  const loose = dayjs(value, valueFormat)
  if (loose.isValid()) {
    return loose
  }
  const raw = dayjs(value)
  return raw.isValid() ? raw : undefined
}

export function formatPickerDayjs(date: Dayjs, valueFormat: YdPickerValueFormat): YdPickerBindingValue {
  if (valueFormat === 'Date') {
    return date.toDate()
  }
  if (valueFormat === 'timestamp') {
    return date.valueOf()
  }
  return date.format(valueFormat)
}

export function pickerDateValue(date: Dayjs, withTime: boolean): DateValue {
  return withTime
    ? new CalendarDateTime(date.year(), date.month() + 1, date.date(), date.hour(), date.minute(), date.second())
    : new CalendarDate(date.year(), date.month() + 1, date.date())
}

export function pickerDayjs(value: DateValue): Dayjs {
  const day = toCalendarDate(value)
  const hour = 'hour' in value ? value.hour : 0
  const minute = 'hour' in value ? value.minute : 0
  const second = 'hour' in value ? value.second : 0
  return dayjs(new Date(day.year, day.month - 1, day.day, hour, minute, second))
}

export function pickerJsDate(value: DateValue): Date {
  return pickerDayjs(value).toDate()
}

export function pickerNowTime(): YdPickerTimePart {
  const now = dayjs()
  return { hour: now.hour(), minute: now.minute(), second: now.second() }
}

export function pickerParseTime(value: string | undefined | null, fallback?: YdPickerTimePart): YdPickerTimePart {
  const parts = typeof value === 'string' ? value.split(':').map(Number) : []
  const hour = Number.isFinite(parts[0]) ? parts[0] : undefined
  const minute = Number.isFinite(parts[1]) ? parts[1] : undefined
  const second = Number.isFinite(parts[2]) ? parts[2] : undefined
  const base = fallback ?? { hour: 0, minute: 0, second: 0 }
  return {
    hour: hour ?? base.hour,
    minute: minute ?? base.minute,
    second: second ?? base.second,
  }
}

export function pickerTimeWithDay(day: Dayjs, time: YdPickerTimePart | undefined): Dayjs {
  return time ? day.hour(time.hour).minute(time.minute).second(time.second) : day
}

/** 绑定值 → 日历面板值；无法解析的字符串保持空，避免面板锚点跳回今天。 */
export function pickerCalendarValue(value: unknown, valueFormat: YdPickerValueFormat, options: { mode: YdPickerMode, range?: boolean, withTime?: boolean }): YdCalendarValue {
  const withTime = options.withTime ?? false
  if (options.range) {
    const list = Array.isArray(value) ? value : [value]
    const dates = list.slice(0, 2).map(item => parsePickerDayjs(item, valueFormat, options.mode))
    if (!dates.some(Boolean)) {
      return undefined
    }
    return {
      start: dates[0] ? pickerDateValue(dates[0], withTime) : undefined,
      end: dates[1] ? pickerDateValue(dates[1], withTime) : undefined,
    }
  }
  const date = parsePickerDayjs(value, valueFormat, options.mode)
  return date ? pickerDateValue(date, withTime) : undefined
}

export function pickerSingleBinding(value: YdCalendarValue, valueFormat: YdPickerValueFormat, time?: YdPickerTimePart): YdPickerBindingValue | undefined {
  if (!value || Array.isArray(value) || 'start' in value) {
    return undefined
  }
  return formatPickerDayjs(pickerTimeWithDay(pickerDayjs(value), time), valueFormat)
}

export function pickerRangeBinding(value: YdCalendarValue, valueFormat: YdPickerValueFormat, startTime?: YdPickerTimePart, endTime?: YdPickerTimePart): YdPickerBindingValue[] | undefined {
  if (!value || Array.isArray(value) || !('start' in value) || !value.start || !value.end) {
    return undefined
  }
  return [
    formatPickerDayjs(pickerTimeWithDay(pickerDayjs(value.start), startTime), valueFormat),
    formatPickerDayjs(pickerTimeWithDay(pickerDayjs(value.end), endTime), valueFormat),
  ]
}

export function pickerDisplayText(value: unknown, valueFormat: YdPickerValueFormat, displayFormat: string, mode: YdPickerMode): string {
  const date = parsePickerDayjs(value, valueFormat, mode)
  if (date) {
    return date.format(displayFormat)
  }
  return typeof value === 'string' ? value : ''
}

/** 面板内部值（含待确认选择）→ 输入框展示文本。 */
export function pickerCalendarDisplayText(value: YdCalendarValue, displayFormat: string, time?: YdPickerTimePart): string {
  if (!value) {
    return ''
  }
  if (Array.isArray(value)) {
    return value.map(item => pickerTimeWithDay(pickerDayjs(item), time).format(displayFormat)).join('、')
  }
  if ('start' in value) {
    const start = value.start ? pickerTimeWithDay(pickerDayjs(value.start), time).format(displayFormat) : ''
    const end = value.end ? pickerTimeWithDay(pickerDayjs(value.end), time).format(displayFormat) : ''
    return start || end ? `${start} - ${end}` : ''
  }
  return pickerTimeWithDay(pickerDayjs(value), time).format(displayFormat)
}

function pickerSpanBlocked(start: DateValue, end: DateValue, blocked: (day: DateValue) => boolean): boolean {
  const total = end.calendar.toJulianDay(end) - start.calendar.toJulianDay(start)
  for (let index = 0; index <= total; index++) {
    if (!blocked(start.add({ days: index }))) {
      return false
    }
  }
  return true
}

export interface YdPickerDisabledOptions {
  mode: YdPickerMode
  weekStartsOn: YdCalendarWeekStart
  disabledDate?: (current?: Date) => boolean
}

/** Arco 语义：周/月/季/年仅整段全部禁用时才不可选。 */
export function pickerDisabledMatchers(options: YdPickerDisabledOptions) {
  const { disabledDate, mode, weekStartsOn } = options
  if (!disabledDate) {
    return {}
  }
  const blocked = (day: DateValue) => disabledDate(pickerJsDate(day)) === true
  return {
    isDateDisabled: (day: DateValue) => mode === 'week'
      ? pickerSpanBlocked(calendarWeekStart(day, weekStartsOn), calendarWeekStart(day, weekStartsOn).add({ days: 6 }), blocked)
      : blocked(day),
    isMonthDisabled: (day: DateValue) => pickerSpanBlocked(day, endOfMonth(day), blocked),
    isQuarterDisabled: (day: DateValue) => {
      const first = calendarQuarterStart(day)
      return pickerSpanBlocked(first, endOfMonth(first.add({ months: 2 })), blocked)
    },
    isYearDisabled: (day: DateValue) => pickerSpanBlocked(startOfYear(day), endOfYear(day), blocked),
  }
}

/** Arco ShortcutType → 系统日历快捷项。 */
export function pickerShortcutItems(shortcuts: YdPickerShortcut[] | undefined, options: { valueFormat: YdPickerValueFormat, mode: YdPickerMode, withTime?: boolean, range?: boolean }): YdCalendarShortcut[] {
  if (!shortcuts?.length) {
    return []
  }
  const withTime = options.withTime ?? false
  const convert = (raw: unknown): YdCalendarValue => {
    if (Array.isArray(raw)) {
      const dates = raw.slice(0, 2).map(item => parsePickerDayjs(item, options.valueFormat, options.mode))
      if (options.range) {
        return { start: dates[0] ? pickerDateValue(dates[0], withTime) : undefined, end: dates[1] ? pickerDateValue(dates[1], withTime) : undefined }
      }
      return dates[0] ? pickerDateValue(dates[0], withTime) : undefined
    }
    const date = parsePickerDayjs(raw, options.valueFormat, options.mode)
    return date ? pickerDateValue(date, withTime) : undefined
  }
  return shortcuts.map((item) => {
    const resolve = (): YdCalendarValue => {
      const raw = typeof item.value === 'function' ? item.value() : item.value
      return convert(raw)
    }
    return { text: String(item.label), value: resolve, disabled: item.disabled }
  })
}
