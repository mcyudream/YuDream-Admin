<script setup lang="ts">
import { ref } from 'vue'
import FaIcon from '../basic/icon/index.vue'

export interface YdThoughtChainItem {
  key: string | number
  title: string
  description?: string
  /** 节点状态决定图标与颜色 */
  status?: 'pending' | 'running' | 'success' | 'error'
  /** 自定义图标（iconify 名称），优先级高于状态图标 */
  icon?: string
  /** 额外内容插槽 key 匹配用 */
  [key: string]: unknown
}

withDefaults(defineProps<{
  items: YdThoughtChainItem[]
  /** 默认收起为单行摘要 */
  collapsible?: boolean
  defaultExpanded?: boolean
  /** 收起时显示的标题 */
  title?: string
}>(), {
  collapsible: true,
  defaultExpanded: true,
  title: '执行过程',
})

const expanded = ref(true)

function statusIcon(item: YdThoughtChainItem): string {
  if (item.icon) {
    return item.icon
  }
  switch (item.status) {
    case 'success': return 'i-ri:checkbox-circle-line'
    case 'error': return 'i-ri:error-warning-line'
    case 'running': return 'i-ri:loader-4-line'
    default: return 'i-ri:record-circle-line'
  }
}
</script>

<template>
  <section class="yd-thought-chain" :class="{ 'is-collapsed': collapsible && !expanded }">
    <button
      v-if="collapsible"
      type="button"
      class="yd-thought-chain__head"
      @click="expanded = !expanded"
    >
      <FaIcon name="i-ri:mind-map" />
      <span>{{ title }}</span>
      <span class="yd-thought-chain__count">{{ items.length }} 步</span>
      <FaIcon :name="expanded ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" class="yd-thought-chain__chevron" />
    </button>
    <ol v-show="!collapsible || expanded" class="yd-thought-chain__list">
      <li
        v-for="(item, index) in items"
        :key="item.key"
        class="yd-thought-chain__item"
        :class="`is-${item.status ?? 'pending'}`"
      >
        <span class="yd-thought-chain__rail">
          <FaIcon
            :name="statusIcon(item)"
            class="yd-thought-chain__icon"
            :class="{ 'is-spin': item.status === 'running' }"
          />
          <i v-if="index < items.length - 1" class="yd-thought-chain__line" />
        </span>
        <div class="yd-thought-chain__content">
          <div class="yd-thought-chain__title">{{ item.title }}</div>
          <div v-if="item.description" class="yd-thought-chain__desc">{{ item.description }}</div>
          <slot name="extra" :item="item" :index="index" />
        </div>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.yd-thought-chain {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-1);
}

.yd-thought-chain__head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 7px 12px;
  border: 0;
  background: var(--color-fill-1);
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

.yd-thought-chain__head:hover {
  color: var(--color-text-1);
}

.yd-thought-chain__count {
  color: var(--color-text-3);
}

.yd-thought-chain__chevron {
  margin-left: auto;
  font-size: 14px;
}

.yd-thought-chain__list {
  margin: 0;
  padding: 12px 14px 4px;
  list-style: none;
}

.yd-thought-chain__item {
  display: flex;
  gap: 10px;
}

.yd-thought-chain__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 18px;
}

.yd-thought-chain__icon {
  font-size: 15px;
  color: var(--color-text-3);
}

.yd-thought-chain__item.is-running .yd-thought-chain__icon {
  color: rgb(var(--primary-6));
}

.yd-thought-chain__item.is-success .yd-thought-chain__icon {
  color: rgb(var(--success-6, 0 180 42));
}

.yd-thought-chain__item.is-error .yd-thought-chain__icon {
  color: rgb(var(--danger-6, 245 63 63));
}

.yd-thought-chain__icon.is-spin {
  animation: yd-thought-chain-spin 1s linear infinite;
}

.yd-thought-chain__line {
  width: 1px;
  flex: 1;
  min-height: 12px;
  margin: 3px 0;
  background: var(--color-border-2);
}

.yd-thought-chain__content {
  min-width: 0;
  padding-bottom: 14px;
}

.yd-thought-chain__title {
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
}

.yd-thought-chain__desc {
  margin-top: 3px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

@keyframes yd-thought-chain-spin {
  to { transform: rotate(360deg); }
}
</style>
