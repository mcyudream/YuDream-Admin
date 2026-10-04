<script setup lang="ts">
import { ref } from 'vue'
import YdIconPicker from '../index.vue'

const icon = ref('morph:settings')
</script>

<template>
  <YdIconPicker v-model="icon" class="w-80" />
</template>
