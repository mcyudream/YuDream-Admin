<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import YdDatePicker from '../index.vue'

const date = ref<string>()

function offsetDate(days: number) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d
}

const shortcuts = [
  { label: '今天', value: () => new Date() },
  { label: '昨天', value: () => offsetDate(-1) },
  { label: '一周后', value: () => offsetDate(7) },
]
</script>

<template>
  <YdDatePicker v-model="date" :shortcuts="shortcuts" shortcuts-position="bottom" />
</template>
