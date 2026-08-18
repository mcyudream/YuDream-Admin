<script setup lang="ts">
import { ref } from 'vue'
import FaIcon from '../basic/icon/index.vue'
import { useYdChatConfig } from './chat-context'

const props = withDefaults(defineProps<{
  /** 推理过程文本 */
  content: string
  /** 标题，默认取全局配置或“深度思考” */
  title?: string
  /** 默认展开 */
  defaultExpanded?: boolean
  /** 是否还在推理中（标题旁显示加载态） */
  pending?: boolean
}>(), {
  title: undefined,
  defaultExpanded: false,
  pending: false,
})

const config = useYdChatConfig()
const expanded = ref(props.defaultExpanded)
</script>

<template>
  <section class="yd-reasoning-block">
    <button type="button" class="yd-reasoning-block__head" @click="expanded = !expanded">
      <FaIcon name="i-ri:brain-line" :class="{ 'is-thinking': pending }" />
      <span>{{ title ?? config.reasoningTitle ?? '深度思考' }}</span>
      <FaIcon :name="expanded ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" class="yd-reasoning-block__chevron" />
    </button>
    <div v-show="expanded" class="yd-reasoning-block__body">
      {{ content }}
    </div>
  </section>
</template>

<style scoped>
.yd-reasoning-block {
  margin-bottom: 10px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-fill-1);
}

.yd-reasoning-block__head {
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

.yd-reasoning-block__head:hover {
  color: var(--color-text-1);
}

.yd-reasoning-block__head :deep(svg:first-child) {
  color: rgb(var(--primary-6));
}

.yd-reasoning-block__head .is-thinking {
  animation: yd-reasoning-pulse 1.2s ease-in-out infinite;
}

.yd-reasoning-block__chevron {
  margin-left: auto;
  font-size: 14px;
}

.yd-reasoning-block__body {
  padding: 2px 12px 10px;
  color: var(--color-text-3);
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}

@keyframes yd-reasoning-pulse {
  0%, 100% { opacity: 0.45; }
  50% { opacity: 1; }
}
</style>
