<script setup lang="ts">
import type { YdChatMessage } from '../../../../../../yudream-frontend/packages/components/src/ai/useYdChatStream'
import { ref } from 'vue'
import YdBubble from '../../../../../../yudream-frontend/packages/components/src/ai/YdBubble.vue'
import YdChatWindow from '../../../../../../yudream-frontend/packages/components/src/ai/YdChatWindow.vue'

const visible = ref(false)
const notice = ref('')
const message: YdChatMessage = { role: 'assistant', content: '窗口内是本地插槽内容。可拖动标题栏、缩放或关闭。' }
</script>

<template>
  <div class="interactive-media-window">
    <button type="button" @click="visible = true">打开真实聊天窗口</button>
    <span>{{ notice || '窗口关闭后可在此重新打开。' }}</span>
    <YdChatWindow v-if="visible" title="本地 AI 助手" :width="380" :height="460" @close="visible = false" @expand="notice = '已触发 expand 事件（不进行路由跳转）'">
      <div class="interactive-media-window__body"><YdBubble :message="message" /></div>
    </YdChatWindow>
  </div>
</template>

<style scoped>
.interactive-media-window { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.interactive-media-window button { padding: 6px 10px; border: 1px solid var(--vp-c-divider); border-radius: 6px; background: var(--vp-c-bg); color: var(--vp-c-text-1); cursor: pointer; }
.interactive-media-window span { color: var(--vp-c-text-2); font-size: 13px; }
.interactive-media-window__body { height: 100%; padding: 14px; overflow: auto; }
</style>
