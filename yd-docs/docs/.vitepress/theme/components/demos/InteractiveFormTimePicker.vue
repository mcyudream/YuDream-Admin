<script setup lang="ts">
import { YdTimePicker } from '@yudream/components'
import { ref } from 'vue'

const time = ref<string>('09:30:00')
const timeRange = ref<string[]>()
const stepped = ref<string>()

const labelStyle = 'width: 72px; flex-shrink: 0; color: var(--color-text-2, var(--vp-c-text-2)); font-size: 13px;'
const valueStyle = 'color: var(--color-text-3, var(--vp-c-text-2)); font-size: 12px;'
</script>

<template>
  <div style="display: grid; max-width: 560px; gap: 12px;">
    <div class="demo-row">
      <span :style="labelStyle">时间</span>
      <YdTimePicker v-model="time" style="flex: 1;" />
      <span :style="valueStyle">{{ time || '未选择' }}</span>
    </div>
    <div class="demo-row">
      <span :style="labelStyle">时间段</span>
      <YdTimePicker v-model="timeRange" range style="flex: 1;" />
      <span :style="valueStyle">{{ timeRange?.join(' ~ ') || '未选择' }}</span>
    </div>
    <div class="demo-row">
      <span :style="labelStyle">步长</span>
      <YdTimePicker v-model="stepped" format="HH:mm" :step="{ minute: 15 }" placeholder="按 15 分钟步长" style="flex: 1;" />
      <span :style="valueStyle">{{ stepped || '未选择' }}</span>
    </div>
  </div>
</template>
