<script setup lang="ts">
const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
}>(), {
  modelValue: '',
  placeholder: '在这里编写 Markdown 内容...',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const textareaRef = ref<HTMLTextAreaElement>()

const previewHtml = computed(() => markdownPreview(props.modelValue || ''))

function updateValue(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}

function insertMarkdown(before: string, after = '', fallback = '') {
  const textarea = textareaRef.value
  const value = props.modelValue || ''
  if (!textarea) {
    emit('update:modelValue', `${value}${before}${fallback}${after}`)
    return
  }
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = value.slice(start, end) || fallback
  const next = `${value.slice(0, start)}${before}${selected}${after}${value.slice(end)}`
  emit('update:modelValue', next)
  nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(start + before.length, start + before.length + selected.length)
  })
}

function insertLine(prefix: string, fallback: string) {
  const textarea = textareaRef.value
  const value = props.modelValue || ''
  if (!textarea) {
    emit('update:modelValue', `${value}\n${prefix}${fallback}`)
    return
  }
  const start = textarea.selectionStart
  const lineStart = value.lastIndexOf('\n', Math.max(start - 1, 0)) + 1
  const next = `${value.slice(0, lineStart)}${prefix}${value.slice(lineStart) || fallback}`
  emit('update:modelValue', next)
  nextTick(() => {
    textarea.focus()
    textarea.setSelectionRange(start + prefix.length, start + prefix.length)
  })
}

function markdownPreview(markdown: string) {
  const lines = escapeHtml(markdown).split(/\r?\n/)
  const html: string[] = []
  let inList = false
  let inCode = false
  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      if (inCode) {
        html.push('</code></pre>')
        inCode = false
      }
      else {
        if (inList) {
          html.push('</ul>')
          inList = false
        }
        html.push('<pre><code>')
        inCode = true
      }
      continue
    }
    if (inCode) {
      html.push(`${line}\n`)
      continue
    }
    const listMatch = line.match(/^\s*[-*]\s+(.+)$/)
    if (listMatch) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inlineMarkdown(listMatch[1])}</li>`)
      continue
    }
    if (inList) {
      html.push('</ul>')
      inList = false
    }
    if (line.startsWith('### ')) {
      html.push(`<h3>${inlineMarkdown(line.slice(4))}</h3>`)
    }
    else if (line.startsWith('## ')) {
      html.push(`<h2>${inlineMarkdown(line.slice(3))}</h2>`)
    }
    else if (line.startsWith('# ')) {
      html.push(`<h1>${inlineMarkdown(line.slice(2))}</h1>`)
    }
    else if (line.trim()) {
      html.push(`<p>${inlineMarkdown(line)}</p>`)
    }
  }
  if (inCode) {
    html.push('</code></pre>')
  }
  if (inList) {
    html.push('</ul>')
  }
  return html.join('')
}

function inlineMarkdown(value: string) {
  return value
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<template>
  <section class="markdown-editor">
    <header class="markdown-editor__toolbar">
      <button type="button" title="标题" @click="insertLine('## ', '标题')">
        <FaIcon name="i-ri:h-2" />
      </button>
      <button type="button" title="加粗" @click="insertMarkdown('**', '**', '加粗文本')">
        <FaIcon name="i-ri:bold" />
      </button>
      <button type="button" title="斜体" @click="insertMarkdown('*', '*', '斜体文本')">
        <FaIcon name="i-ri:italic" />
      </button>
      <button type="button" title="列表" @click="insertLine('- ', '列表项')">
        <FaIcon name="i-ri:list-unordered" />
      </button>
      <button type="button" title="链接" @click="insertMarkdown('[', '](https://)', '链接文本')">
        <FaIcon name="i-ri:link" />
      </button>
      <button type="button" title="代码块" @click="insertMarkdown('```\n', '\n```', 'code')">
        <FaIcon name="i-ri:code-s-slash-line" />
      </button>
    </header>
    <div class="markdown-editor__body">
      <textarea
        ref="textareaRef"
        :value="modelValue"
        :placeholder="placeholder"
        spellcheck="false"
        @input="updateValue"
      />
      <article class="markdown-editor__preview" v-html="previewHtml" />
    </div>
  </section>
</template>

<style scoped>
.markdown-editor {
  display: grid;
  overflow: hidden;
  min-height: 620px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.markdown-editor__toolbar {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.markdown-editor__toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: var(--color-text-2);
}

.markdown-editor__toolbar button:hover {
  background: var(--color-fill-2);
  color: rgb(var(--primary-6));
}

.markdown-editor__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 42%);
  min-height: 0;
}

.markdown-editor textarea {
  width: 100%;
  min-height: 560px;
  padding: 16px;
  border: 0;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
  color: var(--color-text-1);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  line-height: 1.75;
  outline: none;
  resize: vertical;
}

.markdown-editor__preview {
  overflow: auto;
  min-width: 0;
  max-height: 640px;
  padding: 18px;
  color: var(--color-text-1);
  line-height: 1.75;
}

.markdown-editor__preview :deep(h1),
.markdown-editor__preview :deep(h2),
.markdown-editor__preview :deep(h3),
.markdown-editor__preview :deep(p),
.markdown-editor__preview :deep(ul),
.markdown-editor__preview :deep(pre) {
  margin: 0 0 14px;
}

.markdown-editor__preview :deep(h1) {
  font-size: 28px;
}

.markdown-editor__preview :deep(h2) {
  font-size: 22px;
}

.markdown-editor__preview :deep(h3) {
  font-size: 18px;
}

.markdown-editor__preview :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: var(--color-fill-2);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.markdown-editor__preview :deep(pre) {
  overflow: auto;
  padding: 12px;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
}

.markdown-editor__preview :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.markdown-editor__preview :deep(a) {
  color: rgb(var(--primary-6));
}

@media (max-width: 1100px) {
  .markdown-editor__body {
    grid-template-columns: 1fr;
  }

  .markdown-editor textarea {
    border-right: 0;
    border-bottom: 1px solid var(--color-border-2);
  }
}
</style>
