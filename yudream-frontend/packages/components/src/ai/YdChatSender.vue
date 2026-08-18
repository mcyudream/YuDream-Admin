<script setup lang="ts">
import { computed, ref } from 'vue'
import type { YdChatAttachment } from './useYdChatStream'
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import YdAttachmentList from './YdAttachmentList.vue'
import YdSuggestion from './YdSuggestion.vue'
import type { YdSuggestionItem } from './YdSuggestion.vue'
import { useYdChatConfig } from './chat-context'

const props = withDefaults(defineProps<{
  /** 生成中：显示停止按钮、禁止再次发送 */
  loading?: boolean
  disabled?: boolean
  placeholder?: string
  /** 建议问题 chips（空态或随时展示） */
  suggestions?: string[]
  /** 当前附件列表（支持 v-model:attachments） */
  attachments?: YdChatAttachment[]
  /** 可选文件类型，默认图片与常见文档 */
  accept?: string
  /** 是否允许多选 */
  multiple?: boolean
  /** 输入框底部免责声明，缺省取全局配置 */
  disclaimer?: string
  /** 大输入框模式（Kimi 风格，默认更高） */
  large?: boolean
  /** @ 触发的上下文候选项，由使用页面提供并自行解析业务语义 */
  mentionItems?: YdSuggestionItem[]
  /** 页面级助手使用受限内容宽度，嵌入式会话保持父容器宽度 */
  constrained?: boolean
}>(), {
  placeholder: undefined,
  suggestions: () => [],
  attachments: () => [],
  accept: 'image/*,.pdf,.txt,.md,.doc,.docx,.xls,.xlsx,.ppt,.pptx',
  multiple: true,
  disclaimer: undefined,
  large: true,
  mentionItems: () => [],
  constrained: false,
})

const emits = defineEmits<{
  send: [text: string, attachments: YdChatAttachment[]]
  stop: []
  suggestionClick: [text: string]
  attach: [files: File[]]
  removeAttachment: [attachment: YdChatAttachment]
  'update:attachments': [attachments: YdChatAttachment[]]
  mentionSelect: [item: YdSuggestionItem]
}>()

const text = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const focused = ref(false)
const mentionSuggestion = ref<InstanceType<typeof YdSuggestion> | null>(null)
const config = useYdChatConfig()
const placeholderText = computed(() => props.placeholder ?? config.placeholder ?? '尽管问，或让我帮你做点什么…')
const disclaimerText = computed(() => props.disclaimer ?? config.disclaimer ?? '内容由余梦 AI 生成，请核对重要信息')
const canSend = computed(() => !props.disabled && !props.loading && !!text.value.trim())

function submit() {
  if (!canSend.value) {
    return
  }
  emits('send', text.value.trim(), props.attachments)
  text.value = ''
}

function pickFiles() {
  if (props.loading || props.disabled) {
    return
  }
  fileInput.value?.click()
}

function onFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length) {
    emits('attach', files)
  }
  input.value = ''
}

function onDrop(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length) {
    emits('attach', files)
  }
}

function onPaste(event: ClipboardEvent) {
  const files = Array.from(event.clipboardData?.files ?? [])
  if (files.length) {
    emits('attach', files)
  }
}

function onKeydown(event: KeyboardEvent) {
  if (mentionSuggestion.value?.handleKeydown(event)) {
    event.preventDefault()
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}

function onMentionSelect(item: YdSuggestionItem) {
  const triggerAt = text.value.lastIndexOf('@')
  const before = triggerAt >= 0 ? text.value.slice(0, triggerAt) : `${text.value} `
  text.value = `${before}@${item.value ?? item.label} `
  emits('mentionSelect', item)
}
</script>

<template>
  <footer class="yd-sender" :class="{ 'yd-sender--constrained': constrained }">
    <div v-if="suggestions.length" class="yd-sender__suggestions">
      <button
        v-for="item in suggestions"
        :key="item"
        type="button"
        class="yd-sender__chip"
        :disabled="loading || disabled"
        @click="emits('suggestionClick', item)"
      >
        <FaIcon name="i-ri:sparkling-2-line" />
        {{ item }}
      </button>
    </div>

    <div
      class="yd-sender__composer"
      :class="{ 'is-focused': focused, 'yd-sender__composer--large': large }"
      @drop.prevent="onDrop"
      @dragover.prevent
      @paste="onPaste"
    >
      <YdSuggestion
        v-if="mentionItems.length"
        ref="mentionSuggestion"
        class="yd-sender__mention"
        :text="text"
        :items="mentionItems"
        trigger="@"
        @select="onMentionSelect"
      />
      <YdAttachmentList v-if="attachments.length" :attachments="attachments" removable @remove="emits('removeAttachment', $event)" />
      <textarea
        v-model="text"
        :rows="large ? 3 : 2"
        :placeholder="placeholderText"
        :disabled="disabled"
        class="yd-sender__box"
        @focus="focused = true"
        @blur="focused = false"
        @keydown="onKeydown"
      />
      <div class="yd-sender__bar">
        <input ref="fileInput" type="file" class="yd-sender__file" :accept="accept" :multiple="multiple" @change="onFiles">
        <FaButton
          variant="ghost"
          size="icon-sm"
          class="yd-sender__attach"
          title="添加附件"
          :disabled="loading || disabled"
          @click="pickFiles"
        >
          <FaIcon name="i-ri:add-line" class="yd-sender__attach-icon" />
        </FaButton>
        <!-- 上下文/模型选择等，集成进输入框工具条 -->
        <slot name="actions" />
        <span class="yd-sender__hint">{{ disclaimerText }}</span>
        <FaButton
          v-if="loading"
          size="icon-sm"
          variant="outline"
          class="yd-sender__action"
          title="停止生成"
          @click="emits('stop')"
        >
          <FaIcon name="i-ri:stop-fill" />
        </FaButton>
        <FaButton
          v-else
          size="icon-sm"
          class="yd-sender__action yd-sender__action--send"
          title="发送"
          :disabled="!canSend"
          @click="submit"
        >
          <FaIcon name="i-ri:arrow-up-line" />
        </FaButton>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.yd-sender {
  display: grid;
  flex-shrink: 0;
  gap: 12px;
  padding: 8px clamp(16px, 8vw, 120px) 20px;
}

.yd-sender__suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.yd-sender--constrained .yd-sender__suggestions,
.yd-sender--constrained .yd-sender__composer {
  width: min(100%, 920px);
  margin-inline: auto;
}

.yd-sender__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12.5px;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.yd-sender__chip:hover:not(:disabled) {
  border-color: var(--color-border-2);
  background: var(--color-fill-1);
  color: var(--color-text-1);
}

.yd-sender__chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* Kimi 风格大圆角输入框 */
.yd-sender__composer {
  position: relative;
  overflow: visible;
  border: 1px solid var(--color-border-2);
  border-radius: 20px;
  background: var(--color-bg-1);
  box-shadow: 0 4px 24px rgb(0 0 0 / 6%);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.yd-sender__composer.is-focused {
  border-color: var(--color-border-2);
  box-shadow: 0 4px 28px rgb(0 0 0 / 8%);
}

.yd-sender__mention {
  position: absolute;
  z-index: 2;
  right: 14px;
  bottom: calc(100% + 8px);
  left: 14px;
  max-height: 248px;
  overflow-y: auto;
}

.yd-sender__box {
  display: block;
  width: 100%;
  min-height: 84px;
  resize: none;
  padding: 16px 18px 4px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-text-1);
  font: inherit;
  font-size: 15px;
  line-height: 1.7;
  box-shadow: none;
}

.yd-sender__box::placeholder {
  color: var(--color-text-3);
}

.yd-sender__box:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.yd-sender__bar {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 8px;
  padding: 4px 14px 12px;
}

.yd-sender__attach {
  flex-shrink: 0;
  border-radius: 999px;
}

.yd-sender__attach-icon {
  font-size: 18px;
}

.yd-sender__hint {
  overflow: hidden;
  margin-left: auto;
  margin-right: 4px;
  color: var(--color-text-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-sender__file {
  display: none;
}

.yd-sender__action {
  flex-shrink: 0;
  border-radius: 999px;
}

.yd-sender__action--send:not(:disabled) {
  background: var(--color-text-1);
  color: var(--color-bg-1);
}

.yd-sender__action--send:not(:disabled):hover {
  background: var(--color-text-2);
}

@media (max-width: 640px) {
  .yd-sender {
    gap: 8px;
    padding: 8px 12px 12px;
  }

  .yd-sender__bar {
    flex-wrap: wrap;
    gap: 6px;
    padding: 4px 10px 10px;
  }

  .yd-sender__hint {
    display: none;
  }

  .yd-sender__box {
    min-height: 76px;
    padding: 13px 14px 4px;
  }
}
</style>
