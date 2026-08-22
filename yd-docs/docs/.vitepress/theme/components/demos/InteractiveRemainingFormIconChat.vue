<script setup lang="ts">
import { ref } from 'vue'
import { FaButton } from '@yudream/components'
import { YdBubble, YdChatWindow } from '@yudream/components/ai'

const visible = ref(false)
const notice = ref('')
const message = { role: 'assistant' as const, content: '这是本地 mock 消息；该演示不会发起网络或 API 请求。' }
</script>

<template>
  <div class="demo-row"><FaButton size="sm" @click="visible = true">打开真实聊天窗口</FaButton><span class="text-sm text-muted-foreground">{{ notice || '窗口内使用本地 mock 数据。' }}</span></div>
  <YdChatWindow v-if="visible" title="本地 AI 助手" :width="380" :height="460" :fullscreenable="false" @close="visible = false" @expand="notice = '已触发 expand 事件'">
    <div class="p-4"><YdBubble :message="message" /></div>
  </YdChatWindow>
</template>
