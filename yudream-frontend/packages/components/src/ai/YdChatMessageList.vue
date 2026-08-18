<script setup lang="ts">
import type { YdChatAction, YdChatCitation, YdChatGraphNode, YdChatMessage, YdChatRetrievalHit, YdChatToolEvent } from './useYdChatStream'
import { computed, nextTick, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import FaIcon from '../basic/icon/index.vue'
import YdAttachmentList from './YdAttachmentList.vue'
import YdCitationList from './YdCitationList.vue'
import YdChatProcess from './YdChatProcess.vue'
import { useYdChatConfig } from './chat-context'

const props = withDefaults(defineProps<{
  messages: YdChatMessage[]
  /** 渲染前的内容转换（如 wikilink → markdown 链接） */
  transformContent?: (content: string) => string
  /** 生成中（等待首字节）的提示 */
  thinkingText?: string
  /** 深度思考标题 */
  reasoningTitle?: string
  /** 紧凑模式，适用于 420px 宽的公开助手 */
  compact?: boolean
  /** 引用图片地址解析（如开发环境补代理前缀），透传给引用列表 */
  imageUrlResolver?: (url: string) => string
}>(), {
  transformContent: undefined,
  thinkingText: undefined,
  reasoningTitle: undefined,
  compact: false,
  imageUrlResolver: undefined,
})

const config = useYdChatConfig()
const thinkingHint = computed(() => props.thinkingText ?? config.thinkingText ?? '正在检索并思考…')
const reasoningLabel = computed(() => props.reasoningTitle ?? config.reasoningTitle ?? '深度思考')

function rendered(content: string): string {
  const transform = props.transformContent ?? config.transformContent
  return transform ? transform(content) : content
}

const emits = defineEmits<{
  /** assistant 气泡内容上的点击（可拦截 wiki:// 等自定义链接） */
  contentClick: [event: MouseEvent]
  citationClick: [citation: YdChatCitation]
  retrievalClick: [hit: YdChatRetrievalHit]
  graphNodeClick: [node: YdChatGraphNode]
  actionClick: [action: YdChatAction]
  copyMessage: [message: YdChatMessage]
  regenerateMessage: [message: YdChatMessage]
}>()

const listEl = ref<HTMLElement | null>(null)
const expandedReasoning = ref<Record<number, boolean>>({})
/** 用户上翻离开底部时显示「回到底部」悬浮钮 */
const scrolledUp = ref(false)

function toggleReasoning(index: number) {
  expandedReasoning.value[index] = !expandedReasoning.value[index]
}

function toolLabel(tool: YdChatToolEvent): string {
  if (tool.toolName === 'wiki.search' || tool.toolName === 'web.fetch' || tool.toolName === 'web_fetch') {
    return '检索知识库'
  }
  if (tool.toolName) {
    return tool.toolName
  }
  return tool.status === 'complete' ? '工具已完成' : '调用工具'
}

function isNearBottom(): boolean {
  const el = listEl.value
  if (!el) {
    return true
  }
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = listEl.value
    if (el && (force || isNearBottom())) {
      el.scrollTop = el.scrollHeight
    }
    scrolledUp.value = false
  })
}

function onListScroll() {
  scrolledUp.value = !isNearBottom()
}

// 新消息强制滚到底；流式增量仅在用户本就在底部时跟随
watch(() => props.messages.length, () => scrollToBottom(true))
watch(() => {
  const last = props.messages[props.messages.length - 1]
  return last
    ? last.content.length + (last.reasoning?.length ?? 0) + (last.tools?.length ?? 0) + (last.activities?.length ?? 0) + (last.activities?.reduce((total, activity) => total + (activity.hits?.length ?? 0) + (activity.graph?.nodes.length ?? 0) + (activity.graph?.edges.length ?? 0), 0) ?? 0)
    : 0
}, () => scrollToBottom())

defineExpose({ scrollToBottom })
</script>

<template>
  <div ref="listEl" class="yd-chat-list" @scroll.passive="onListScroll">
    <div v-if="!messages.length" class="yd-chat-list__empty">
      <slot name="empty" />
    </div>

    <template v-for="(message, index) in messages" :key="message.id ?? index">
      <!-- 用户消息：右侧气泡 -->
      <div v-if="message.role === 'user'" class="yd-msg yd-msg--user">
        <div class="yd-msg__body yd-msg__body--user">
          <YdAttachmentList :attachments="message.attachments ?? []" />
          <div class="yd-bubble yd-bubble--user">{{ message.content }}</div>
        </div>
      </div>

      <!-- 助手消息 -->
      <div v-else class="yd-msg">
        <div class="yd-msg__avatar"><FaIcon name="i-ri:sparkling-2-line" /></div>
        <div class="yd-msg__body">
          <!-- 深度思考 -->
          <section v-if="message.reasoning" class="yd-reasoning">
            <button type="button" class="yd-reasoning__head" @click="toggleReasoning(index)">
              <FaIcon name="i-ri:brain-line" />
              <span>{{ reasoningLabel }}</span>
              <FaIcon :name="expandedReasoning[index] ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" class="yd-reasoning__chevron" />
            </button>
            <div v-show="expandedReasoning[index]" class="yd-reasoning__body">
              {{ message.reasoning }}
            </div>
          </section>

          <YdChatProcess
            v-if="message.activities?.length"
            :activities="message.activities"
            :compact="compact"
            @retrieval-select="emits('retrievalClick', $event)"
            @graph-node-select="emits('graphNodeClick', $event)"
          />

          <!-- 工具调用状态 -->
          <div v-if="message.tools?.length" class="yd-tools">
            <div
              v-for="(tool, toolIndex) in message.tools"
              :key="tool.toolCallId ?? toolIndex"
              class="yd-tool"
              :class="{ 'is-complete': tool.status === 'complete', 'is-error': tool.status === 'error' }"
            >
              <FaIcon
                :name="tool.status === 'complete' ? 'i-ri:checkbox-circle-line' : tool.status === 'error' ? 'i-ri:error-warning-line' : 'i-ri:loader-4-line'"
                class="yd-tool__icon"
                :class="{ 'yd-tool__icon--spin': tool.status !== 'complete' && tool.status !== 'error' }"
              />
              <span class="yd-tool__name">{{ toolLabel(tool) }}</span>
              <span v-if="tool.message" class="yd-tool__message">{{ tool.message }}</span>
            </div>
          </div>

          <!-- 思考占位 -->
          <div
            v-if="message.pending && !message.content && !message.reasoning && !message.tools?.length && !message.activities?.length"
            class="yd-bubble yd-bubble--assistant yd-bubble--thinking"
          >
            <span class="yd-dot" /><span class="yd-dot" /><span class="yd-dot" />
            {{ thinkingHint }}
          </div>

          <!-- 正文 -->
          <div
            v-if="message.content || message.pending"
            class="yd-bubble yd-bubble--assistant"
            :class="{ 'yd-bubble--error': message.error }"
            @click="emits('contentClick', $event)"
          >
            <MdPreview
              :model-value="rendered(message.content)"
              language="zh-CN"
              preview-theme="github"
              code-theme="github"
              class="yd-markdown"
            />
            <span v-if="message.pending" class="yd-caret" />
          </div>

          <YdCitationList
            v-if="!message.error && message.citations?.length"
            :citations="message.citations"
            :image-url-resolver="imageUrlResolver"
            @select="emits('citationClick', $event)"
          />

          <div v-if="!message.error && message.content && !message.pending" class="yd-message-tools">
            <button type="button" class="yd-message-tool" @click="emits('copyMessage', message)">
              <FaIcon name="i-ri:file-copy-line" />
              <span>复制</span>
            </button>
            <button type="button" class="yd-message-tool" @click="emits('regenerateMessage', message)">
              <FaIcon name="i-ri:refresh-line" />
              <span>重新生成</span>
            </button>
          </div>

          <div v-if="message.actions?.length" class="yd-actions">
            <button
              v-for="action in message.actions"
              :key="action.label"
              type="button"
              class="yd-action"
              @click="emits('actionClick', action)"
            >
              <FaIcon v-if="action.action === 'copy'" name="i-ri:file-copy-line" />
              <FaIcon v-else-if="action.action === 'open'" name="i-ri:external-link-line" />
              <FaIcon v-else name="i-ri:arrow-right-line" />
              {{ action.label }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- 回到底部：sticky 吸附在滚动容器底部，不占文档流高度 -->
    <button
      v-show="scrolledUp"
      type="button"
      class="yd-chat-list__to-bottom"
      title="回到底部"
      @click="scrollToBottom(true)"
    >
      <FaIcon name="i-ri:arrow-down-line" />
    </button>
  </div>
</template>

<style scoped>
.yd-chat-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px clamp(16px, 8vw, 120px) 20px;
}

.yd-chat-list__empty {
  display: grid;
  justify-items: center;
  padding: 70px 0;
  color: var(--color-text-3);
}

/* 回到底部悬浮钮（sticky 底部吸附，按钮自身高度被负 margin 抵消） */
.yd-chat-list__to-bottom {
  position: sticky;
  z-index: 5;
  bottom: 12px;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  margin-top: -34px;
  margin-left: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 50%;
  background: var(--color-bg-1);
  color: var(--color-text-2);
  cursor: pointer;
  font-size: 16px;
  box-shadow: 0 4px 16px rgb(0 0 0 / 10%);
  transition: color 0.15s, border-color 0.15s, transform 0.15s;
}

.yd-chat-list__to-bottom:hover {
  border-color: var(--color-border-3);
  color: var(--color-text-1);
  transform: translateY(1px);
}

.yd-msg {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.yd-msg--user {
  justify-content: flex-end;
}

.yd-msg__avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 9px;
  background: rgba(var(--primary-6), 0.12);
  color: rgb(var(--primary-6));
  font-size: 15px;
}

.yd-msg__body {
  max-width: 100%;
  min-width: 0;
}

.yd-msg--user .yd-msg__body {
  display: flex;
  max-width: 82%;
  flex-direction: column;
  align-items: flex-end;
}

.yd-bubble {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14.5px;
  line-height: 1.75;
}

.yd-bubble--user {
  max-width: 100%;
  padding: 11px 16px;
  border-radius: 18px;
  border-bottom-right-radius: 6px;
  background: rgba(var(--primary-6), 0.1);
  color: var(--color-text-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.yd-bubble--assistant {
  padding: 4px 0;
  border: 0;
  background: transparent;
  border-radius: 0;
}

.yd-bubble--error {
  border-color: rgb(var(--danger-6, 245 63 63) / 33%);
  background: rgb(var(--danger-6, 245 63 63) / 6%);
  color: rgb(var(--danger-6, 245 63 63));
}

.yd-markdown :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.yd-markdown :deep(.md-editor-preview) {
  background: transparent;
  color: inherit;
  font-size: 14px;
}

.yd-markdown :deep(a[href^='wiki://']) {
  border-bottom: 1px dashed rgba(var(--primary-6), 0.5);
  color: rgb(var(--primary-6));
  cursor: pointer;
  text-decoration: none;
}

/* 深度思考 */
.yd-reasoning {
  margin-bottom: 10px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-fill-1);
}

.yd-reasoning__head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 7px 12px;
  border: 0;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

.yd-reasoning__head:hover {
  color: var(--color-text-1);
}

.yd-reasoning__head :deep(svg:first-child) {
  color: rgb(var(--primary-6));
}

.yd-reasoning__chevron {
  margin-left: auto;
  font-size: 14px;
}

.yd-reasoning__body {
  padding: 2px 12px 10px;
  color: var(--color-text-3);
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 工具状态 */
.yd-tools {
  display: grid;
  gap: 6px;
  margin-bottom: 10px;
}

.yd-tool {
  display: flex;
  align-items: center;
  gap: 7px;
  width: fit-content;
  max-width: 100%;
  padding: 6px 11px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-1);
  color: var(--color-text-2);
  font-size: 12px;
}

.yd-tool__icon {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
}

.yd-tool__icon--spin {
  animation: yd-chat-spin 1s linear infinite;
}

.yd-tool.is-complete .yd-tool__icon {
  color: rgb(var(--success-6, 0 180 42));
}

.yd-tool.is-error .yd-tool__icon {
  color: rgb(var(--danger-6, 245 63 63));
}

.yd-tool__name {
  flex-shrink: 0;
  font-weight: 500;
}

.yd-tool__message {
  overflow: hidden;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 生成中三点动画 */
.yd-bubble--thinking {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 13px;
}

.yd-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgb(var(--primary-6));
  animation: yd-chat-blink 1.2s infinite;
}

.yd-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.yd-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes yd-chat-blink {
  0%, 60%, 100% { opacity: 0.25; }
  30% { opacity: 1; }
}

@keyframes yd-chat-spin {
  to { transform: rotate(360deg); }
}

/* 流式输出光标 */
.yd-caret {
  display: inline-block;
  width: 8px;
  height: 16px;
  margin-left: 2px;
  border-radius: 2px;
  background: rgb(var(--primary-6));
  vertical-align: text-bottom;
  animation: yd-chat-blink 1s infinite;
}

/* 建议动作 */
.yd-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.yd-action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-1);
  color: rgb(var(--primary-6));
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition: border-color 0.15s, background 0.15s;
}

.yd-action:hover {
  border-color: rgba(var(--primary-6), 0.4);
  background: rgba(var(--primary-6), 0.06);
}

/* 消息操作 */
.yd-message-tools {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.yd-message-tool {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-3);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.yd-message-tool:hover {
  background: var(--color-fill-1);
  color: rgb(var(--primary-6));
}
</style>
