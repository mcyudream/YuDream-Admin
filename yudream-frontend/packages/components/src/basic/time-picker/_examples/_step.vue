<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import { ref } from 'vue'
import YdTimePicker from '../index.vue'

const time = ref<string>()
</script>

<template>
  <YdTimePicker v-model="time" format="HH:mm" :step="{ minute: 30 }" />
</template>
