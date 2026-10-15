<script setup lang="ts">
import { useYdToast, YdAction } from '@yudream/components'
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  /** 展示用代码片段；传入后显示「代码」折叠面板 */
  code?: string
  /** 是否默认展开代码 */
  codeOpen?: boolean
  kind?: string
}>(), {
  code: '',
  codeOpen: false,
  kind: '组件',
})

const toast = useYdToast()
const codeVisible = ref(props.codeOpen)

async function copyCode() {
  try {
    await navigator.clipboard.writeText(props.code)
    toast.success('代码已复制')
  }
  catch {
    toast.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <section class="demo-card">
    <header class="demo-card__head">
      <div class="demo-card__title-row">
        <code class="demo-card__title">{{ title }}</code>
        <span class="demo-card__badge">{{ kind }}</span>
      </div>
      <p v-if="description" class="demo-card__desc">
        {{ description }}
      </p>
    </header>

    <div class="demo-card__body">
      <slot />
    </div>

    <footer v-if="code" class="demo-card__code">
      <div class="demo-card__code-bar">
        <YdAction type="button" class="demo-card__code-toggle" :aria-expanded="codeVisible" @click="codeVisible = !codeVisible">
          <YdIcon :name="codeVisible ? 'morph:chevron-up' : 'morph:chevron-down'" />
          {{ codeVisible ? '收起代码' : '展开代码' }}
        </YdAction>
        <YdButton v-if="codeVisible" size="icon-sm" variant="ghost" title="复制代码" @click="copyCode">
          <YdIcon name="morph:copy" />
        </YdButton>
      </div>
      <div v-if="codeVisible" class="demo-card__code-body">
        <pre class="demo-card__pre"><code>{{ code }}</code></pre>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.demo-card {
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-large);
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.demo-card__head {
  padding: 18px 22px 16px;
  background: linear-gradient(180deg, var(--color-bg-1), var(--color-fill-1));
  border-bottom: 1px solid var(--color-border-2);
}

.demo-card__title-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.demo-card__title {
  padding: 2px 8px;
  font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.demo-card__badge {
  padding: 1px 7px;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.demo-card__desc {
  margin: 10px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.demo-card__body {
  display: grid;
  gap: 16px;
  padding: 20px 22px 22px;
}

.demo-card__code {
  border-top: 1px solid var(--color-border-2);
}

.demo-card__code-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px 6px 16px;
}

.demo-card__code-toggle {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 4px 0;
  font: inherit;
  font-size: 12px;
  color: var(--color-text-2);
  cursor: pointer;
  background: transparent;
  border: 0;
}

.demo-card__code-toggle:hover {
  color: var(--color-text-1);
}

.demo-card__code-body {
  padding: 0 12px 12px;
}

.demo-card__pre {
  padding: 16px;
  margin: 0;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Consolas, "Liberation Mono", monospace;
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--color-text-1);
  white-space: pre;
  background: var(--color-fill-2);
  border-radius: 8px;
}
</style>
