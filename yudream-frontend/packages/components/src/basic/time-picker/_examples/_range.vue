<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import YdTimePicker from '../index.vue'

const range = ref<string[]>()
</script>

<template>
  <YdTimePicker v-model="range" range />
</template>
