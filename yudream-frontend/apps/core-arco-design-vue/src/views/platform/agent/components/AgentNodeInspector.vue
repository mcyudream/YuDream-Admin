<script setup lang="ts">
import type { Node } from '@vue-flow/core'
import type { AgentNodeData } from './types'
import type { AgentModelOption, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'

const props = defineProps<{
  node: Node<AgentNodeData>
  systemTools: SystemAgentTool[]
  customTools: AgentTool[]
  models: AgentModelOption[]
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

const providerOptions = computed(() => {
  const providers = new Map<string, { label: string, value: string, disabled: boolean }>()
  for (const model of props.models) {
    const current = providers.get(model.providerCode)
    providers.set(model.providerCode, {
      label: `${model.providerName} (${model.providerCode})`,
      value: model.providerCode,
      disabled: current ? current.disabled && !model.configured : !model.configured,
    })
  }
  return [...providers.values()]
})

const modelOptions = computed(() => props.models
  .filter(model => model.providerCode === props.node.data.providerCode)
  .map(model => ({
    label: `${model.modelName}${model.vision ? ' · Vision' : ''}${model.configured ? '' : ' · 未配置 Key'}`,
    value: model.modelCode,
    disabled: !model.configured,
  })))

const selectedModel = computed(() => props.models.find(model => model.providerCode === props.node.data.providerCode && model.modelCode === props.node.data.modelCode))

function updateField(key: keyof AgentNodeData, value: unknown) {
  emit('update', { [key]: value })
}

function changeProvider(providerCode: string) {
  const model = props.models.find(item => item.providerCode === providerCode && item.configured && item.defaultModel)
    || props.models.find(item => item.providerCode === providerCode && item.configured)
  emit('update', {
    providerCode,
    modelCode: model?.modelCode || '',
    vision: model?.vision || false,
  })
}

function changeModel(modelCode: string) {
  const model = props.models.find(item => item.providerCode === props.node.data.providerCode && item.modelCode === modelCode)
  emit('update', { modelCode, vision: model?.vision || false })
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

      <section v-if="node.data.kind === 'llm'" class="form-section">
        <h3>模型配置</h3>
        <label class="form-field required">
          <span>模型供应商</span>
          <FaSelect :model-value="node.data.providerCode" class="w-full" :options="providerOptions" placeholder="选择已配置的供应商" @update:model-value="changeProvider(String($event || ''))" />
        </label>
        <label class="form-field required">
          <span>对话模型</span>
          <FaSelect :model-value="node.data.modelCode" class="w-full" :options="modelOptions" :disabled="!node.data.providerCode" placeholder="选择模型" @update:model-value="changeModel(String($event || ''))" />
          <small v-if="selectedModel">{{ selectedModel.vision ? '支持文本和图片输入' : '支持文本输入' }}</small>
          <small v-else class="field-error">尚未选择可用模型，无法运行调试</small>
        </label>
        <label class="check-option">
          <input :checked="node.data.acceptFiles" type="checkbox" @change="updateField('acceptFiles', ($event.target as HTMLInputElement).checked)">
          <span><b>允许文本附件</b><small>调试时读取 TXT、Markdown、JSON、CSV 等文本内容作为模型输入</small></span>
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
.form-field .field-error { color: rgb(var(--danger-6)); }
.check-option { display: grid; grid-template-columns: 16px minmax(0, 1fr); align-items: start; gap: 8px; margin-top: 13px; padding: 9px; border: 1px solid var(--color-border-2); border-radius: 6px; cursor: pointer; }
.check-option input { margin-top: 2px; accent-color: rgb(var(--primary-6)); }
.check-option span { display: grid; gap: 3px; }
.check-option b { color: var(--color-text-1); font-size: 10px; font-weight: 500; }
.check-option small { color: var(--color-text-3); font-size: 8px; line-height: 1.5; }
.inspector-footer { padding: 12px 16px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
</style>
