<script setup lang="ts">
import type { YdChatCitation, YdChatGraphNode, YdChatRetrievalHit } from '@yudream/components'
import { useYdChatStream, YdChatMessageList, YdChatSender, YdChatWindow } from '@yudream/components'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { chatStreamOnceEndpoint } from '@/api/modules/platform-chat'
import { wikilinksToMarkdown } from '../../platform/wiki/wiki-utils'
import { resolveApiFileUrl, rewriteApiFileUrls } from '@/utils/api-file-url'
import { useAppAccountStore } from '@/store/modules/app/account'

// wikilink 转链接 + 站内文件地址补 dev 代理前缀
const transformContent = (content: string) => rewriteApiFileUrls(wikilinksToMarkdown(content))

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
const { messages, streaming, send, stop } = useYdChatStream({
  endpoint: chatStreamOnceEndpoint,
  protocol: 'agui',
  transport: 'sse',
  historyLimit: 10,
  buildBody: (question, history) => ({
    scopeType: 'WIKI',
    spaceSlug: props.slug,
    question,
    history,
  }),
})

const suggestions = ['这个知识库主要讲什么？', '帮我梳理一下内容目录', '如何快速上手？']

function onCitationClick(citation: YdChatCitation) {
  emits('citationSelect', citation)
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
        :title="`文档助手${spaceName ? ' · ' + spaceName : ''}`"
        :width="420"
        :height="620"
        :min-width="360"
        :min-height="420"
        expand-only
        @close="toggle"
        @expand="openInChatPage"
      >
        <YdChatMessageList
          :messages="messages"
          :transform-content="transformContent"
          :image-url-resolver="resolveApiFileUrl"
          compact
          @citation-click="onCitationClick"
          @retrieval-click="onRetrievalClick"
          @graph-node-click="onGraphNodeClick"
        >
          <template #empty>
            <div v-if="!isLogin" class="wiki-assistant__empty">
              <FaIcon name="i-ri:login-circle-line" />
              <strong>登录后即可提问</strong>
              <p>登录后可在知识库助手中进行流式问答。</p>
              <button type="button" class="wiki-assistant__login" @click="goLogin">去登录</button>
            </div>
            <div v-else class="wiki-assistant__empty">
              <FaIcon name="i-ri:chat-smile-3-line" />
              <strong>关于这份文档，有什么想问的？</strong>
              <p>我会基于知识库内容回答，并附上可跳转的引用来源。</p>
            </div>
          </template>
        </YdChatMessageList>

        <YdChatSender
          v-if="isLogin"
          :loading="streaming"
          :suggestions="messages.length ? [] : suggestions"
          placeholder="输入问题，Enter 发送（Shift+Enter 换行）"
          @send="send"
          @stop="stop"
          @suggestion-click="send"
        />
      </YdChatWindow>
    </Transition>

    <button type="button" class="wiki-assistant__fab" @click="toggle">
      <FaIcon :name="open ? 'i-ri:close-line' : 'i-ri:chat-smile-3-line'" />
      <span v-if="!open">文档助手</span>
    </button>
  </div>
</template>

<style scoped>
.wiki-assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1000;
  display: grid;
  justify-items: end;
  gap: 12px;
}

.wiki-assistant__fab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 16px;
  border: 1px solid var(--color-text-1);
  border-radius: 999px;
  background: var(--color-text-1);
  color: var(--color-bg-1);
  cursor: pointer;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 10px 24px rgb(0 0 0 / 20%);
  transition: transform 0.15s, box-shadow 0.15s;
}

.wiki-assistant__fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgb(0 0 0 / 25%);
}

.wiki-assistant__fab :deep(svg) {
  font-size: 18px;
}

.wiki-assistant__panel {
  display: flex;
  overflow: hidden;
  width: min(420px, calc(100vw - 32px));
  height: min(620px, calc(100vh - 100px));
  border: 1px solid var(--color-border-2);
  border-radius: 16px;
  background: var(--color-bg-2);
  flex-direction: column;
  box-shadow: 0 24px 60px rgb(0 0 0 / 20%);
}

.wiki-assistant__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-fill-1);
}

.wiki-assistant__head-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.wiki-assistant__mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 10px;
  background: var(--color-fill-1);
  color: var(--color-text-1);
  font-size: 18px;
}

.wiki-assistant__head-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.wiki-assistant__head-text strong {
  color: var(--color-text-1);
  font-size: 14px;
}

.wiki-assistant__head-text span {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-assistant__head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.wiki-assistant__hint {
  max-width: 150px;
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wiki-assistant__icon {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  place-items: center;
}

.wiki-assistant__icon:hover:not(:disabled) {
  background: var(--color-fill-3);
  color: var(--color-text-1);
}

.wiki-assistant__icon:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.wiki-assistant__empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 44px 24px;
  color: var(--color-text-3);
  text-align: center;
}

.wiki-assistant__empty :deep(svg) {
  color: var(--color-text-1);
  font-size: 28px;
}

.wiki-assistant__empty strong {
  color: var(--color-text-1);
  font-size: 15px;
}

.wiki-assistant__empty p {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
}

.wiki-assistant__login {
  margin-top: 8px;
  padding: 7px 16px;
  border: 1px solid var(--color-text-1);
  border-radius: 999px;
  background: var(--color-text-1);
  color: var(--color-bg-1);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
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

@media (max-width: 560px) {
  .wiki-assistant {
    right: 14px;
    bottom: 14px;
  }
}
</style>
