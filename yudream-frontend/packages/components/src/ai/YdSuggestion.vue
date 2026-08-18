<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FaIcon from '../basic/icon/index.vue'

export interface YdSuggestionItem {
  key: string | number
  label: string
  description?: string
  icon?: string
  /** 选中后填入输入框的文本，缺省用 label */
  value?: string
}

const props = withDefaults(defineProps<{
  /** 当前输入文本（用于按触发符过滤） */
  text: string
  items: YdSuggestionItem[]
  /** 触发字符，默认 / */
  trigger?: string
}>(), {
  trigger: '/',
})

const emits = defineEmits<{
  /** 选中建议；query 为触发符后已输入的过滤词 */
  select: [item: YdSuggestionItem, query: string]
}>()

const activeIndex = ref(0)

/** 触发符开头的最后一个词，例如 “帮我 /翻译” 中的 “/翻译” */
const triggered = computed(() => {
  const match = props.text.match(/(?:^|\s)(\/[^\s]*)$/)
  if (!match || props.trigger !== '/') {
    const at = props.text.lastIndexOf(props.trigger)
    if (at < 0) {
      return null
    }
    const tail = props.text.slice(at)
    return /\s/.test(tail) ? null : tail
  }
  return match[1]
})

const query = computed(() => triggered.value?.slice(props.trigger.length) ?? '')

const filtered = computed(() => {
  const keyword = query.value.toLowerCase()
  if (!keyword) {
    return props.items
  }
  return props.items.filter(item =>
    item.label.toLowerCase().includes(keyword) || item.description?.toLowerCase().includes(keyword))
})

const visible = computed(() => triggered.value != null && filtered.value.length > 0)

watch(visible, () => (activeIndex.value = 0))

/** 输入组件 keydown 时调用，返回 true 表示事件已被消费 */
function handleKeydown(event: KeyboardEvent): boolean {
  if (!visible.value) {
    return false
  }
  if (event.key === 'ArrowDown') {
    activeIndex.value = (activeIndex.value + 1) % filtered.value.length
    return true
  }
  if (event.key === 'ArrowUp') {
    activeIndex.value = (activeIndex.value - 1 + filtered.value.length) % filtered.value.length
    return true
  }
  if (event.key === 'Enter' || event.key === 'Tab') {
    const item = filtered.value[activeIndex.value]
    if (item) {
      emits('select', item, query.value)
    }
    return true
  }
  if (event.key === 'Escape') {
    return true
  }
  return false
}

defineExpose({ visible, handleKeydown })
</script>

<template>
  <div v-if="visible" class="yd-suggestion">
    <button
      v-for="(item, index) in filtered"
      :key="item.key"
      type="button"
      class="yd-suggestion__item"
      :class="{ 'is-active': index === activeIndex }"
      @mouseenter="activeIndex = index"
      @click="emits('select', item, query)"
    >
      <FaIcon v-if="item.icon" :name="item.icon" class="yd-suggestion__icon" />
      <span class="yd-suggestion__label">{{ item.label }}</span>
      <span v-if="item.description" class="yd-suggestion__desc">{{ item.description }}</span>
    </button>
  </div>
</template>

<style scoped>
.yd-suggestion {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-1);
  box-shadow: 0 6px 24px rgb(0 0 0 / 8%);
}

.yd-suggestion__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
}

.yd-suggestion__item.is-active {
  background: rgba(var(--primary-6), 0.07);
  color: rgb(var(--primary-6));
}

.yd-suggestion__icon {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
}

.yd-suggestion__desc {
  overflow: hidden;
  margin-left: auto;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
