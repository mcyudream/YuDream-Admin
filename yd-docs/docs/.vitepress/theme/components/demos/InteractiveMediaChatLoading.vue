<script setup lang="ts">
import { ref } from 'vue'
import YdChatLoading from '../../../../../../yudream-frontend/packages/components/src/ai/YdChatLoading.vue'

const text = ref('正在思考…')
</script>

<template>
  <div class="interactive-media-loading">
    <YdChatLoading :text="text" />
    <label>提示文案 <input v-model="text" placeholder="留空只显示圆点"></label>
  </div>
</template>

<style scoped>
.interactive-media-loading { display: grid; gap: 12px; }
.interactive-media-loading label { color: var(--vp-c-text-2); font-size: 13px; }
.interactive-media-loading input { margin-left: 8px; padding: 5px 8px; border: 1px solid var(--vp-c-divider); border-radius: 6px; background: var(--vp-c-bg); color: var(--vp-c-text-1); }
</style>
