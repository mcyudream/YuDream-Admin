<script setup lang="ts">
import { ref } from 'vue'
import YdIconPicker from '../index.vue'

const icon = ref('i-ri:settings-3-line')
</script>

<template>
  <YdIconPicker v-model="icon" class="w-80" />
</template>
