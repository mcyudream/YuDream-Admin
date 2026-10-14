<script setup lang="ts">
import type { YdCodeEditorInstance } from '@yudream/components'
import { isJsonObject } from '../config/agent-json'

const props = defineProps<{
  modelValue: string
  defaultValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editor = ref<YdCodeEditorInstance | null>(null)
const valid = computed(() => isJsonObject(props.modelValue))

function format() {
  editor.value?.format()
}

function restoreDefault() {
  emit('update:modelValue', props.defaultValue)
}
</script>

<template>
  <div class="agent-json-editor">
    <YdCodeEditor
      ref="editor"
      :model-value="modelValue"
      :default-value="defaultValue"
      language="json"
      height="160px"
      aria-label="Agent JSON 配置"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <div class="editor-footer">
      <small :class="valid ? 'valid' : 'invalid'">{{ valid ? 'JSON object 有效' : '必须是有效的 JSON object' }}</small>
      <div class="flex gap-2">
        <YdButton size="sm" variant="ghost" @click="restoreDefault">恢复默认</YdButton>
        <YdButton size="sm" variant="outline" @click="format">格式化 JSON</YdButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-json-editor { display: grid; gap: 6px; }.editor-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.editor-footer small { font-size: 10px; }.valid { color: rgb(var(--success-6)); }.invalid { color: rgb(var(--danger-6)); }
</style>
