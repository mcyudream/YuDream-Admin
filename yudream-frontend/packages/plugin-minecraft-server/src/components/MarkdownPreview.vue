<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'

defineProps<{
  content?: string
}>()
</script>

<template>
  <MdPreview
    class="mc-markdown-preview"
    language="zh-CN"
    preview-theme="github"
    code-theme="github"
    :model-value="content || ''"
  />
</template>
