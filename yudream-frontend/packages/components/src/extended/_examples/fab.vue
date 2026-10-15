<script setup lang="ts">
import { ref } from 'vue'
import YdFab from '../YdFab.vue'

const open = ref(false)
const selected = ref('')
const actions = [
  { id: 'new', label: '新建', icon: 'morph:file-plus' },
  { id: 'photo', label: '拍照', icon: 'morph:camera' },
  { id: 'upload', label: '上传', icon: 'morph:upload' },
]
</script>

<template>
  <div class="border rounded-md h-64 relative">
    <YdFab v-model:open="open" :actions="actions" position="absolute" @select="selected = $event.label" />
    <p v-if="selected" class="p-4">
      已选择：{{ selected }}
    </p>
  </div>
</template>
