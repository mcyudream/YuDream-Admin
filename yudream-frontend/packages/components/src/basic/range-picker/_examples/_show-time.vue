<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import FaRangePicker from '../index.vue'

const range = ref<string[]>()
</script>

<template>
  <FaRangePicker v-model="range" show-time :placeholder="['开始时间', '结束时间']" />
</template>
