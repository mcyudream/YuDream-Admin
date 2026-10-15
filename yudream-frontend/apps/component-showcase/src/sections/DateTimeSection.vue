<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const dateValue = ref('2026-10-15')
const weekValue = ref('')
const monthValue = ref('')
const quarterValue = ref('')
const dateTimeValue = ref('')
const rangeValue = ref<string[] | undefined>(['2026-10-01', '2026-10-15'])
const dateTimeRangeValue = ref<string[] | undefined>()
const timeValue = ref('09:30:00')
const timeRangeValue = ref<string[] | undefined>()

const shortcuts = [
  { label: '今天', value: () => new Date() },
  { label: '一周后', value: () => new Date(Date.now() + 7 * 86400000) },
]
</script>

<template>
  <DemoCard title="YdDatePicker" description="日期选择：mode 覆盖 date / week / month / quarter / year，showTime 联动时间，绑定值为格式化字符串">
    <div class="gap-4 grid max-w-md">
      <div class="gap-2 grid">
        <span class="text-sm">日期（默认）</span>
        <YdDatePicker v-model="dateValue" :shortcuts="shortcuts" />
        <output class="text-xs text-muted-foreground" aria-live="polite">
          绑定值：{{ dateValue ?? '(空)' }}
        </output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">周（显式 valueFormat 保证可读）</span>
        <YdDatePicker v-model="weekValue" mode="week" value-format="gggg-第wo" placeholder="请选择周" />
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">月份</span>
        <YdDatePicker v-model="monthValue" mode="month" value-format="YYYY-MM" placeholder="请选择月份" />
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">季度</span>
        <YdDatePicker v-model="quarterValue" mode="quarter" placeholder="请选择季度" />
        <output class="text-xs text-muted-foreground" aria-live="polite">
          绑定值：{{ quarterValue || '(空)' }}
        </output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">日期时间（showTime）</span>
        <YdDatePicker v-model="dateTimeValue" show-time placeholder="请选择日期时间" />
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">禁用状态</span>
        <YdDatePicker model-value="2026-10-15" disabled />
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdRangePicker" description="范围选择：绑定值为 [开始, 结束] 数组，清除后为 undefined">
    <div class="gap-4 grid max-w-md">
      <div class="gap-2 grid">
        <span class="text-sm">日期范围</span>
        <YdRangePicker v-model="rangeValue" />
        <output class="text-xs text-muted-foreground break-words" aria-live="polite">
          绑定值：{{ rangeValue ? `${rangeValue[0]} 至 ${rangeValue[1]}` : '(空)' }}
        </output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">月份范围</span>
        <YdRangePicker mode="month" value-format="YYYY-MM" :placeholder="['开始月份', '结束月份']" />
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">日期时间范围（showTime 需点确定）</span>
        <YdRangePicker v-model="dateTimeRangeValue" show-time :shortcuts="shortcuts" shortcuts-position="bottom" />
        <output class="text-xs text-muted-foreground break-words" aria-live="polite">
          绑定值：{{ dateTimeRangeValue ? `${dateTimeRangeValue[0]} 至 ${dateTimeRangeValue[1]}` : '(空)' }}
        </output>
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdTimePicker" description="时间选择：绑定值按 format 格式化，range 开启范围，面板需点确定写入">
    <div class="gap-4 grid max-w-md">
      <div class="gap-2 grid">
        <span class="text-sm">时间</span>
        <YdTimePicker v-model="timeValue" />
        <output class="text-xs text-muted-foreground" aria-live="polite">
          绑定值：{{ timeValue ?? '(空)' }}
        </output>
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">时间范围</span>
        <YdTimePicker v-model="timeRangeValue" range />
      </div>

      <div class="gap-2 grid">
        <span class="text-sm">12 小时制 + 步长</span>
        <YdTimePicker use12-hours :step="{ minute: 15 }" format="HH:mm" placeholder="请选择时间" />
      </div>
    </div>
  </DemoCard>
</template>
