<script setup lang="ts">
import type { YdChatAttachment } from './useYdChatStream'
import type { YdSuggestionItem } from './YdSuggestion.vue'
import { computed, ref } from 'vue'
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import { useYdChatConfig } from './chat-context'
import YdAttachmentList from './YdAttachmentList.vue'
import YdSuggestion from './YdSuggestion.vue'

const props = withDefaults(defineProps<{
  /** 生成中：显示停止按钮、禁止再次发送 */
  loading?: boolean
  disabled?: boolean
  placeholder?: string
  /** 建议问题 chips（空态或随时展示） */
  suggestions?: string[]
  /** 当前附件列表（支持 v-model:attachments） */
  attachments?: YdChatAttachment[]
  /** 是否允许添加附件；由宿主按当前能力决定 */
  attachmentsEnabled?: boolean
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
  attachmentsEnabled: true,
  accept: 'image/*,.pdf,.txt,.md,.doc,.docx,.xls,.xlsx,.ppt,.pptx',
  multiple: true,
  disclaimer: undefined,
  large: true,
  mentionItems: () => [],
  constrained: false,
})

const emits = defineEmits<{
  'send': [text: string, attachments: YdChatAttachment[]]
  'stop': []
  'suggestionClick': [text: string]
  'attach': [files: File[]]
  'removeAttachment': [attachment: YdChatAttachment]
  'update:attachments': [attachments: YdChatAttachment[]]
  'mentionSelect': [item: YdSuggestionItem]
}>()

const text = defineModel<string>({ default: '' })
const fileInput = ref<HTMLInputElement | null>(null)
const focused = ref(false)
const mentionSuggestion = ref<InstanceType<typeof YdSuggestion> | null>(null)
const config = useYdChatConfig()
const placeholderText = computed(() => props.placeholder ?? config.placeholder ?? '尽管问，或让我帮你做点什么…')
const disclaimerText = computed(() => props.disclaimer ?? config.disclaimer ?? '内容由余梦 AI 生成，请核对重要信息')
const canSend = computed(() => !props.disabled && !props.loading && !!text.value.trim())
const canAttach = computed(() => props.attachmentsEnabled && !props.loading && !props.disabled)

function submit() {
  if (!canSend.value) {
    return
  }
  emits('send', text.value.trim(), props.attachments)
  text.value = ''
}

function pickFiles() {
  if (!canAttach.value) {
    return
  }
  fileInput.value?.click()
}

function onFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (canAttach.value && files.length) {
    emits('attach', files)
  }
  input.value = ''
}

function onDrop(event: DragEvent) {
  if (!canAttach.value) {
    return
  }
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length) {
    emits('attach', files)
  }
}

function onPaste(event: ClipboardEvent) {
  if (!canAttach.value) {
    return
  }
  const files = Array.from(event.clipboardData?.files ?? [])
  if (files.length) {
    emits('attach', files)
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) {
    return
  }
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
        <FaIcon name="morph:sparkles" />
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
      <div v-if="$slots.context" class="yd-sender__context">
        <slot name="context" />
      </div>
      <textarea
        v-model="text"
        :rows="large ? 3 : 2"
        :placeholder="placeholderText"
        :disabled="disabled"
        aria-label="输入消息"
        class="yd-sender__box"
        @focus="focused = true"
        @blur="focused = false"
        @keydown="onKeydown"
      />
      <div class="yd-sender__bar">
        <input v-if="attachmentsEnabled" ref="fileInput" type="file" class="yd-sender__file" :accept="accept" :multiple="multiple" @change="onFiles">
        <FaButton
          v-if="attachmentsEnabled"
          variant="ghost"
          size="icon-sm"
          class="yd-sender__attach"
          title="添加附件"
          :disabled="loading || disabled"
          @click="pickFiles"
        >
          <FaIcon name="morph:plus" class="yd-sender__attach-icon" />
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
          <FaIcon name="morph:square" />
        </FaButton>
        <FaButton
          v-else
          size="icon-sm"
          class="yd-sender__action yd-sender__action--send"
          title="发送"
          :disabled="!canSend"
          @click="submit"
        >
          <FaIcon name="morph:arrow-up" />
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
  gap: 8px;
  justify-content: center;
}

.yd-sender--constrained .yd-sender__suggestions,
.yd-sender--constrained .yd-sender__composer {
  width: min(100%, 920px);
  margin-inline: auto;
}

.yd-sender__chip {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 6px 14px;
  font: inherit;
  font-size: calc(var(--yd-control-font-size, 14px) - 1px);
  color: var(--color-text-2);
  cursor: pointer;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 12px);
  transition:
    border-color var(--yd-motion-duration-fast, 140ms) var(--yd-motion-ease, ease),
    color var(--yd-motion-duration-fast, 140ms) var(--yd-motion-ease, ease),
    background var(--yd-motion-duration-fast, 140ms) var(--yd-motion-ease, ease);
}

.yd-sender__chip:hover:not(:disabled) {
  color: var(--color-text-1);
  background: var(--color-fill-1);
  border-color: var(--color-border-2);
}

.yd-sender__chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.yd-sender__composer {
  position: relative;
  overflow: visible;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius, 18px);
  box-shadow: var(--yd-surface-shadow, none);
  transition:
    border-color var(--yd-motion-duration-fast, 140ms) var(--yd-motion-ease, ease),
    box-shadow var(--yd-motion-duration-fast, 140ms) var(--yd-motion-ease, ease);
}

.yd-sender__composer.is-focused {
  border-color: var(--color-text-3);
  box-shadow: var(--yd-surface-shadow-hover, none);
}

.yd-sender__context {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 12px 14px 0;
  color: var(--color-text-2);
}

.yd-sender__mention {
  position: absolute;
  right: 14px;
  bottom: calc(100% + 8px);
  left: 14px;
  z-index: 2;
  max-height: 248px;
  overflow-y: auto;
}

.yd-sender__box {
  display: block;
  width: 100%;
  min-height: 84px;
  padding: 16px 18px 4px;
  font: inherit;
  font-size: var(--yd-control-font-size, 14px);
  line-height: 1.7;
  color: var(--color-text-1);
  resize: none;
  outline: 0;
  background: transparent;
  border: 0;
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
  flex-wrap: nowrap;
  gap: 8px;
  align-items: center;
  padding: 4px 14px 12px;
}

.yd-sender__attach {
  flex-shrink: 0;
  border-radius: var(--yd-control-radius, 12px);
}

.yd-sender__attach-icon {
  font-size: 18px;
}

.yd-sender__hint {
  margin-right: 4px;
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.yd-sender__file {
  display: none;
}

.yd-sender__action {
  flex-shrink: 0;
  border-radius: var(--yd-control-radius, 12px);
}

.yd-sender__action--send:not(:disabled) {
  color: var(--color-bg-1);
  background: var(--color-text-1);
}

.yd-sender__action--send:not(:disabled):hover {
  background: var(--color-text-2);
}

@media (width <= 768px) {
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
