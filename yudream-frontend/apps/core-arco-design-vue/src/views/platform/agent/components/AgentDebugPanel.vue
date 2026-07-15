<script setup lang="ts">
import type { AgentDebugMessage, AgentDebugStatus } from './types'

const props = defineProps<{
  messages: AgentDebugMessage[]
  running: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
  stop: []
  clear: []
  close: []
}>()

const input = ref('')
const scrollEl = useTemplateRef<HTMLElement>('scrollEl')

watch(() => props.messages, () => {
  nextTick(() => {
    if (scrollEl.value) {
      scrollEl.value.scrollTop = scrollEl.value.scrollHeight
    }
  })
}, { deep: true })

function send() {
  const content = input.value.trim()
  if (!content || props.running) {
    return
  }
  input.value = ''
  emit('send', content)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

function statusIcon(status: AgentDebugStatus) {
  return ({
    RUNNING: 'i-ri:loader-4-line',
    COMPLETED: 'i-ri:check-line',
    SKIPPED: 'i-ri:skip-forward-line',
    FAILED: 'i-ri:close-line',
  } as const)[status]
}
</script>

<template>
  <aside class="debug-panel">
    <header class="debug-header">
      <div class="debug-title">
        <span class="debug-mark"><FaIcon name="i-ri:bug-line" /></span>
        <span>
          <strong>Agent 调试</strong>
          <small><i :class="{ active: running }" />{{ running ? '运行中' : '就绪' }}</small>
        </span>
      </div>
      <div class="debug-actions">
        <button type="button" title="清空调试记录" aria-label="清空调试记录" :disabled="running || !messages.length" @click="emit('clear')">
          <FaIcon name="i-ri:delete-bin-6-line" />
        </button>
        <button type="button" title="返回配置" aria-label="返回配置" @click="emit('close')">
          <FaIcon name="i-ri:close-line" />
        </button>
      </div>
    </header>

    <div ref="scrollEl" class="message-scroll">
      <div v-if="!messages.length" class="debug-empty">
        <span><FaIcon name="i-ri:chat-3-line" /></span>
        <strong>暂无调试记录</strong>
      </div>

      <article v-for="message in messages" :key="message.id" class="chat-message" :class="message.role">
        <div class="message-avatar">
          <FaIcon :name="message.role === 'user' ? 'i-ri:user-3-line' : 'i-ri:robot-2-line'" />
        </div>
        <div class="message-main">
          <div class="message-meta">
            <strong>{{ message.role === 'user' ? '你' : 'Agent' }}</strong>
            <span v-if="message.status === 'streaming'">生成中</span>
            <span v-else-if="message.status === 'failed'" class="failed">运行失败</span>
            <span v-else-if="message.status === 'cancelled'">已停止</span>
          </div>

          <div v-if="message.steps?.length" class="debug-steps">
            <div v-for="step in message.steps" :key="`${step.nodeId}-${step.status}`" class="debug-step" :class="step.status.toLowerCase()">
              <span class="step-icon"><FaIcon :name="statusIcon(step.status)" /></span>
              <span class="step-copy"><strong>{{ step.nodeTitle }}</strong><small>{{ step.message }}</small></span>
            </div>
          </div>

          <div v-if="message.tools?.length" class="tool-results">
            <div v-for="(tool, index) in message.tools" :key="`${tool.toolName}-${index}`">
              <FaIcon name="i-ri:tools-line" />
              <span><strong>{{ tool.toolName }}</strong><small>{{ tool.message || tool.action || '工具执行完成' }}</small></span>
            </div>
          </div>

          <div v-if="message.content || message.status === 'streaming'" class="message-content">
            {{ message.content }}<i v-if="message.status === 'streaming'" class="stream-caret" />
          </div>
        </div>
      </article>
    </div>

    <footer class="debug-composer">
      <FaTextarea v-model="input" class="w-full" :disabled="running" :autosize="{ minRows: 3, maxRows: 7 }" placeholder="输入调试消息" @keydown="onKeydown" />
      <div class="composer-footer">
        <span>{{ input.length }}/2000</span>
        <button v-if="running" type="button" class="send-button stop" title="停止调试" aria-label="停止调试" @click="emit('stop')">
          <FaIcon name="i-ri:stop-fill" />
        </button>
        <button v-else type="button" class="send-button" title="发送消息" aria-label="发送消息" :disabled="!input.trim()" @click="send">
          <FaIcon name="i-ri:send-plane-2-fill" />
        </button>
      </div>
    </footer>
  </aside>
</template>

<style scoped>
.debug-panel { display: grid; width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; grid-template-rows: 66px minmax(0, 1fr) auto; border-left: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.debug-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0 12px 0 14px; border-bottom: 1px solid var(--color-border-2); }
.debug-title { display: flex; min-width: 0; align-items: center; gap: 9px; }
.debug-mark { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 6px; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); }
.debug-title > span:last-child { display: grid; gap: 3px; }
.debug-title strong { color: var(--color-text-1); font-size: 13px; }
.debug-title small { display: flex; align-items: center; gap: 5px; color: var(--color-text-3); font-size: 9px; }
.debug-title small i { width: 6px; height: 6px; border-radius: 50%; background: var(--color-text-4); }
.debug-title small i.active { background: rgb(var(--success-6)); box-shadow: 0 0 0 3px rgb(var(--success-2)); }
.debug-actions { display: flex; gap: 4px; }
.debug-actions button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 5px; color: var(--color-text-3); background: transparent; cursor: pointer; }
.debug-actions button:hover { color: var(--color-text-1); background: var(--color-fill-2); }
.debug-actions button:disabled { color: var(--color-text-4); cursor: not-allowed; }
.message-scroll { min-height: 0; overflow: auto; padding: 16px 13px 22px; }
.debug-empty { display: grid; height: 100%; place-items: center; align-content: center; gap: 10px; color: var(--color-text-3); }
.debug-empty > span { display: grid; width: 46px; height: 46px; place-items: center; border-radius: 8px; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); font-size: 21px; }
.debug-empty strong { font-size: 11px; font-weight: 500; }
.chat-message { display: grid; grid-template-columns: 26px minmax(0, 1fr); align-items: start; gap: 8px; }
.chat-message + .chat-message { margin-top: 18px; }
.chat-message.user { direction: rtl; }
.chat-message.user > * { direction: ltr; }
.message-avatar { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 6px; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); font-size: 13px; }
.user .message-avatar { color: var(--color-text-2); background: var(--color-fill-3); }
.message-main { min-width: 0; }
.message-meta { display: flex; align-items: center; gap: 7px; margin-bottom: 5px; color: var(--color-text-3); font-size: 9px; }
.user .message-meta { justify-content: flex-end; }
.message-meta strong { color: var(--color-text-2); font-size: 10px; }
.message-meta .failed { color: rgb(var(--danger-6)); }
.message-content { padding: 9px 10px; border: 1px solid var(--color-border-2); border-radius: 6px; color: var(--color-text-1); background: var(--color-bg-1); font-size: 11px; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.user .message-content { border-color: rgb(var(--primary-3)); background: rgb(var(--primary-1)); }
.stream-caret { display: inline-block; width: 5px; height: 12px; margin-left: 2px; background: rgb(var(--primary-6)); animation: blink 0.85s infinite; vertical-align: -2px; }
.debug-steps { display: grid; gap: 1px; margin-bottom: 8px; padding: 7px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-fill-1); }
.debug-step { display: grid; min-height: 33px; grid-template-columns: 20px minmax(0, 1fr); align-items: center; gap: 5px; }
.step-icon { display: grid; width: 18px; height: 18px; place-items: center; border-radius: 50%; color: var(--color-text-3); background: var(--color-fill-3); font-size: 10px; }
.debug-step.running .step-icon { color: rgb(var(--primary-6)); background: rgb(var(--primary-2)); animation: spin 1.1s linear infinite; }
.debug-step.completed .step-icon { color: rgb(var(--success-6)); background: rgb(var(--success-1)); }
.debug-step.failed .step-icon { color: rgb(var(--danger-6)); background: rgb(var(--danger-1)); }
.step-copy { display: grid; min-width: 0; gap: 2px; }
.step-copy strong { color: var(--color-text-2); font-size: 10px; font-weight: 500; }
.step-copy small { overflow: hidden; color: var(--color-text-3); font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.tool-results { display: grid; gap: 5px; margin-bottom: 8px; }
.tool-results > div { display: grid; grid-template-columns: 20px minmax(0, 1fr); align-items: center; gap: 5px; padding: 6px 7px; border: 1px solid rgb(var(--warning-3)); border-radius: 5px; color: rgb(var(--warning-7)); background: rgb(var(--warning-1)); }
.tool-results span { display: grid; min-width: 0; gap: 1px; }
.tool-results strong { font-size: 9px; }
.tool-results small { overflow: hidden; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
.debug-composer { padding: 10px 12px 12px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.composer-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 7px; }
.composer-footer > span { color: var(--color-text-4); font-size: 8px; }
.send-button { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 6px; color: white; background: rgb(var(--primary-6)); cursor: pointer; }
.send-button.stop { background: rgb(var(--danger-6)); }
.send-button:disabled { color: var(--color-text-4); background: var(--color-fill-3); cursor: not-allowed; }
@keyframes blink { 50% { opacity: 0; } }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
