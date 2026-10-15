import type { DateValue } from '@internationalized/date'
import assert from 'node:assert/strict'
// The workspace runs these pure date checks with node:test via tsx.
// eslint-disable-next-line test/no-import-node-test
import test from 'node:test'
import { CalendarDate, CalendarDateTime } from '@internationalized/date'
import { calendarDateBlocked, calendarPeriod, calendarQuarterStart, calendarRangeSize, calendarTime, calendarValueAllowed, calendarWeekEnd, calendarWeekStart, firstCalendarDate, normalizeCalendarTime, normalizeCalendarValue } from './calendar'

function day(year: number, month: number, date = 1) {
  return new CalendarDate(year, month, date)
}

test('range limits count included periods across leap days, month and year boundaries', () => {
  const leapRange = { start: day(2024, 2, 28), end: day(2024, 3, 1) }
  assert.equal(calendarRangeSize(leapRange.start, leapRange.end, 'date'), 3)
  assert.equal(calendarValueAllowed(leapRange, 'date', { maximumDays: 3 }, true), true)
  assert.equal(calendarValueAllowed(leapRange, 'date', { maximumDays: 2 }, true), false)
  const months = { start: day(2025, 12, 15), end: day(2026, 2, 22) }
  assert.equal(calendarRangeSize(months.start, months.end, 'month'), 3)
  assert.equal(calendarValueAllowed(months, 'month', { maximumMonths: 2 }, true), false)
  assert.equal(calendarValueAllowed(months, 'month', { maximumMonths: 3 }, true), true)
  const years = { start: day(2024, 9), end: day(2026, 2) }
  assert.equal(calendarRangeSize(years.start, years.end, 'year'), 3)
  assert.equal(calendarValueAllowed(years, 'year', { maximumYears: 2 }, true), false)
  assert.equal(calendarValueAllowed(years, 'year', { maximumYears: 3 }, true), true)
  assert.equal(calendarValueAllowed(leapRange, 'date', { maximumDays: Number.NaN }, true), false)
})

test('shortcuts cannot bypass disabled dates or unavailable interior periods', () => {
  const value = { start: day(2026, 10, 1), end: day(2026, 10, 5) }
  const unavailable = { isDateUnavailable: (date: DateValue) => date.day === 3 }
  assert.equal(calendarValueAllowed(value, 'date', unavailable, true), false)
  assert.equal(calendarValueAllowed(value, 'date', { ...unavailable, allowNonContiguousRanges: true }, true), true)
  assert.equal(calendarValueAllowed(value, 'date', { isDateDisabled: date => date.day === 3, allowNonContiguousRanges: true }, true), false)
  assert.equal(calendarValueAllowed(value, 'date', { isDateUnavailable: date => date.day === 1, allowNonContiguousRanges: true }, true), false)
  assert.equal(calendarValueAllowed({ start: day(2026, 3), end: day(2026, 5) }, 'month', { isMonthUnavailable: date => date.month === 4 }, true), false)
  assert.equal(calendarValueAllowed({ start: day(2024, 3), end: day(2026, 5) }, 'year', { isYearDisabled: date => date.year === 2025 }, true), false)
})

test('day, month and year bounds follow visible calendar cell granularity', () => {
  const bounds = { minValue: day(2026, 10, 14), maxValue: day(2026, 11, 3) }
  assert.equal(calendarDateBlocked(day(2026, 10, 13), 'date', bounds), true)
  assert.equal(calendarDateBlocked(day(2026, 10, 14), 'date', bounds), false)
  assert.equal(calendarDateBlocked(day(2026, 10), 'month', bounds), false)
  assert.equal(calendarDateBlocked(day(2026, 11), 'month', bounds), false)
  assert.equal(calendarDateBlocked(day(2026, 9), 'month', bounds), true)
  assert.equal(calendarDateBlocked(day(2026, 1), 'year', bounds), false)
  assert.equal(calendarDateBlocked(day(2027, 1), 'year', bounds), true)
  assert.equal(calendarValueAllowed([day(2026, 10, 14), day(2026, 11, 4)], 'date', bounds), false)
})

test('partial ranges, clearing and readonly use the same validation path', () => {
  assert.equal(calendarValueAllowed({ start: day(2026, 10, 1), end: undefined }, 'date', {}, true), true)
  assert.equal(calendarValueAllowed({ start: day(2026, 10, 5), end: day(2026, 10, 1) }, 'date', {}, true), false)
  assert.equal(calendarValueAllowed(undefined, 'date', {}), true)
  assert.equal(calendarValueAllowed(undefined, 'date', { readonly: true }), false)
  assert.equal(calendarValueAllowed(day(2026, 10, 1), 'date', { disabled: true }), false)
  assert.equal(calendarValueAllowed(day(2026, 10, 1), 'date', {}, true), false)
  assert.equal(firstCalendarDate({ start: undefined, end: day(2026, 10, 3) })?.toString(), '2026-10-03')
  assert.equal(firstCalendarDate([]), undefined)
})

test('month/year normalization removes unrelated day and time; time fallback never throws', () => {
  const selected = new CalendarDateTime(2026, 10, 14, 15, 32, 7)
  assert.equal(normalizeCalendarValue(selected, 'month')?.toString(), '2026-10-01')
  assert.equal(normalizeCalendarValue(selected, 'year')?.toString(), '2026-01-01')
  assert.equal(calendarTime(selected), '15:32:07')
  assert.equal(calendarTime(undefined, '09:15'), '09:15:00')
  assert.equal(normalizeCalendarTime('bad', '08:20'), '08:20:00')
  assert.equal(normalizeCalendarTime('bad', 'also bad'), '00:00:00')
  assert.equal(normalizeCalendarTime('24:61'), '00:00:00')
})

test('week mode snaps selections to the configured week start', () => {
  // 2026-10-15 是周四
  assert.equal(calendarWeekStart(day(2026, 10, 15), 1).toString(), '2026-10-12')
  assert.equal(calendarWeekStart(day(2026, 10, 15), 0).toString(), '2026-10-11')
  assert.equal(calendarWeekEnd(day(2026, 10, 15), 1).toString(), '2026-10-18')
  assert.equal(calendarPeriod(day(2026, 10, 15), 'week', 1).toString(), '2026-10-12')
  assert.equal(normalizeCalendarValue(day(2026, 10, 15), 'week', 1)?.toString(), '2026-10-12')
  assert.deepEqual(normalizeCalendarValue({ start: day(2026, 10, 15), end: day(2026, 10, 20) }, 'week', 1), { start: day(2026, 10, 12), end: day(2026, 10, 19) })
  assert.equal(calendarRangeSize(day(2026, 10, 12), day(2026, 10, 26), 'week', 1), 3)
  assert.equal(calendarValueAllowed({ start: day(2026, 10, 12), end: day(2026, 10, 26) }, 'week', { maximumDays: 2 }, true), false)
  // 与月/年一致：周单元格与 [min, max] 相交即可选
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'week', { maxValue: day(2026, 10, 12) }), false)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'week', { maxValue: day(2026, 10, 11) }), true)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'week', { minValue: day(2026, 10, 18) }), false)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'week', { minValue: day(2026, 11, 1) }), true)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'week', { minValue: day(2026, 10, 12), maxValue: day(2026, 10, 18) }), false)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'week', { isDateDisabled: date => date.day === 12 }), true)
})

test('quarter mode normalizes to the first month and counts whole quarters', () => {
  assert.equal(calendarQuarterStart(day(2026, 10, 15)).toString(), '2026-10-01')
  assert.equal(calendarQuarterStart(day(2026, 7, 31)).toString(), '2026-07-01')
  assert.equal(calendarPeriod(day(2026, 12, 31), 'quarter').toString(), '2026-10-01')
  assert.deepEqual(normalizeCalendarValue({ start: day(2026, 7, 31), end: day(2026, 10, 5) }, 'quarter'), { start: day(2026, 7, 1), end: day(2026, 10, 1) })
  assert.equal(calendarRangeSize(day(2026, 1, 1), day(2026, 10, 1), 'quarter'), 4)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'quarter', { isQuarterDisabled: date => date.month === 10 }), true)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'quarter', { maxValue: day(2026, 9, 30) }), true)
  assert.equal(calendarDateBlocked(day(2026, 10, 15), 'quarter', { maxValue: day(2026, 10, 31) }), false)
  assert.equal(calendarValueAllowed({ start: day(2026, 4, 1), end: day(2026, 10, 1) }, 'quarter', { isQuarterDisabled: date => date.month === 7 }, true), false)
})
