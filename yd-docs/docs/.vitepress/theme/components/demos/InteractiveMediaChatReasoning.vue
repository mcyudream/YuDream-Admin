<script setup lang="ts">
import { ref } from 'vue'
import YdChatReasoning from '../../../../../../yudream-frontend/packages/components/src/ai/YdChatReasoning.vue'

const pending = ref(true)
const title = ref('深度思考')
const content = ref('先核对需求，再使用本地响应式状态展示真实组件的展开和收起行为。')
</script>

<template>
  <div class="interactive-media-reasoning">
    <YdChatReasoning :content="content" :title="title" :pending="pending" default-expanded />
    <label>标题 <input v-model="title"></label>
    <label>内容 <input v-model="content"></label>
    <label><input v-model="pending" type="checkbox"> 模拟推理进行中</label>
  </div>
</template>

<style scoped>
.interactive-media-reasoning { display: grid; max-width: 520px; gap: 10px; }
.interactive-media-reasoning label { color: var(--vp-c-text-2); font-size: 13px; }
.interactive-media-reasoning input:not([type='checkbox']) { width: min(100%, 360px); margin-left: 8px; padding: 5px 8px; border: 1px solid var(--vp-c-divider); border-radius: 6px; background: var(--vp-c-bg); color: var(--vp-c-text-1); }
</style>
