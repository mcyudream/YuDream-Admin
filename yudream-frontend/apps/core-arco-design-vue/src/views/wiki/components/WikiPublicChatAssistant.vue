<script setup lang="ts">
import type { YdAgentRenderDocument, YdAgentSendRequest, YdChatCitation, YdChatGraphNode, YdChatRetrievalHit } from '@yudream/components'
import { YdAgentWorkbench, YdAlert, YdChatWindow } from '@yudream/components'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { startPublicWikiChatRun } from '@/api/modules/platform-wiki'
import { useAiRunTask } from '@/composables/use-ai-run-task'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { useAppAccountStore } from '@/store/modules/app/account'
import { describeAiRunError } from '@/utils/ai-run-error'
import { resolveApiFileUrl, rewriteApiFileUrls } from '@/utils/api-file-url'

const props = defineProps<{
  slug: string
  spaceName?: string
}>()

const emits = defineEmits<{
  citationSelect: [citation: YdChatCitation]
}>()

const router = useRouter()
const appAccountStore = useAppAccountStore()
const open = ref(false)
const isLogin = computed(() => appAccountStore.isLogin)
const { auth } = useAppAuth()
const canOpenChat = computed(() => auth('platform:chat:session:view') && auth('platform:chat:use'))
const runtime = useAiAssistantStore()
const sessionKey = computed(() => isLogin.value && props.slug ? `public-wiki:${props.slug}` : undefined)
const task = useAiRunTask(sessionKey)
const messages = computed(() => sessionKey.value ? runtime.taskMessages(sessionKey.value) : [])
const harness = computed(() => ({ name: 'AgentScope Harness', runId: task.run.value?.id, status: task.running.value ? task.run.value?.status === 'WAITING' ? 'waiting' as const : 'running' as const : task.run.value?.status === 'FAILED' ? 'error' as const : task.run.value?.status === 'CANCELLED' ? 'cancelled' as const : task.run.value ? 'completed' as const : 'idle' as const }))
const renderDocuments = computed<Record<string, YdAgentRenderDocument>>(() => Object.fromEntries(messages.value.filter(message => message.id).map((message) => {
  const references: YdAgentRenderDocument['blocks'] = []
  const text = rewriteApiFileUrls(message.content).replace(/\[\[([^\][|]+)(?:\|([^\]]+))?\]\]/g, (original, rawTitle: string) => {
    const citation = message.citations?.find(citation => citation.title === rawTitle.trim())
    if (!citation) {
      return original
    }
    const id = `citation:${citation.nodeId || citation.title}`
    if (!references.some(block => block.type === 'reference' && block.reference.id === id)) {
      references.push({ type: 'reference', reference: { id, title: citation.title, excerpt: citation.excerpt || '', path: citation.path } })
    }
    return `[[ref:${id}]]`
  })
  return [message.id!, { version: 1, blocks: [{ type: 'markdown', text }, ...references, ...(message.tools || []).map((tool, index) => ({ type: 'tool' as const, tool: { id: tool.toolCallId || `${message.id}:tool:${index}`, name: tool.toolName || '工具调用', status: tool.status === 'executing' ? 'running' as const : tool.status === 'error' ? 'error' as const : tool.status === 'cancelled' ? 'cancelled' as const : 'success' as const, description: tool.message, output: tool.payload ? JSON.stringify(tool.payload, null, 2) : undefined } }))] }]
})))

function send(request: YdAgentSendRequest) {
  if (!sessionKey.value || task.running.value || !request.text.trim()) {
    return
  }
  runtime.appendTaskInput(sessionKey.value, request.text)
  void task.start(() => startPublicWikiChatRun(props.slug, request.text))
}

const suggestions = ['这个知识库主要讲什么？', '帮我梳理一下内容目录', '如何快速上手？']

function onCitationClick(citation: YdChatCitation) {
  emits('citationSelect', citation)
}

function onReferenceSelect(reference: { title: string, path?: string }) {
  const citation = messages.value.flatMap(message => message.citations || []).find(citation => citation.title === reference.title && citation.path === reference.path)
  onCitationClick(citation || { title: reference.title, path: reference.path })
}

function onRetrievalClick(hit: YdChatRetrievalHit) {
  emits('citationSelect', hit)
}

function onGraphNodeClick(node: YdChatGraphNode) {
  emits('citationSelect', { nodeId: node.id, title: node.title, path: node.path })
}

function toggle() {
  open.value = !open.value
}

/** 最大化/全屏：跳转到统一 AI 助手聊天页并带上知识库上下文 */
function openInChatPage() {
  if (!canOpenChat.value) {
    return
  }
  open.value = false
  router.push({ path: '/platform/chat', query: { scopeType: 'WIKI', spaceSlug: props.slug } })
}

function goLogin() {
  window.location.href = '/login'
}
</script>

<template>
  <div class="wiki-assistant" :class="{ 'is-open': open }">
    <Transition name="wiki-assistant-panel">
      <YdChatWindow
        v-if="open"
        :title="`文档助手${spaceName ? ` · ${spaceName}` : ''}`"
        :width="420"
        :height="620"
        :min-width="360"
        :min-height="420"
        :expand-only="canOpenChat"
        @close="toggle"
        @expand="openInChatPage"
      >
        <YdAlert v-if="task.failure.value" variant="destructive" :title="describeAiRunError(task.failure.value).message" :description="task.failure.value" />
        <YdAgentWorkbench
          :messages="messages"
          :render-documents="renderDocuments"
          :harness="harness"
          :image-url-resolver="resolveApiFileUrl"
          :available-modes="['ask']"
          :suggestions="isLogin ? suggestions : []"
          :disabled="!isLogin"
          class="wiki-assistant__workbench"
          @send="send"
          @stop="task.stop"
          @citation-select="onCitationClick"
          @retrieval-select="onRetrievalClick"
          @graph-node-select="onGraphNodeClick"
          @reference-select="onReferenceSelect"
        >
          <template #empty>
            <div v-if="!isLogin" class="wiki-assistant__empty">
              <YdIcon name="morph:log-in" />
              <strong>登录后即可提问</strong>
              <p>登录后可在知识库助手中进行流式问答。</p>
              <YdButton type="button" variant="outline" class="wiki-assistant__login" @click="goLogin">
                去登录
              </YdButton>
            </div>
            <div v-else class="wiki-assistant__empty">
              <YdIcon name="morph:message-circle-more" />
              <strong>关于这份文档，有什么想问的？</strong>
              <p>我会基于知识库内容回答，并附上可跳转的引用来源。</p>
            </div>
          </template>
        </YdAgentWorkbench>
      </YdChatWindow>
    </Transition>

    <YdButton type="button" variant="outline" class="wiki-assistant__fab" :aria-expanded="open" @click="toggle">
      <YdIcon :name="open ? 'morph:x' : 'morph:message-circle-more'" />
      <span v-if="!open">文档助手</span>
    </YdButton>
  </div>
</template>

<style scoped>
.wiki-assistant__workbench {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.wiki-assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  display: grid;
  gap: 12px;
  justify-items: end;
}

.wiki-assistant__fab {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 11px 16px;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-bg-1);
  cursor: pointer;
  background: var(--color-text-1);
  border: 1px solid var(--color-text-1);
  border-radius: 999px;
  box-shadow: 0 10px 24px rgb(0 0 0 / 20%);
  transition: transform 0.15s, box-shadow 0.15s;
}

.wiki-assistant__fab:hover {
  box-shadow: 0 14px 30px rgb(0 0 0 / 25%);
  transform: translateY(-2px);
}

.wiki-assistant__fab :deep(svg) {
  font-size: 18px;
}

.wiki-assistant__panel {
  display: flex;
  flex-direction: column;
  width: min(420px, calc(100vw - 32px));
  height: min(620px, calc(100vh - 100px));
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgb(0 0 0 / 20%);
}

.wiki-assistant__head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--color-fill-1);
  border-bottom: 1px solid var(--color-border-2);
}

.wiki-assistant__head-main {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.wiki-assistant__mark {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  color: var(--color-text-1);
  background: var(--color-fill-1);
  border-radius: 10px;
}

.wiki-assistant__head-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.wiki-assistant__head-text strong {
  font-size: 14px;
  color: var(--color-text-1);
}

.wiki-assistant__head-text span {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.wiki-assistant__head-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.wiki-assistant__hint {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-1);
  white-space: nowrap;
}

.wiki-assistant__icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--color-text-3);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 7px;
}

.wiki-assistant__icon:hover:not(:disabled) {
  color: var(--color-text-1);
  background: var(--color-fill-3);
}

.wiki-assistant__icon:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wiki-assistant__empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 44px 24px;
  color: var(--color-text-3);
  text-align: center;
}

.wiki-assistant__empty :deep(svg) {
  font-size: 28px;
  color: var(--color-text-1);
}

.wiki-assistant__empty strong {
  font-size: 15px;
  color: var(--color-text-1);
}

.wiki-assistant__empty p {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
}

.wiki-assistant__login {
  padding: 7px 16px;
  margin-top: 8px;
  font: inherit;
  font-size: 13px;
  color: var(--color-bg-1);
  cursor: pointer;
  background: var(--color-text-1);
  border: 1px solid var(--color-text-1);
  border-radius: 999px;
}

.wiki-assistant-panel-enter-active,
.wiki-assistant-panel-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.wiki-assistant-panel-enter-from,
.wiki-assistant-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

@media (width <= 560px) {
  .wiki-assistant {
    right: 14px;
    bottom: 14px;
  }
}
</style>
