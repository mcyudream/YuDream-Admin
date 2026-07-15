<script setup lang="ts">
import type { AgentApplicationPayload, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'

defineProps<{
  form: AgentApplicationPayload
  systemTools: SystemAgentTool[]
  customTools: AgentTool[]
  codeDisabled?: boolean
}>()

const emit = defineEmits<{
  toggleTool: [code: string, enabled: boolean]
  updateField: [key: keyof AgentApplicationPayload, value: unknown]
}>()
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
          <h3>可用工具</h3>
          <span>{{ form.toolCodes.length }} 已选择</span>
        </div>

        <div class="tool-group">
          <strong>系统工具</strong>
          <label v-for="tool in systemTools" :key="tool.code" class="tool-option">
            <input type="checkbox" :checked="form.toolCodes.includes(tool.code)" @change="emit('toggleTool', tool.code, ($event.target as HTMLInputElement).checked)">
            <span><b>{{ tool.name }}</b><small>{{ tool.description || tool.code }}</small></span>
          </label>
          <p v-if="!systemTools.length" class="empty-copy">
            暂无已启用的系统工具
          </p>
        </div>

        <div class="tool-group">
          <strong>自定义 Python 工具</strong>
          <label v-for="tool in customTools" :key="tool.code" class="tool-option">
            <input type="checkbox" :checked="form.toolCodes.includes(tool.code)" @change="emit('toggleTool', tool.code, ($event.target as HTMLInputElement).checked)">
            <span><b>{{ tool.name }}</b><small>{{ tool.description || tool.code }}</small></span>
          </label>
          <p v-if="!customTools.length" class="empty-copy">
            请先在 Agent 工具页创建 Python 工具
          </p>
        </div>
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
.tool-group { display: grid; gap: 8px; margin-top: 15px; }
.tool-group > strong { color: var(--color-text-3); font-size: 10px; }
.tool-option { display: grid; min-width: 0; grid-template-columns: 16px minmax(0, 1fr); align-items: start; gap: 8px; padding: 8px; border: 1px solid var(--color-border-2); border-radius: 6px; cursor: pointer; }
.tool-option:hover { border-color: rgb(var(--primary-3)); background: var(--color-fill-1); }
.tool-option input { margin-top: 2px; accent-color: rgb(var(--primary-6)); }
.tool-option > span { display: grid; min-width: 0; gap: 2px; }
.tool-option b { color: var(--color-text-1); font-size: 11px; font-weight: 500; }
.tool-option small { overflow: hidden; color: var(--color-text-3); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.empty-copy { margin: 0; color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
</style>
