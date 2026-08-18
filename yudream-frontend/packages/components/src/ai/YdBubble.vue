<script setup lang="ts">
import type { YdChatCitation, YdChatMessage } from './useYdChatStream'
import { computed } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import FaIcon from '../basic/icon/index.vue'
import YdAttachmentList from './YdAttachmentList.vue'
import YdChatActions from './YdChatActions.vue'
import YdChatLoading from './YdChatLoading.vue'
import YdChatReasoning from './YdChatReasoning.vue'
import YdCitationList from './YdCitationList.vue'
import { useYdChatConfig } from './chat-context'

const props = withDefaults(defineProps<{
  message: YdChatMessage
  /** 渲染前的内容转换（如 wikilink → markdown 链接），缺省取全局配置 */
  transformContent?: (content: string) => string
  /** 等待首字节提示 */
  thinkingText?: string
  /** 助手头像图标（iconify 名称），传空字符串隐藏 */
  avatar?: string
  /** 显示消息操作栏（复制/重新生成/反馈） */
  showActions?: boolean
  /** 操作栏启用点赞点踩 */
  feedback?: boolean
  /** 流式输出光标 */
  caret?: boolean
  /** 引用图片地址解析（如开发环境补代理前缀），透传给引用列表 */
  imageUrlResolver?: (url: string) => string
}>(), {
  transformContent: undefined,
  thinkingText: undefined,
  avatar: undefined,
  showActions: false,
  feedback: false,
  caret: true,
  imageUrlResolver: undefined,
})

const emits = defineEmits<{
  contentClick: [event: MouseEvent]
  citationClick: [citation: YdChatCitation]
  copy: [message: YdChatMessage]
  regenerate: [message: YdChatMessage]
  feedback: [message: YdChatMessage, value: 'like' | 'dislike' | null]
}>()

const config = useYdChatConfig()

const avatarIcon = computed(() => props.avatar ?? config.assistantAvatar ?? 'i-ri:sparkling-2-line')
const hint = computed(() => props.thinkingText ?? config.thinkingText ?? '正在思考…')

const thinking = computed(() =>
  props.message.pending
  && !props.message.content
  && !props.message.reasoning
  && !props.message.tools?.length
  && !props.message.activities?.length)

function rendered(content: string): string {
  const transform = props.transformContent ?? config.transformContent
  return transform ? transform(content) : content
}
</script>

<template>
  <!-- 用户消息：右侧气泡 -->
  <div v-if="message.role === 'user'" class="yd-bubble-row yd-bubble-row--user">
    <div class="yd-bubble-row__body yd-bubble-row__body--user">
      <YdAttachmentList :attachments="message.attachments ?? []" />
      <div class="yd-bubble yd-bubble--user">{{ message.content }}</div>
    </div>
  </div>

  <!-- 助手消息 -->
  <div v-else class="yd-bubble-row">
    <div v-if="avatarIcon" class="yd-bubble-row__avatar">
      <slot name="avatar">
        <FaIcon :name="avatarIcon" />
      </slot>
    </div>
    <div class="yd-bubble-row__body">
      <slot name="header" :message="message" />

      <YdChatReasoning
        v-if="message.reasoning"
        :content="message.reasoning"
        :pending="message.pending"
      />

      <slot name="process" :message="message" />

      <div v-if="thinking" class="yd-bubble yd-bubble--assistant">
        <YdChatLoading :text="hint" />
      </div>

      <div
        v-else-if="message.content || message.pending"
        class="yd-bubble yd-bubble--assistant"
        :class="{ 'yd-bubble--error': message.error }"
        @click="emits('contentClick', $event)"
      >
        <slot name="content" :message="message" :rendered="rendered(message.content)">
          <MdPreview
            :model-value="rendered(message.content)"
            language="zh-CN"
            preview-theme="github"
            code-theme="github"
            class="yd-bubble__markdown"
          />
        </slot>
        <span v-if="caret && message.pending" class="yd-bubble__caret" />
      </div>

      <YdCitationList
        v-if="!message.error && message.citations?.length"
        :citations="message.citations"
        :image-url-resolver="imageUrlResolver"
        @select="emits('citationClick', $event)"
      />

      <YdChatActions
        v-if="showActions && !message.error && message.content && !message.pending"
        :copy-text="message.content"
        regenerable
        :feedback="feedback"
        @copy="emits('copy', message)"
        @regenerate="emits('regenerate', message)"
        @feedback="emits('feedback', message, $event)"
      />

      <slot name="footer" :message="message" />
    </div>
  </div>
</template>

<style scoped>
.yd-bubble-row {
  display: flex;
  gap: 10px;
}

.yd-bubble-row--user {
  justify-content: flex-end;
}

.yd-bubble-row__avatar {
  display: grid;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 10px;
  background: rgba(var(--primary-6), 0.12);
  color: rgb(var(--primary-6));
  font-size: 16px;
}

.yd-bubble-row__body {
  max-width: 82%;
  min-width: 0;
}

.yd-bubble {
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
}

.yd-bubble--user {
  max-width: 100%;
  border-bottom-right-radius: 4px;
  background: rgb(var(--primary-6));
  color: oklch(var(--primary-foreground));
  white-space: pre-wrap;
  word-break: break-word;
}

.yd-bubble--assistant {
  border: 1px solid var(--color-border-2);
  border-top-left-radius: 4px;
  background: var(--color-bg-1);
}

.yd-bubble--error {
  border-color: rgb(var(--danger-6, 245 63 63) / 33%);
  background: rgb(var(--danger-6, 245 63 63) / 6%);
  color: rgb(var(--danger-6, 245 63 63));
}

.yd-bubble__markdown :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.yd-bubble__markdown :deep(.md-editor-preview) {
  background: transparent;
  color: inherit;
  font-size: 14px;
}

.yd-bubble__caret {
  display: inline-block;
  width: 8px;
  height: 16px;
  margin-left: 2px;
  border-radius: 2px;
  background: rgb(var(--primary-6));
  vertical-align: text-bottom;
  animation: yd-bubble-blink 1s infinite;
}

@keyframes yd-bubble-blink {
  0%, 60%, 100% { opacity: 0.25; }
  30% { opacity: 1; }
}
</style>
