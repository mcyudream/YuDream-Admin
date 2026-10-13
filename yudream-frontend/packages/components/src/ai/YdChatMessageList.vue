<script setup lang="ts">
import type { YdChatAction, YdChatCitation, YdChatGraphNode, YdChatMessage, YdChatRetrievalHit, YdChatToolEvent } from './useYdChatStream'
import { MdPreview } from 'md-editor-v3'
import { computed, nextTick, ref, watch } from 'vue'
import FaIcon from '../basic/icon/index.vue'
import { useYdChatConfig } from './chat-context'
import YdAttachmentList from './YdAttachmentList.vue'
import YdChatProcess from './YdChatProcess.vue'
import YdCitationList from './YdCitationList.vue'
import 'md-editor-v3/lib/style.css'

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
const config = useYdChatConfig()
const thinkingHint = computed(() => props.thinkingText ?? config.thinkingText ?? '正在检索并思考…')
const reasoningLabel = computed(() => props.reasoningTitle ?? config.reasoningTitle ?? '深度思考')

function rendered(content: string): string {
  const transform = props.transformContent ?? config.transformContent
  return transform ? transform(content) : content
}

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
          <div class="yd-bubble yd-bubble--user">
            {{ message.content }}
          </div>
        </div>
      </div>

      <!-- 助手消息 -->
      <div v-else class="yd-msg">
        <div class="yd-msg__avatar">
          <FaIcon name="morph:sparkles" />
        </div>
        <div class="yd-msg__body">
          <!-- 深度思考 -->
          <section v-if="message.reasoning" class="yd-reasoning">
            <button type="button" class="yd-reasoning__head" @click="toggleReasoning(index)">
              <FaIcon name="morph:brain" />
              <span>{{ reasoningLabel }}</span>
              <FaIcon :name="expandedReasoning[index] ? 'morph:chevron-up' : 'morph:chevron-down'" class="yd-reasoning__chevron" />
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
                :name="tool.status === 'complete' ? 'morph:circle-check' : tool.status === 'error' ? 'morph:triangle-alert' : 'morph:loader-circle'"
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
              <FaIcon name="morph:copy" />
              <span>复制</span>
            </button>
            <button type="button" class="yd-message-tool" @click="emits('regenerateMessage', message)">
              <FaIcon name="morph:refresh-cw" />
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
              <FaIcon v-if="action.action === 'copy'" name="morph:copy" />
              <FaIcon v-else-if="action.action === 'open'" name="morph:external-link" />
              <FaIcon v-else name="morph:arrow-right" />
              {{ action.label }}
            </button>
          </div>

          <!-- 业务侧可将澄清选项等交互内容放在对应 assistant 消息下，而不是固定在输入区。 -->
          <slot name="message-extra" :message="message" :index="index" />
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
      <FaIcon name="morph:arrow-down" />
    </button>
  </div>
</template>

<style scoped>
.yd-chat-list {
  flex: 1;
  min-height: 0;
  padding: 8px clamp(16px, 8vw, 120px) 20px;
  overflow-y: auto;
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
  bottom: 12px;
  z-index: 5;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  margin-top: -34px;
  margin-left: auto;
  font-size: 16px;
  color: var(--color-text-2);
  cursor: pointer;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 50%;
  box-shadow: 0 4px 16px rgb(0 0 0 / 10%);
  transition: color 0.15s, border-color 0.15s, transform 0.15s;
}

.yd-chat-list__to-bottom:hover {
  color: var(--color-text-1);
  border-color: var(--color-border-3);
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
  flex-shrink: 0;
  place-items: center;
  width: 30px;
  height: 30px;
  font-size: 15px;
  color: var(--yd-emphasis, oklch(var(--primary)));
  background: color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 12%, transparent);
  border-radius: 9px;
}

.yd-msg__body {
  min-width: 0;
  max-width: 100%;
}

.yd-msg--user .yd-msg__body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 82%;
}

.yd-bubble {
  padding: 10px 14px;
  font-size: 14.5px;
  line-height: 1.75;
  border-radius: 14px;
}

.yd-bubble--user {
  max-width: 100%;
  padding: 11px 16px;
  color: var(--color-text-1);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  background: color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 10%, transparent);
  border-radius: 18px;
  border-bottom-right-radius: 6px;
}

.yd-bubble--assistant {
  padding: 4px 0;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.yd-bubble--error {
  color: rgb(var(--danger-6, 245 63 63));
  background: rgb(var(--danger-6, 245 63 63) / 6%);
  border-color: rgb(var(--danger-6, 245 63 63) / 33%);
}

.yd-markdown :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.yd-markdown :deep(.md-editor-preview) {
  font-size: 14px;
  color: inherit;
  background: transparent;
}

.yd-markdown :deep(a[href^='wiki://']) {
  color: var(--yd-emphasis, oklch(var(--primary)));
  text-decoration: none;
  cursor: pointer;
  border-bottom: 1px dashed color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 50%, transparent);
}

/* 深度思考 */
.yd-reasoning {
  margin-bottom: 10px;
  overflow: hidden;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.yd-reasoning__head {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  padding: 7px 12px;
  font: inherit;
  font-size: 12px;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.yd-reasoning__head:hover {
  color: var(--color-text-1);
}

.yd-reasoning__head :deep(svg:first-child) {
  color: var(--yd-emphasis, oklch(var(--primary)));
}

.yd-reasoning__chevron {
  margin-left: auto;
  font-size: 14px;
}

.yd-reasoning__body {
  padding: 2px 12px 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--color-text-3);
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
  gap: 7px;
  align-items: center;
  width: fit-content;
  max-width: 100%;
  padding: 6px 11px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.yd-tool__icon {
  flex-shrink: 0;
  color: var(--yd-emphasis, oklch(var(--primary)));
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
  text-overflow: ellipsis;
  color: var(--color-text-3);
  white-space: nowrap;
}

/* 生成中三点动画 */
.yd-bubble--thinking {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 13px;
  color: var(--color-text-3);
}

.yd-dot {
  width: 6px;
  height: 6px;
  background: var(--yd-emphasis, oklch(var(--primary)));
  border-radius: 50%;
  animation: yd-chat-blink 1.2s infinite;
}

.yd-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.yd-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes yd-chat-blink {
  0%,
 60%,
 100% { opacity: 0.25; }
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
  vertical-align: text-bottom;
  background: var(--yd-emphasis, oklch(var(--primary)));
  border-radius: 2px;
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
  gap: 5px;
  align-items: center;
  padding: 5px 12px;
  font: inherit;
  font-size: 12px;
  color: var(--yd-emphasis, oklch(var(--primary)));
  cursor: pointer;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  transition: border-color 0.15s, background 0.15s;
}

.yd-action:hover {
  background: color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 6%, transparent);
  border-color: color-mix(in oklab, var(--yd-emphasis, oklch(var(--primary))) 40%, transparent);
}

/* 消息操作 */
.yd-message-tools {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.yd-message-tool {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 6px;
  font: inherit;
  font-size: 12px;
  color: var(--color-text-3);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.yd-message-tool:hover {
  color: var(--yd-emphasis, oklch(var(--primary)));
  background: var(--color-fill-1);
}
</style>
