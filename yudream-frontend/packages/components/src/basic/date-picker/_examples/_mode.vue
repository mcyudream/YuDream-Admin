<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import YdDatePicker from '../index.vue'

const week = ref<string>()
const month = ref<string>()
const quarter = ref<string>()
const year = ref<string>()
</script>

<template>
  <div class="flex flex-col gap-4">
    <YdDatePicker v-model="week" mode="week" value-format="YYYY-MM-DD" placeholder="请选择周" />
    <YdDatePicker v-model="month" mode="month" value-format="YYYY-MM" placeholder="请选择月份" />
    <YdDatePicker v-model="quarter" mode="quarter" placeholder="请选择季度" />
    <YdDatePicker v-model="year" mode="year" value-format="YYYY" placeholder="请选择年份" />
  </div>
</template>
