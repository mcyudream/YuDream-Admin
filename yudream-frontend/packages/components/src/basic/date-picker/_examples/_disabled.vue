<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import FaDatePicker from '../index.vue'

const date = ref('2026-09-05')
</script>

<template>
  <FaDatePicker v-model="date" disabled />
</template>
