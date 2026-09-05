<script setup lang="ts">
import { YdDatePicker } from '@yudream/components'
import { ref } from 'vue'

const date = ref<string>('2026-09-05')
const dateTime = ref<string>()
const month = ref<string>()

const labelStyle = 'width: 72px; flex-shrink: 0; color: var(--color-text-2, var(--vp-c-text-2)); font-size: 13px;'
const valueStyle = 'color: var(--color-text-3, var(--vp-c-text-2)); font-size: 12px;'
</script>

<template>
  <div style="display: grid; max-width: 560px; gap: 12px;">
    <div class="demo-row">
      <span :style="labelStyle">日期</span>
      <YdDatePicker v-model="date" style="flex: 1;" />
      <span :style="valueStyle">{{ date || '未选择' }}</span>
    </div>
    <div class="demo-row">
      <span :style="labelStyle">日期时间</span>
      <YdDatePicker v-model="dateTime" show-time placeholder="请选择日期时间" style="flex: 1;" />
      <span :style="valueStyle">{{ dateTime || '未选择' }}</span>
    </div>
    <div class="demo-row">
      <span :style="labelStyle">月份</span>
      <YdDatePicker v-model="month" mode="month" value-format="YYYY-MM" placeholder="请选择月份" style="flex: 1;" />
      <span :style="valueStyle">{{ month || '未选择' }}</span>
    </div>
  </div>
</template>
