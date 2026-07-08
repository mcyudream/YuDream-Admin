<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  content?: string
}>()

const html = computed(() => markdownPreview(props.content || ''))

function markdownPreview(markdown: string) {
  const lines = escapeHtml(markdown).split(/\r?\n/)
  const html: string[] = []
  let inList = false
  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      continue
    }
    if (line.startsWith('### ')) {
      closeList()
      html.push(`<h3>${inline(line.slice(4))}</h3>`)
    }
    else if (line.startsWith('## ')) {
      closeList()
      html.push(`<h2>${inline(line.slice(3))}</h2>`)
    }
    else if (line.startsWith('# ')) {
      closeList()
      html.push(`<h1>${inline(line.slice(2))}</h1>`)
    }
    else if (line.startsWith('- ')) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inline(line.slice(2))}</li>`)
    }
    else {
      closeList()
      html.push(`<p>${inline(line)}</p>`)
    }
  }
  closeList()
  return html.join('')

  function closeList() {
    if (inList) {
      html.push('</ul>')
      inList = false
    }
  }
}

function inline(value: string) {
  return value
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => {
    const map: Record<string, string> = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      '\'': '&#39;',
    }
    return map[char] || char
  })
}
</script>

<template>
  <article class="mc-markdown-preview" v-html="html" />
</template>
