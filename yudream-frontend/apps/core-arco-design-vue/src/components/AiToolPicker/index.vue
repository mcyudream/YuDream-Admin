<script setup lang="ts">
import { YdMultiSelect } from '@yudream/components'
import { computed } from 'vue'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'

/**
 * 输入框旁的「工具」选择：为当前账号的通用对话启用系统工具模块（网页、知识库…）或 MCP 服务。
 * 选项已按权限与开关过滤；只在有可选项时出现，选择保存在本浏览器。
 */
withDefaults(defineProps<{ disabled?: boolean }>(), { disabled: false })
const assistant = useAiAssistantStore()
const options = computed(() => assistant.toolOptions.modules.map(module => ({
  label: `${module.source === 'mcp' ? 'MCP · ' : ''}${module.name}`,
  value: module.code,
})))
const selected = computed({
  get: () => assistant.toolSelection.modules.filter(code => options.value.some(option => option.value === code)),
  set: codes => assistant.setToolModules(codes),
})
</script>

<template>
  <YdMultiSelect
    v-if="options.length"
    v-model="selected"
    :options="options"
    :disabled="disabled"
    :max-tag-count="1"
    placeholder="不启用工具"
    empty-text="没有可用的工具"
    aria-label="为本次对话启用的工具"
    class="ai-tool-picker"
  />
</template>

<style scoped>
.ai-tool-picker {
  min-width: 140px;
  max-width: 220px;
}
</style>
