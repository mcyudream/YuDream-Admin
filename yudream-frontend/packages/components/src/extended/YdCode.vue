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
  prefixes?: string[]
}>(), { language: 'plaintext', copyable: true })
const highlightedLines = computed(() => props.code.split('\n').map(line => hljs.highlight(line, { language: hljs.getLanguage(props.language) ? props.language : 'plaintext', ignoreIllegals: true }).value))
</script>

<template>
  <section class="yd-code yd-surface">
    <header class="yd-row">
      <span>{{ title || language }}</span><YdCopyButton v-if="copyable" :text="code" />
    </header>
    <div class="yd-code-body">
      <div v-for="(line, index) in code.split('\n')" :key="`${line}-${index}`" class="yd-code-line">
        <span v-if="lineNumbers || prefixes?.[index]" class="yd-code-prefix">{{ prefixes?.[index] ?? index + 1 }}</span>
        <pre><code :class="`language-${language}`" v-html="highlightedLines[index]" /></pre>
      </div>
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

.yd-code-line { display: flex; min-width: max-content; }
.yd-code-prefix { width: 2.5rem; flex: 0 0 auto; padding: 16px 0 16px 12px; color: oklch(var(--muted-foreground)); text-align: center; }

pre {
  display: block;
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
