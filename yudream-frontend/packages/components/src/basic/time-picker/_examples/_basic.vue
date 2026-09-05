<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import FaTimePicker from '../index.vue'

const time = ref<string>()
</script>

<template>
  <FaTimePicker v-model="time" />
</template>
