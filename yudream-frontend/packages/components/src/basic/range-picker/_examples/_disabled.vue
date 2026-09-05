<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import YdRangePicker from '../index.vue'

const range = ref<string[]>(['2026-09-01', '2026-09-05'])
</script>

<template>
  <YdRangePicker v-model="range" disabled />
</template>
