<script setup lang="ts">
import type { YdCalendarValue } from '@yudream/components'
import { CalendarDate, YdCalendarDate, YdCalendarDateRange, YdCalendarMonth, YdCalendarMonthRange, YdCalendarYear, YdCalendarYearRange, YdDate } from '@yudream/components'
import { shallowRef } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const date = shallowRef<YdCalendarValue>(new CalendarDate(2026, 10, 14))
const range = shallowRef<YdCalendarValue>()
const month = shallowRef<YdCalendarValue>()
const year = shallowRef<YdCalendarValue>()
const datetime = shallowRef<YdCalendarValue>()
const shortcuts = [{ text: '体验周', value: { start: new CalendarDate(2026, 10, 14), end: new CalendarDate(2026, 10, 20) } }]

function calendarLabel(value: YdCalendarValue): string {
  if (!value) {
    return '未选择'
  }
  if (Array.isArray(value)) {
    return value.length ? value.map(item => item.toString()).join('、') : '未选择'
  }
  if ('start' in value) {
    const start = value.start?.toString() ?? '…'
    const end = value.end?.toString() ?? '…'
    return value.start || value.end ? `${start} 至 ${end}` : '未选择'
  }
  return value.toString()
}
</script>

<template>
  <div class="calendar-gallery">
    <DemoCard title="日期与范围" description="键盘导航、快捷选择、禁用规则和范围限制">
      <div class="calendar-row">
        <YdCalendarDate v-model="date" /><YdCalendarDateRange v-model="range" :shortcuts="shortcuts" :maximum-days="14" />
      </div>
      <p class="demo-label">
        日期：{{ calendarLabel(date) }} · 范围：{{ calendarLabel(range) }}
      </p>
    </DemoCard>
    <DemoCard title="月份与年份" description="独立面板适合统计周期、预算年度等场景">
      <div class="calendar-row">
        <YdCalendarMonth v-model="month" /><YdCalendarMonthRange /><YdCalendarYear v-model="year" /><YdCalendarYearRange />
      </div>
    </DemoCard>
    <DemoCard title="弹出选择与日期时间" description="表单内选择、年/月/日逐层导航，时间独立调整">
      <div class="demo-row">
        <YdCalendarDate v-model="date" picker /><YdDate v-model="datetime" picker with-time picker-placeholder="选择日期和时间" /><YdCalendarYear picker picker-placeholder="选择年份" />
      </div>
      <p class="demo-label">
        日期时间：{{ calendarLabel(datetime) }}
      </p>
    </DemoCard>
  </div>
</template>

<style scoped>
.calendar-gallery {
  display: grid;
  gap: var(--yd-workspace-gap);
}

.calendar-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yd-content-gap);
  align-items: flex-start;
  min-width: 0;
}

/* The range calendar is wider than a single month. Compact cells keep both
 * panels visible on desktop while the existing wrap behavior still handles
 * narrow screens. */
.calendar-row:first-child {
  gap: 12px;
}

.calendar-row:first-child :deep(.yd-calendar-cell) {
  min-width: 34px;
  height: 34px;
  padding-inline: 4px;
}

@media (width <= 920px) {
  .calendar-row:first-child :deep(.yd-calendar-cell) {
    min-width: 32px;
    height: 32px;
  }
}
</style>
