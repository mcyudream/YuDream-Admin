<script setup lang="ts">
import type { ShortcutType } from '@arco-design/web-vue/es/date-picker/interface'
import { YdRangePicker } from '@yudream/components'
import { ref } from 'vue'

const range = ref<string[]>()
const rangeWithTime = ref<string[]>(['2026-09-01 00:00:00', '2026-09-05 23:59:59'])

function dayOffset(offset: number): Date {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d
}

const shortcuts: ShortcutType[] = [
  { label: '近 7 天', value: () => [dayOffset(-6), new Date()] },
  { label: '近 30 天', value: () => [dayOffset(-29), new Date()] },
  { label: '本月', value: () => [new Date(new Date().getFullYear(), new Date().getMonth(), 1), new Date()] },
]

const labelStyle = 'width: 72px; flex-shrink: 0; color: var(--color-text-2, var(--vp-c-text-2)); font-size: 13px;'
const valueStyle = 'color: var(--color-text-3, var(--vp-c-text-2)); font-size: 12px; word-break: break-all;'
</script>

<template>
  <div style="display: grid; max-width: 640px; gap: 12px;">
    <div class="demo-row">
      <span :style="labelStyle">日期范围</span>
      <YdRangePicker v-model="range" :shortcuts="shortcuts" style="flex: 1;" />
    </div>
    <div :style="valueStyle">
      已选：{{ range?.join(' ~ ') || '未选择' }}
    </div>
    <div class="demo-row">
      <span :style="labelStyle">含时间</span>
      <YdRangePicker v-model="rangeWithTime" show-time style="flex: 1;" />
    </div>
    <div :style="valueStyle">
      已选：{{ rangeWithTime?.join(' ~ ') || '未选择' }}
    </div>
  </div>
</template>
