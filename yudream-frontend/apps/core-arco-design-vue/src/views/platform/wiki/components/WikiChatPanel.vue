<script setup lang="ts">
import type { YdAgentRenderDocument, YdAgentSendRequest, YdChatCitation, YdChatGraphNode, YdChatRetrievalHit } from '@yudream/components'
import { YdAgentWorkbench, YdAlert } from '@yudream/components'
import { computed, inject } from 'vue'
import { useRouter } from 'vue-router'
import { startWikiChatRun } from '@/api/modules/platform-wiki'
import { useAiRunTask } from '@/composables/use-ai-run-task'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const router = useRouter()

const spaceName = computed(() => store.space.value?.name || '')
const spaceSlug = computed(() => store.space.value?.slug || '')

const runtime = useAiAssistantStore()
const sessionKey = computed(() => store.spaceId.value ? `wiki:${store.spaceId.value}` : undefined)
const task = useAiRunTask(sessionKey)
const messages = computed(() => sessionKey.value ? runtime.taskMessages(sessionKey.value) : [])
const renderDocuments = computed<Record<string, YdAgentRenderDocument>>(() => Object.fromEntries(messages.value.filter(message => message.id).map((message) => {
  const references: YdAgentRenderDocument['blocks'] = []
  const text = message.content.replace(/\[\[([^\][|]+)(?:\|([^\]]+))?\]\]/g, (original, rawTitle: string) => {
    const node = store.flatTree.value.find(node => node.title === rawTitle.trim())
    if (!node?.id) {
      return original
    }
    const id = `node:${node.id}`
    if (!references.some(block => block.type === 'reference' && block.reference.id === id)) {
      references.push({ type: 'reference', reference: { id, title: node.title, excerpt: '', path: node.path } })
    }
    return `[[ref:${id}]]`
  })
  return [message.id!, { version: 1, blocks: [{ type: 'markdown', text }, ...references, ...(message.tools || []).map((tool, index) => ({ type: 'tool' as const, tool: {
    id: tool.toolCallId || `${message.id}:tool:${index}`,
    name: tool.toolName || '工具调用',
    status: tool.status === 'executing' ? 'running' as const : tool.status === 'error' ? 'error' as const : tool.status === 'cancelled' ? 'cancelled' as const : 'success' as const,
    description: tool.message,
    output: tool.payload ? JSON.stringify(tool.payload, null, 2) : undefined,
  } }))] }]
})))
const harness = computed(() => ({ name: 'AgentScope Harness', runId: task.run.value?.id, status: task.running.value ? task.run.value?.status === 'WAITING' ? 'waiting' as const : 'running' as const : task.run.value?.status === 'FAILED' ? 'error' as const : task.run.value?.status === 'CANCELLED' ? 'cancelled' as const : task.run.value ? 'completed' as const : 'idle' as const }))

const suggestions = computed(() => store.spaceId.value
  ? ['这个知识库主要讲什么？', '帮我梳理一下内容目录', '最近有哪些新摄入的内容？']
  : [])

function ask(request: YdAgentSendRequest) {
  if (!store.spaceId.value) {
    toast.warning('请先选择知识库')
    return
  }
  if (!sessionKey.value || task.running.value) {
    return
  }
  runtime.appendTaskInput(sessionKey.value, request.text)
  void task.start(() => startWikiChatRun(store.spaceId.value, { question: request.text, sessionId: sessionKey.value! }))
}

function onCitationClick(citation: YdChatCitation) {
  store.openPage({ nodeId: citation.nodeId, title: citation.title, path: citation.path })
}

function onRetrievalClick(hit: YdChatRetrievalHit) {
  store.openPage({ nodeId: hit.nodeId, title: hit.title, path: hit.path })
}

function onGraphNodeClick(node: YdChatGraphNode) {
  store.openPage({ nodeId: node.id, title: node.title, path: node.path })
}

/** 在统一 AI 助手聊天页打开当前知识库问答 */
function openInChatPage() {
  router.push({ path: '/platform/chat', query: { scopeType: 'WIKI', spaceSlug: spaceSlug.value } })
}
</script>

<template>
  <div class="chat-panel">
    <header class="chat-head">
      <div class="chat-head__title">
        <YdIcon name="morph:message-circle-more" />
        <strong>智能问答</strong>
        <span v-if="spaceName">基于「{{ spaceName }}」检索回答</span>
      </div>
      <div class="chat-head__side">
        <YdTooltip text="在 AI 助手中打开">
          <YdButton size="icon-sm" variant="ghost" @click="openInChatPage">
            <YdIcon name="morph:external-link" />
          </YdButton>
        </YdTooltip>
        <YdTooltip text="恢复后端会话">
          <YdButton size="icon-sm" variant="ghost" @click="task.reconnect">
            <YdIcon name="morph:refresh-cw" />
          </YdButton>
        </YdTooltip>
      </div>
    </header>

    <YdAlert v-if="task.failure.value" variant="destructive" :title="task.failure.value" />
    <YdAgentWorkbench
      :messages="messages"
      :render-documents="renderDocuments"
      :harness="harness"
      :available-modes="['ask']"
      :suggestions="suggestions"
      :disabled="!store.spaceId.value"
      class="wiki-agent-workbench"
      @send="ask"
      @stop="task.stop"
      @citation-select="onCitationClick"
      @retrieval-select="onRetrievalClick"
      @graph-node-select="onGraphNodeClick"
      @reference-select="reference => store.openPage({ nodeId: reference.id.startsWith('node:') ? reference.id.slice(5) : undefined, title: reference.title, path: reference.path })"
    >
      <template #empty>
        <div class="chat-empty">
          <div class="chat-empty__icon">
            <YdIcon name="morph:message-circle-more" />
          </div>
          <strong>向知识库提问</strong>
          <p>回答会实时流式输出，自动检索 Wiki 页面并附带来源引用，引用可点击跳转</p>
        </div>
      </template>
    </YdAgentWorkbench>
  </div>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.wiki-agent-workbench {
  flex: 1;
  height: auto;
  min-height: 0;
}

.chat-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: var(--color-bg-1);
  border-bottom: 1px solid var(--color-border-2);
}

.chat-head__title {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.chat-head__title :deep(svg) {
  color: var(--color-text-2);
}

.chat-head__title span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.chat-head__side {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
}

.chat-head__tool {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
}

.chat-head__spin {
  animation: chat-spin 1s linear infinite;
}

@keyframes chat-spin {
  to { transform: rotate(360deg); }
}

.chat-empty {
  display: grid;
  gap: 8px;
  justify-items: center;
}

.chat-empty__icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  font-size: 28px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 16px;
}

.chat-empty strong {
  font-size: 16px;
  color: var(--color-text-1);
}

.chat-empty p {
  margin: 0;
  font-size: 12px;
  text-align: center;
}
</style>
