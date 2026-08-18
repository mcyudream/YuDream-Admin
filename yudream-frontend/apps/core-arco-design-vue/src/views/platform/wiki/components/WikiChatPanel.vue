<script setup lang="ts">
import type { YdChatCitation, YdChatGraphNode, YdChatRetrievalHit } from '@yudream/components'
import { computed, inject, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useYdChatStream, YdChatMessageList, YdChatSender } from '@yudream/components'
import { wikiChatAguiEndpoint } from '@/api/modules/platform-wiki'
import { resolveApiFileUrl, rewriteApiFileUrls } from '@/utils/api-file-url'
import { resolveWikiLink, wikilinksToMarkdown, wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()
const router = useRouter()

const spaceName = computed(() => store.space.value?.name || '')
const spaceSlug = computed(() => store.space.value?.slug || '')

// wikilink 转链接 + 站内文件地址补 dev 代理前缀
const transformContent = (content: string) => rewriteApiFileUrls(wikilinksToMarkdown(content))

const suggestions = computed(() => store.spaceId.value
  ? ['这个知识库主要讲什么？', '帮我梳理一下内容目录', '最近有哪些新摄入的内容？']
  : [])

const { messages, streaming, toolHint, send, stop, clear } = useYdChatStream({
  endpoint: () => wikiChatAguiEndpoint(store.spaceId.value),
  protocol: 'agui',
  transport: 'sse',
  historyLimit: 10,
})

function ask(text: string) {
  if (!store.spaceId.value) {
    toast.warning('请先选择知识库')
    return
  }
  void send(text)
}

function onContentClick(event: MouseEvent) {
  const title = resolveWikiLink(event)
  if (title) {
    store.openPage({ title })
  }
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

// 切知识库清空对话
watch(() => store.spaceId.value, () => clear())

/** 在统一 AI 助手聊天页打开当前知识库问答 */
function openInChatPage() {
  router.push({ path: '/platform/chat', query: { scopeType: 'WIKI', spaceSlug: spaceSlug.value } })
}
</script>

<template>
  <div class="chat-panel">
    <header class="chat-head">
      <div class="chat-head__title">
        <FaIcon name="i-ri:chat-smile-3-line" />
        <strong>智能问答</strong>
        <span v-if="spaceName">基于「{{ spaceName }}」检索回答</span>
      </div>
      <div class="chat-head__side">
        <span v-if="streaming && toolHint" class="chat-head__tool">
          <FaIcon name="i-ri:loader-4-line" class="chat-head__spin" /> {{ toolHint }}
        </span>
        <FaTooltip text="在 AI 助手中打开">
          <FaButton size="icon-sm" variant="ghost" @click="openInChatPage">
            <FaIcon name="i-ri:external-link-line" />
          </FaButton>
        </FaTooltip>
        <FaTooltip text="清空对话">
          <FaButton size="icon-sm" variant="ghost" :disabled="!messages.length" @click="clear">
            <FaIcon name="i-ri:delete-bin-line" />
          </FaButton>
        </FaTooltip>
      </div>
    </header>

    <YdChatMessageList
      :messages="messages"
      :transform-content="transformContent"
      :image-url-resolver="resolveApiFileUrl"
      @content-click="onContentClick"
      @citation-click="onCitationClick"
      @retrieval-click="onRetrievalClick"
      @graph-node-click="onGraphNodeClick"
    >
      <template #empty>
        <div class="chat-empty">
          <div class="chat-empty__icon"><FaIcon name="i-ri:chat-smile-3-line" /></div>
          <strong>向知识库提问</strong>
          <p>回答会实时流式输出，自动检索 Wiki 页面并附带来源引用，引用可点击跳转</p>
        </div>
      </template>
    </YdChatMessageList>

    <YdChatSender
      :loading="streaming"
      :suggestions="messages.length ? [] : suggestions"
      @send="ask"
      @stop="stop"
      @suggestion-click="ask"
    />
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

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.chat-head__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.chat-head__title :deep(svg) {
  color: rgb(var(--primary-6));
}

.chat-head__title span {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-head__side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.chat-head__tool {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: rgb(var(--primary-6));
  font-size: 12px;
}

.chat-head__spin {
  animation: chat-spin 1s linear infinite;
}

@keyframes chat-spin {
  to { transform: rotate(360deg); }
}

.chat-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
}

.chat-empty__icon {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  border-radius: 16px;
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
  font-size: 28px;
}

.chat-empty strong {
  color: var(--color-text-1);
  font-size: 16px;
}

.chat-empty p {
  margin: 0;
  font-size: 12px;
  text-align: center;
}
</style>
