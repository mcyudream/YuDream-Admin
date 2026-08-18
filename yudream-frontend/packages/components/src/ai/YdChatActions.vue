<script setup lang="ts">
import { ref } from 'vue'
import FaIcon from '../basic/icon/index.vue'

export interface YdChatActionItem {
  key: string
  label?: string
  icon?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  /** 复制的文本内容 */
  copyText?: string
  /** 显示重新生成 */
  regenerable?: boolean
  /** 显示点赞/点踩反馈 */
  feedback?: boolean
  /** 初始反馈状态 */
  feedbackValue?: 'like' | 'dislike' | null
  /** 自定义操作项 */
  items?: YdChatActionItem[]
  /** 紧凑图标模式（不显示文字） */
  iconOnly?: boolean
}>(), {
  copyText: '',
  regenerable: false,
  feedback: false,
  feedbackValue: null,
  items: () => [],
  iconOnly: false,
})

const emits = defineEmits<{
  copy: [text: string]
  regenerate: []
  feedback: [value: 'like' | 'dislike' | null]
  action: [item: YdChatActionItem]
}>()

const copied = ref(false)
const feedbackState = ref<'like' | 'dislike' | null>(props.feedbackValue)

async function onCopy() {
  if (!props.copyText) {
    return
  }
  try {
    await navigator.clipboard?.writeText(props.copyText)
  }
  catch {
    // 剪贴板不可用时仍抛出 copy 事件，由业务兜底
  }
  copied.value = true
  setTimeout(() => (copied.value = false), 1600)
  emits('copy', props.copyText)
}

function onFeedback(value: 'like' | 'dislike') {
  feedbackState.value = feedbackState.value === value ? null : value
  emits('feedback', feedbackState.value)
}
</script>

<template>
  <div class="yd-chat-actions">
    <button
      v-if="copyText"
      type="button"
      class="yd-chat-actions__btn"
      :class="{ 'is-active': copied }"
      :title="copied ? '已复制' : '复制'"
      @click="onCopy"
    >
      <FaIcon :name="copied ? 'i-ri:check-line' : 'i-ri:file-copy-line'" />
      <span v-if="!iconOnly">{{ copied ? '已复制' : '复制' }}</span>
    </button>
    <button
      v-if="regenerable"
      type="button"
      class="yd-chat-actions__btn"
      title="重新生成"
      @click="emits('regenerate')"
    >
      <FaIcon name="i-ri:refresh-line" />
      <span v-if="!iconOnly">重新生成</span>
    </button>
    <template v-if="feedback">
      <button
        type="button"
        class="yd-chat-actions__btn"
        :class="{ 'is-active': feedbackState === 'like' }"
        title="有帮助"
        @click="onFeedback('like')"
      >
        <FaIcon :name="feedbackState === 'like' ? 'i-ri:thumb-up-fill' : 'i-ri:thumb-up-line'" />
      </button>
      <button
        type="button"
        class="yd-chat-actions__btn"
        :class="{ 'is-active': feedbackState === 'dislike' }"
        title="没帮助"
        @click="onFeedback('dislike')"
      >
        <FaIcon :name="feedbackState === 'dislike' ? 'i-ri:thumb-down-fill' : 'i-ri:thumb-down-line'" />
      </button>
    </template>
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      class="yd-chat-actions__btn"
      :disabled="item.disabled"
      :title="item.label"
      @click="emits('action', item)"
    >
      <FaIcon v-if="item.icon" :name="item.icon" />
      <span v-if="!iconOnly && item.label">{{ item.label }}</span>
    </button>
    <slot />
  </div>
</template>

<style scoped>
.yd-chat-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.yd-chat-actions__btn {
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

.yd-chat-actions__btn:hover:not(:disabled) {
  background: var(--color-fill-1);
  color: rgb(var(--primary-6));
}

.yd-chat-actions__btn.is-active {
  color: rgb(var(--primary-6));
}

.yd-chat-actions__btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
