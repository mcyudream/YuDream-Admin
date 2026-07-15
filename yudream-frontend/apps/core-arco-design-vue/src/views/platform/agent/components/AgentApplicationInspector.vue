<script setup lang="ts">
import type { AgentApplicationPayload, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'

const emit = defineEmits<{
  updateField: [key: keyof AgentApplicationPayload, value: unknown]
}>()

const props = defineProps<{
  form: AgentApplicationPayload
  toolCodes: string[]
  systemTools: SystemAgentTool[]
  customTools: AgentTool[]
  codeDisabled?: boolean
}>()

const authorizedTools = computed(() => props.toolCodes.map((code) => {
  const systemTool = props.systemTools.find(tool => tool.code === code)
  if (systemTool) {
    return { ...systemTool, type: '系统工具' }
  }
  const customTool = props.customTools.find(tool => tool.code === code)
  return customTool
    ? { ...customTool, type: 'Python 工具' }
    : { code, name: code, description: '该工具当前不可用或已被删除', type: '不可用工具' }
}))
</script>

<template>
  <aside class="inspector">
    <header class="inspector-header">
      <div>
        <span>应用设置</span>
        <strong>{{ form.name || '未命名 Agent 应用' }}</strong>
      </div>
      <FaIcon name="i-ri:settings-3-line" />
    </header>

    <div class="inspector-body">
      <section class="form-section">
        <h3>基础信息</h3>
        <label class="form-field required">
          <span>应用名称</span>
          <FaInput :model-value="form.name" class="w-full" maxlength="60" placeholder="例如：运营内容 Agent" @update:model-value="emit('updateField', 'name', $event)" />
          <small>显示在 Agent 应用列表和调用入口中</small>
        </label>
        <label class="form-field required">
          <span>应用编码</span>
          <FaInput :model-value="form.code" class="w-full" :disabled="codeDisabled" maxlength="60" placeholder="例如：content-agent" @update:model-value="emit('updateField', 'code', $event)" />
          <small>仅支持稳定的英文业务标识，创建后不可修改</small>
        </label>
        <label class="form-field">
          <span>应用描述</span>
          <FaTextarea :model-value="form.description" class="w-full" :autosize="{ minRows: 3, maxRows: 6 }" maxlength="200" placeholder="说明这个 Agent 解决的问题和使用范围" @update:model-value="emit('updateField', 'description', $event)" />
        </label>
      </section>

      <section class="form-section">
        <h3>Agent 指令</h3>
        <label class="form-field">
          <span>系统提示词</span>
          <FaTextarea :model-value="form.systemPrompt" class="w-full" input-class="font-mono" :autosize="{ minRows: 7, maxRows: 14 }" placeholder="定义 Agent 的角色、任务边界、处理步骤和输出格式" @update:model-value="emit('updateField', 'systemPrompt', $event)" />
          <small>工作流节点指令会在此基础上补充具体任务</small>
        </label>
      </section>

      <section class="form-section">
        <div class="section-title">
          <h3>应用授权摘要</h3>
          <span>{{ toolCodes.length }} 项派生授权</span>
        </div>
        <p class="summary-copy">
          授权范围由画布中各模型节点选择的工具自动汇总，不能在应用级单独编辑。
        </p>
        <div v-if="authorizedTools.length" class="tool-summary" aria-label="模型节点派生的工具授权">
          <div v-for="tool in authorizedTools" :key="tool.code" class="tool-summary-row">
            <span class="tool-type">{{ tool.type }}</span>
            <span><b>{{ tool.name }}</b><small>{{ tool.description || tool.code }}</small></span>
          </div>
        </div>
        <p v-else class="empty-copy">
          尚未有模型节点授权工具；在文本生成、结构化提取、意图分类或视觉理解节点中配置。
        </p>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.inspector { display: flex; width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; flex-direction: column; border-left: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.inspector-header { display: flex; min-height: 66px; align-items: center; justify-content: space-between; padding: 11px 16px; border-bottom: 1px solid var(--color-border-2); }
.inspector-header div { display: grid; min-width: 0; gap: 3px; }
.inspector-header span { color: var(--color-text-3); font-size: 10px; }
.inspector-header strong { overflow: hidden; color: var(--color-text-1); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.inspector-header > :last-child { color: var(--color-text-3); font-size: 18px; }
.inspector-body { min-height: 0; overflow: auto; padding: 0 16px 24px; flex: 1; }
.form-section { padding: 17px 0; border-bottom: 1px solid var(--color-border-2); }
.form-section:last-child { border-bottom: 0; }
.form-section h3 { margin: 0 0 13px; color: var(--color-text-1); font-size: 12px; }
.form-field { display: grid; gap: 7px; margin-top: 13px; color: var(--color-text-2); font-size: 11px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 9px; line-height: 1.5; }
.section-title { display: flex; align-items: center; justify-content: space-between; }
.section-title h3 { margin-bottom: 0; }
.section-title span { color: rgb(var(--primary-6)); font-size: 10px; }
.summary-copy { margin: 0; color: var(--color-text-3); font-size: 10px; line-height: 1.6; }
.tool-summary { display: grid; gap: 8px; margin-top: 12px; }
.tool-summary-row { display: grid; min-width: 0; grid-template-columns: max-content minmax(0, 1fr); align-items: start; gap: 7px; padding-bottom: 8px; border-bottom: 1px solid var(--color-border-2); }
.tool-summary-row:last-child { padding-bottom: 0; border-bottom: 0; }
.tool-summary-row > span:last-child { display: grid; min-width: 0; gap: 2px; }
.tool-summary-row b { overflow: hidden; color: var(--color-text-1); font-size: 11px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.tool-summary-row small { overflow: hidden; color: var(--color-text-3); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.tool-type { padding: 2px 4px; border-radius: 3px; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); font-size: 9px; line-height: 1.3; white-space: nowrap; }
.empty-copy { margin: 0; color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
</style>
