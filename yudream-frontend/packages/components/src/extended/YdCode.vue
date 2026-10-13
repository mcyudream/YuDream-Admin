<script setup lang="ts">
import hljs from 'highlight.js/lib/common'
import { computed } from 'vue'
import YdCopyButton from './YdCopyButton.vue'
import './extended.css'

const props = withDefaults(defineProps<{
  code: string
  language?: string
  title?: string
  copyable?: boolean
  lineNumbers?: boolean
}>(), { language: 'plaintext', copyable: true })
const highlighted = computed(() => hljs.highlight(props.code, { language: hljs.getLanguage(props.language) ? props.language : 'plaintext', ignoreIllegals: true }).value)
</script>

<template>
  <section class="yd-code yd-surface">
    <header class="yd-row">
      <span>{{ title || language }}</span><YdCopyButton v-if="copyable" :text="code" />
    </header>
    <div class="yd-code-body">
      <pre v-if="lineNumbers" aria-hidden="true" class="yd-muted yd-line-numbers">{{ code.split('\n').map((_, i) => i + 1).join('\n') }}</pre><pre><code :class="`language-${language}`" v-html="highlighted" /></pre>
    </div>
  </section>
</template>

<style scoped>
.yd-code {
  overflow: hidden;
}

header {
  justify-content: space-between;
  padding: 10px 16px;
  font-size: 0.75rem;
  border-bottom: 1px solid oklch(var(--border));
}

.yd-code-body {
  display: flex;
  overflow: auto;
  background: oklch(var(--muted) / 35%);
}

pre {
  padding: 16px;
  margin: 0;
  font-family: ui-monospace, monospace;
  font-size: 13px;
  line-height: 1.7;
  tab-size: 2;
}

.yd-line-numbers {
  padding-right: 0;
  text-align: right;
  user-select: none;
}

code :deep(.hljs-keyword),
code :deep(.hljs-title),
code :deep(.hljs-built_in) {
  font-weight: 650;
  color: var(--yd-emphasis);
}

code :deep(.hljs-string),
code :deep(.hljs-number),
code :deep(.hljs-literal) {
  color: oklch(var(--foreground));
  text-decoration: underline;
  text-decoration-color: oklch(var(--border));
  text-underline-offset: 3px;
}

code :deep(.hljs-comment) {
  font-style: italic;
  color: oklch(var(--muted-foreground));
}
</style>
