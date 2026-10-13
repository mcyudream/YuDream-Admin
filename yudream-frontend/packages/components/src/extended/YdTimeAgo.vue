<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { computed, ref } from 'vue'

const props = withDefaults(defineProps<{
  date: string | number | Date
  locale?: string
  showSecond?: boolean
  updateInterval?: number
}>(), { locale: 'zh-CN', updateInterval: 30000 })
const now = ref(new Date())
useIntervalFn(() => {
  now.value = new Date()
}, () => Math.max(1000, Number.isFinite(props.updateInterval) ? props.updateInterval : 30000))
const dateValue = computed(() => new Date(props.date))
const valid = computed(() => !Number.isNaN(dateValue.value.getTime()))
const label = computed(() => {
  if (!valid.value) {
    return ''
  }
  const seconds = (dateValue.value.getTime() - now.value.getTime()) / 1000
  if (Math.abs(seconds) < (props.showSecond ? 1 : 60)) {
    return '刚刚'
  }
  const units = [[31536000, 'year'], [2592000, 'month'], [604800, 'week'], [86400, 'day'], [3600, 'hour'], [60, 'minute'], [1, 'second']] as const
  const [size, unit] = units.find(([size]) => Math.abs(seconds) >= size) ?? units[6]
  return new Intl.RelativeTimeFormat(props.locale, { numeric: 'auto' }).format(Math.trunc(seconds / size), unit)
})
</script>

<template>
  <time :datetime="valid ? dateValue.toISOString() : undefined" :title="valid ? dateValue.toLocaleString(locale) : undefined">{{ label }}</time>
</template>
