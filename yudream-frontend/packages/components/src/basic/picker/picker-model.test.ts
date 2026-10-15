/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { parsePickerDayjs, pickerCalendarDisplayText, pickerCalendarValue, pickerClockTokens, pickerDateValue, pickerDefaultDisplayFormat, pickerDefaultValueFormat, pickerDisabledMatchers, pickerDisplayText, pickerRangeBinding, pickerShortcutItems, pickerSingleBinding, pickerTimeFormat } from './picker-model'

test('显示格式与绑定格式按模式回落到 Arco 一致的默认值', () => {
  assert.equal(pickerDefaultDisplayFormat('date'), 'YYYY-MM-DD')
  assert.equal(pickerDefaultDisplayFormat('date', true), 'YYYY-MM-DD HH:mm:ss')
  assert.equal(pickerDefaultDisplayFormat('week'), 'gggg-wo')
  assert.equal(pickerDefaultDisplayFormat('month'), 'YYYY-MM')
  assert.equal(pickerDefaultDisplayFormat('quarter'), 'YYYY-[Q]Q')
  assert.equal(pickerDefaultDisplayFormat('year'), 'YYYY')
  assert.equal(pickerDefaultValueFormat('week'), 'YYYY-MM-DD')
  assert.equal(pickerDefaultValueFormat('quarter'), 'YYYY-MM')
  assert.equal(pickerDefaultValueFormat('year'), 'YYYY')
  assert.equal(pickerDefaultValueFormat('date', true), 'YYYY-MM-DD HH:mm:ss')
})

test('时间列粒度由显示格式的时间部分决定', () => {
  assert.equal(pickerTimeFormat('YYYY-MM-DD HH:mm:ss'), 'HH:mm:ss')
  assert.equal(pickerTimeFormat('YYYY-MM-DD HH:mm'), 'HH:mm')
  assert.equal(pickerTimeFormat('YYYY-MM-DD hh:mm A'), 'hh:mm A')
  assert.equal(pickerTimeFormat('YYYY-MM-DD'), 'HH:mm:ss')
  assert.deepEqual(pickerClockTokens('YYYY-MM-DD HH:mm'), { hour: true, minute: true, second: false, period: false })
  assert.deepEqual(pickerClockTokens('YYYY-MM-DD hh:mm:ss A'), { hour: true, minute: true, second: true, period: true })
})

test('各模式绑定值可解析并可回写', () => {
  assert.equal(parsePickerDayjs('2026-10-15', 'YYYY-MM-DD', 'date')?.format('YYYY-MM-DD'), '2026-10-15')
  assert.equal(parsePickerDayjs('2026-10', 'YYYY-MM', 'month')?.format('YYYY-MM-DD'), '2026-10-01')
  assert.equal(parsePickerDayjs('2026', 'YYYY', 'year')?.format('YYYY-MM-DD'), '2026-01-01')
  assert.equal(parsePickerDayjs('2026-07', 'YYYY-MM', 'quarter')?.format('YYYY-MM-DD'), '2026-07-01')
  const stamp = parsePickerDayjs(1760486400000, 'timestamp')
  assert.equal(stamp?.valueOf(), 1760486400000)
  const date = new Date(2026, 9, 15, 9, 30, 0)
  assert.equal(parsePickerDayjs(date, 'Date')?.format('YYYY-MM-DD HH:mm:ss'), '2026-10-15 09:30:00')
  assert.equal(parsePickerDayjs('不是日期', 'YYYY-MM-DD'), undefined)
})

test('周模式可回读周格式文本用于恢复选中态', () => {
  const parsed = parsePickerDayjs('2026-第42周', 'gggg-wo', 'week')
  assert.equal(parsed?.format('YYYY-MM-DD'), '2026-10-12')
  assert.equal(parsePickerDayjs('2026-42周', 'gggg-wo', 'week')?.format('YYYY-MM-DD'), '2026-10-12')
  assert.equal(pickerDisplayText('2026-第42周', 'gggg-wo', 'gggg-wo', 'week'), '2026-42周')
})

test('绑定值与面板值之间按 valueFormat 合并时间', () => {
  const value = pickerCalendarValue('2026-10-15', 'YYYY-MM-DD', { mode: 'date' })
  assert.equal(pickerSingleBinding(value, 'YYYY-MM-DD'), '2026-10-15')
  assert.equal(pickerSingleBinding(value, 'YYYY-MM-DD HH:mm:ss', { hour: 9, minute: 30, second: 0 }), '2026-10-15 09:30:00')
  assert.equal(pickerSingleBinding(value, 'timestamp'), new Date(2026, 9, 15).getTime())
  const range = pickerCalendarValue(['2026-10-01', '2026-10-15'], 'YYYY-MM-DD', { mode: 'date', range: true })
  assert.equal(pickerRangeBinding(range, 'YYYY-MM-DD')?.join('~'), '2026-10-01~2026-10-15')
  assert.equal(pickerRangeBinding(range, 'YYYY-MM-DD HH:mm:ss', { hour: 8, minute: 0, second: 0 }, { hour: 20, minute: 0, second: 0 })?.join('~'), '2026-10-01 08:00:00~2026-10-15 20:00:00')
  assert.equal(pickerRangeBinding({ start: pickerDateValue(parsePickerDayjs('2026-10-15', 'YYYY-MM-DD')!, false), end: undefined }, 'YYYY-MM-DD'), undefined)
})

test('周/月/季/年整段禁用才不可选，日期模式逐日判断', () => {
  const week = pickerDisabledMatchers({ mode: 'week', weekStartsOn: 1, disabledDate: day => day?.getDay() !== 0 })
  const monday = pickerDateValue(parsePickerDayjs('2026-10-12', 'YYYY-MM-DD')!, false)
  assert.equal(week.isDateDisabled?.(monday), false)
  const allBlocked = pickerDisabledMatchers({ mode: 'week', weekStartsOn: 1, disabledDate: () => true })
  assert.equal(allBlocked.isDateDisabled?.(monday), true)
  assert.equal(allBlocked.isMonthDisabled?.(monday), true)
  assert.equal(allBlocked.isQuarterDisabled?.(monday), true)
  assert.equal(allBlocked.isYearDisabled?.(monday), true)
  const firstDayOfMonth = pickerDisabledMatchers({ mode: 'month', weekStartsOn: 1, disabledDate: day => day?.getDate() === 1 })
  assert.equal(firstDayOfMonth.isMonthDisabled?.(monday), false)
  assert.equal(firstDayOfMonth.isDateDisabled?.(monday), false)
  assert.deepEqual(pickerDisabledMatchers({ mode: 'date', weekStartsOn: 1 }), {})
})

test('快捷项转换为系统日历格式并解析绑定值', () => {
  const items = pickerShortcutItems([{ label: '今天', value: () => new Date(2026, 9, 15) }], { valueFormat: 'YYYY-MM-DD', mode: 'date' })
  assert.equal(items.length, 1)
  assert.equal(items[0].text, '今天')
  const value = typeof items[0].value === 'function' ? items[0].value() : items[0].value
  assert.equal(value && 'year' in value ? value.toString() : '', '2026-10-15')
  const rangeItems = pickerShortcutItems([{ label: '本月', value: ['2026-10-01', '2026-10-31'] }], { valueFormat: 'YYYY-MM-DD', mode: 'date', range: true })
  const rangeValue = typeof rangeItems[0].value === 'function' ? rangeItems[0].value() : rangeItems[0].value
  assert.deepEqual(rangeValue, { start: rangeValue && 'start' in rangeValue ? rangeValue.start : undefined, end: rangeValue && 'start' in rangeValue ? rangeValue.end : undefined })
  assert.equal(rangeValue && 'start' in rangeValue ? rangeValue.start?.toString() : '', '2026-10-01')
  assert.equal(rangeValue && 'start' in rangeValue ? rangeValue.end?.toString() : '', '2026-10-31')
})

test('面板待确认值按显示格式输出文本，区间保持两端各自时间', () => {
  const start = pickerDateValue(parsePickerDayjs('2026-10-01', 'YYYY-MM-DD')!, false)
  const end = pickerDateValue(parsePickerDayjs('2026-10-15', 'YYYY-MM-DD')!, false)
  const range = { start, end }
  assert.equal(pickerCalendarDisplayText(range, 'YYYY-MM-DD HH:mm:ss', { hour: 8, minute: 0, second: 0 }), '2026-10-01 08:00:00 - 2026-10-15 08:00:00')
  assert.equal(pickerCalendarDisplayText(range, 'YYYY-MM-DD'), '2026-10-01 - 2026-10-15')
  assert.equal(pickerCalendarDisplayText(start, 'YYYY-MM-DD'), '2026-10-01')
})
