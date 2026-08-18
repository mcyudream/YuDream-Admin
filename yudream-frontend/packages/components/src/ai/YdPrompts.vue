<script setup lang="ts">
import FaIcon from '../basic/icon/index.vue'

export interface YdPromptItem {
  key: string | number
  label: string
  description?: string
  icon?: string
  disabled?: boolean
  /** 发送给模型的文本，缺省用 label */
  value?: string
}

withDefaults(defineProps<{
  items: YdPromptItem[]
  /** 分组标题 */
  title?: string
  /** 纵向列表（antd-x vertical） */
  vertical?: boolean
  /** 自动换行而非横向滚动 */
  wrap?: boolean
}>(), {
  title: '',
  vertical: false,
  wrap: true,
})

const emits = defineEmits<{
  select: [item: YdPromptItem]
}>()
</script>

<template>
  <section v-if="items.length" class="yd-prompts" :class="{ 'yd-prompts--vertical': vertical }">
    <h3 v-if="title" class="yd-prompts__title">{{ title }}</h3>
    <div class="yd-prompts__list" :class="{ 'is-wrap': wrap }">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="yd-prompts__item"
        :disabled="item.disabled"
        @click="emits('select', item)"
      >
        <FaIcon v-if="item.icon" :name="item.icon" class="yd-prompts__icon" />
        <span class="yd-prompts__text">
          <span class="yd-prompts__label">{{ item.label }}</span>
          <span v-if="item.description" class="yd-prompts__desc">{{ item.description }}</span>
        </span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.yd-prompts__title {
  margin: 0 0 10px;
  color: var(--color-text-2);
  font-size: 14px;
  font-weight: 600;
}

.yd-prompts__list {
  display: flex;
  gap: 8px;
}

.yd-prompts__list.is-wrap {
  flex-wrap: wrap;
}

.yd-prompts--vertical .yd-prompts__list {
  flex-direction: column;
}

.yd-prompts__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 8px 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-1);
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}

.yd-prompts__item:hover:not(:disabled) {
  border-color: rgba(var(--primary-6), 0.4);
  background: rgba(var(--primary-6), 0.05);
  color: rgb(var(--primary-6));
}

.yd-prompts__item:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.yd-prompts__icon {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
  font-size: 16px;
}

.yd-prompts__text {
  display: grid;
  min-width: 0;
}

.yd-prompts__desc {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
