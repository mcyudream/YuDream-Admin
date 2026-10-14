<script setup lang="ts">
import type { YdCodeEditorCompletion, YdCodeEditorLanguage } from '@yudream/components'

withDefaults(defineProps<{
  modelValue: string
  language?: Exclude<YdCodeEditorLanguage, 'json' | 'text'>
  disabled?: boolean
  completions?: YdCodeEditorCompletion[]
}>(), {
  language: 'html',
  disabled: false,
  completions: () => [],
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <YdCodeEditor
    :model-value="modelValue"
    :language="language"
    :disabled="disabled"
    :completions="completions"
    height="100%"
    aria-label="CMS 页面源码"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<style scoped>
:deep(.yd-code-editor-shell),
:deep(.yd-code-editor) {
  width: 100%;
  height: 100%;
  min-height: 0;
}
</style>
