<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const previewHtml = computed(() => markdownPreview(props.modelValue || ''))

function update(value: string) {
  emit('update:modelValue', value)
}

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
  <div class="mc-markdown">
    <textarea :value="modelValue" rows="14" @input="update(($event.target as HTMLTextAreaElement).value)" />
    <article class="mc-markdown__preview" v-html="previewHtml" />
  </div>
</template>
