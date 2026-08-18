<script setup lang="ts">
import { useFaToast } from '@yudream/components'
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  title: string
  description?: string
  /** 展示用代码片段；传入后显示「代码」折叠面板 */
  code?: string
  /** 是否默认展开代码 */
  codeOpen?: boolean
}>(), {
  code: '',
  codeOpen: false,
})

const toast = useFaToast()
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
        <span class="demo-card__badge">组件</span>
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
        <button type="button" class="demo-card__code-toggle" @click="codeVisible = !codeVisible">
          <FaIcon :name="codeVisible ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" />
          {{ codeVisible ? '收起代码' : '展开代码' }}
        </button>
        <FaButton v-if="codeVisible" size="icon-sm" variant="ghost" title="复制代码" @click="copyCode">
          <FaIcon name="i-ri:file-copy-line" />
        </FaButton>
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
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-large);
  background: var(--color-bg-1);
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}

.demo-card__head {
  padding: 18px 22px 16px;
  border-bottom: 1px solid var(--color-border-2);
  background: linear-gradient(180deg, var(--color-bg-1), var(--color-fill-1));
}

.demo-card__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.demo-card__title {
  padding: 2px 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: rgb(var(--primary-6));
  font-family: ui-monospace, 'SFMono-Regular', 'JetBrains Mono', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  font-weight: 600;
}

.demo-card__badge {
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
  font-size: 11px;
}

.demo-card__desc {
  margin: 10px 0 0;
  color: var(--color-text-3);
  font-size: 12.5px;
  line-height: 1.6;
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
  align-items: center;
  gap: 5px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.demo-card__code-toggle:hover {
  color: rgb(var(--primary-6));
}

.demo-card__code-body {
  padding: 0 12px 12px;
}

.demo-card__pre {
  margin: 0;
  overflow-x: auto;
  padding: 16px;
  border-radius: 8px;
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-family: ui-monospace, 'SFMono-Regular', 'JetBrains Mono', Consolas, 'Liberation Mono', monospace;
  font-size: 12.5px;
  line-height: 1.65;
  white-space: pre;
}
</style>
