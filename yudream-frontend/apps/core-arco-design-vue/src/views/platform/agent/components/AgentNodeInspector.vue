<script setup lang="ts">
import type { Node } from '@vue-flow/core'
import type { AgentNodeData } from './types'
import type { AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'

const props = defineProps<{
  node: Node<AgentNodeData>
  systemTools: SystemAgentTool[]
  customTools: AgentTool[]
}>()

const emit = defineEmits<{
  update: [data: Partial<AgentNodeData>]
  delete: []
  close: []
}>()

const toolOptions = computed(() => [
  ...props.systemTools.map(tool => ({ label: `[系统] ${tool.name} (${tool.code})`, value: tool.code })),
  ...props.customTools.map(tool => ({ label: `[Python] ${tool.name} (${tool.code})`, value: tool.code })),
])

function updateField(key: keyof AgentNodeData, value: unknown) {
  emit('update', { [key]: value })
}
</script>

<template>
  <aside class="inspector">
    <header class="inspector-header">
      <div>
        <span>节点设置 · {{ node.data.label }}</span>
        <strong>{{ node.data.title || '未命名节点' }}</strong>
      </div>
      <button type="button" title="关闭节点设置" aria-label="关闭节点设置" @click="emit('close')">
        <FaIcon name="i-ri:close-line" />
      </button>
    </header>

    <div class="inspector-body">
      <section class="form-section">
        <h3>基础设置</h3>
        <label class="form-field required">
          <span>节点名称</span>
          <FaInput :model-value="node.data.title" class="w-full" maxlength="40" placeholder="输入便于识别的节点名称" @update:model-value="updateField('title', $event)" />
        </label>
        <label class="form-field">
          <span>节点说明</span>
          <FaTextarea :model-value="node.data.description" class="w-full" :autosize="{ minRows: 3, maxRows: 5 }" maxlength="160" placeholder="简要说明该步骤处理什么内容" @update:model-value="updateField('description', $event)" />
        </label>
      </section>

      <section v-if="node.data.kind === 'tool'" class="form-section">
        <h3>工具配置</h3>
        <label class="form-field required">
          <span>调用工具</span>
          <FaSelect :model-value="node.data.toolCode" class="w-full" :options="toolOptions" placeholder="选择系统工具或 Python 工具" @update:model-value="updateField('toolCode', $event)" />
          <small>需同时在应用设置中勾选该工具，保存时会自动同步</small>
        </label>
      </section>

      <section v-if="node.data.kind === 'condition'" class="form-section">
        <h3>分支规则</h3>
        <label class="form-field required">
          <span>条件表达式</span>
          <FaTextarea :model-value="node.data.condition" class="w-full" input-class="font-mono" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="例如：input.category == 'urgent'" @update:model-value="updateField('condition', $event)" />
          <small>使用上游节点输出字段描述进入当前分支的条件</small>
        </label>
      </section>

      <section class="form-section">
        <h3>输入与输出</h3>
        <label class="form-field">
          <span>输入变量</span>
          <FaInput :model-value="node.data.inputName" class="w-full" placeholder="例如：str.query" @update:model-value="updateField('inputName', $event)" />
        </label>
        <label class="form-field">
          <span>输出变量</span>
          <FaInput :model-value="node.data.outputName" class="w-full" placeholder="例如：str.answer" @update:model-value="updateField('outputName', $event)" />
        </label>
      </section>

      <section v-if="!['start', 'end', 'input'].includes(node.data.kind)" class="form-section">
        <h3>{{ node.data.kind === 'llm' ? '模型指令' : node.data.kind === 'code' ? 'Python 代码' : node.data.kind === 'template' ? '转换模板' : '节点指令' }}</h3>
        <label class="form-field">
          <span>处理内容</span>
          <FaTextarea :model-value="node.data.prompt" class="w-full" input-class="font-mono" :autosize="{ minRows: 8, maxRows: 16 }" :placeholder="node.data.kind === 'code' ? '输入 Python 处理代码' : node.data.kind === 'template' ? '输入文本模板和变量占位符' : '输入该节点的任务指令、参数映射或处理要求'" @update:model-value="updateField('prompt', $event)" />
        </label>
      </section>
    </div>

    <footer class="inspector-footer">
      <FaButton class="w-full" variant="destructive" @click="emit('delete')">
        <FaIcon name="i-ri:delete-bin-line" /> 删除节点
      </FaButton>
    </footer>
  </aside>
</template>

<style scoped>
.inspector { display: flex; width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; flex-direction: column; border-left: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.inspector-header { display: flex; min-height: 66px; align-items: center; justify-content: space-between; padding: 11px 16px; border-bottom: 1px solid var(--color-border-2); }
.inspector-header > div { display: grid; min-width: 0; gap: 3px; }
.inspector-header span { color: var(--color-text-3); font-size: 10px; }
.inspector-header strong { overflow: hidden; color: var(--color-text-1); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.inspector-header button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 6px; color: var(--color-text-3); background: var(--color-fill-2); cursor: pointer; }
.inspector-body { min-height: 0; overflow: auto; padding: 0 16px 24px; flex: 1; }
.form-section { padding: 17px 0; border-bottom: 1px solid var(--color-border-2); }
.form-section:last-child { border-bottom: 0; }
.form-section h3 { margin: 0 0 13px; color: var(--color-text-1); font-size: 12px; }
.form-field { display: grid; gap: 7px; margin-top: 13px; color: var(--color-text-2); font-size: 11px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 9px; line-height: 1.55; }
.inspector-footer { padding: 12px 16px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
</style>
