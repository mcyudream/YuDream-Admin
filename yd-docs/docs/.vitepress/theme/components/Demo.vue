<script setup lang="ts">
import { ref, useId } from 'vue'

withDefaults(defineProps<{
  title?: string
  description?: string
  /** 仅作为文本显示，绝不编译或执行。 */
  source?: string
}>(), {
  title: '',
  description: '',
  source: '',
})

const showSource = ref(false)
const sourceId = `yd-demo-source-${useId()}`
</script>

<template>
  <section class="yd-demo">
    <header v-if="title || description" class="yd-demo__header">
      <div v-if="title" class="yd-demo__title">{{ title }}</div>
      <p v-if="description" class="yd-demo__description">{{ description }}</p>
    </header>

    <div class="yd-demo__preview">
      <slot />
    </div>

    <footer v-if="source" class="yd-demo__footer">
      <button
        class="yd-demo__toggle"
        type="button"
        :aria-expanded="showSource"
        :aria-controls="sourceId"
        @click="showSource = !showSource"
      >
        {{ showSource ? '收起源码' : '查看源码' }}
      </button>
    </footer>

    <div v-if="showSource && source" :id="sourceId" class="yd-demo__source">
      <pre><code>{{ source }}</code></pre>
    </div>
  </section>
</template>

<style scoped>
.yd-demo {
  overflow: hidden;
  margin: 16px 0;
  border: 1px solid var(--color-border-2, var(--vp-c-divider));
  border-radius: var(--border-radius-large, 12px);
  background: var(--color-bg-1, var(--vp-c-bg));
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.yd-demo__header {
  padding: 18px 22px 16px;
  border-bottom: 1px solid var(--color-border-2, var(--vp-c-divider));
  background: linear-gradient(
    180deg,
    var(--color-bg-1, var(--vp-c-bg)),
    var(--color-fill-1, var(--vp-c-bg-soft))
  );
}

.yd-demo__title {
  color: var(--color-text-1, var(--vp-c-text-1));
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.yd-demo__description {
  margin: 8px 0 0;
  color: var(--color-text-3, var(--vp-c-text-2));
  font-size: 13px;
  line-height: 1.6;
}

.yd-demo__preview {
  min-width: 0;
  padding: 20px 22px 22px;
}

.yd-demo__preview :deep(.demo-row) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.yd-demo__footer {
  display: flex;
  justify-content: flex-end;
  padding: 6px 12px;
  border-top: 1px solid var(--color-border-2, var(--vp-c-divider));
  background: var(--color-bg-1, var(--vp-c-bg));
}

.yd-demo__toggle {
  padding: 5px 8px;
  border: 0;
  border-radius: var(--border-radius-small, 4px);
  background: transparent;
  color: var(--color-text-2, var(--vp-c-text-2));
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  line-height: 1.5;
}

.yd-demo__toggle:hover {
  color: rgb(var(--primary-6));
  background: var(--color-fill-2, var(--vp-c-bg-soft));
}

.yd-demo__toggle:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: 2px;
}

.yd-demo__source {
  border-top: 1px solid var(--color-border-2, var(--vp-c-divider));
  background: var(--color-fill-2, var(--vp-code-block-bg));
}

.yd-demo__source pre {
  margin: 0;
  overflow-x: auto;
  padding: 16px 22px;
  color: var(--color-text-1, var(--vp-c-text-1));
  font-family: ui-monospace, 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 12.5px;
  line-height: 1.65;
  white-space: pre;
}

@media (max-width: 640px) {
  .yd-demo__header,
  .yd-demo__preview {
    padding-right: 16px;
    padding-left: 16px;
  }

  .yd-demo__source pre {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>
