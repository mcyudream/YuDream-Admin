<script setup lang="ts">
import type { YdChatMessage } from '../../../../../../yudream-frontend/packages/components/src/ai/useYdChatStream'
import { ref } from 'vue'
import YdBubble from '../../../../../../yudream-frontend/packages/components/src/ai/YdBubble.vue'

const notice = ref('')
const userMessage: YdChatMessage = { id: 'demo-user', role: 'user', content: '帮我说明这个真实组件 demo 的交互。' }
const assistantMessage: YdChatMessage = { id: 'demo-assistant', role: 'assistant', content: '这是由 **YdBubble** 渲染的本地消息，可操作、可反馈。' }
const streamingMessage = ref<YdChatMessage>({ id: 'demo-stream', role: 'assistant', content: '', pending: true })
let timer: ReturnType<typeof setInterval> | undefined

function streamLocally() {
  if (timer) return
  const text = '这段回答逐字在本地追加，没有 API 请求。'
  let index = 0
  streamingMessage.value = { id: 'demo-stream', role: 'assistant', content: '', pending: true }
  timer = setInterval(() => {
    index += 2
    streamingMessage.value.content = text.slice(0, index)
    if (index >= text.length) {
      streamingMessage.value.pending = false
      clearInterval(timer)
      timer = undefined
    }
  }, 80)
}
</script>

<template>
  <div class="interactive-media-bubble">
    <YdBubble :message="userMessage" />
    <YdBubble :message="assistantMessage" show-actions feedback @copy="notice = '已触发 copy 事件'" @regenerate="notice = '已触发 regenerate 事件'" @feedback="value => notice = `反馈：${value ?? '取消'}`" />
    <YdBubble :message="streamingMessage" caret thinking-text="本地 mock 正在准备…" />
    <div><button type="button" @click="streamLocally">重新播放本地流式输出</button><span>{{ notice }}</span></div>
  </div>
</template>

<style scoped>
.interactive-media-bubble { display: grid; max-width: 560px; gap: 12px; }
.interactive-media-bubble div { display: flex; gap: 8px; align-items: center; }
.interactive-media-bubble button { padding: 5px 10px; border: 1px solid var(--vp-c-divider); border-radius: 6px; background: var(--vp-c-bg); color: var(--vp-c-text-1); cursor: pointer; }
.interactive-media-bubble span { color: var(--vp-c-text-2); font-size: 12px; }
</style>
