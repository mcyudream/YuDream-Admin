<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import FaDatePicker from '../index.vue'

const date = ref<string>()
</script>

<template>
  <FaDatePicker v-model="date" />
</template>
