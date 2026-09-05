<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import YdDatePicker from '../index.vue'

const datetime = ref<string>()
</script>

<template>
  <YdDatePicker v-model="datetime" show-time placeholder="请选择日期时间" />
</template>
