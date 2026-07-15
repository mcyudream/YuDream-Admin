<script setup lang="ts">
import type { Node } from '@vue-flow/core'
import type { AgentNodeData } from './types'
import type { AgentKnowledgeSpaceOption, AgentModelOption, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'

const props = defineProps<{
  node: Node<AgentNodeData>
  systemTools: SystemAgentTool[]
  customTools: AgentTool[]
  models: AgentModelOption[]
  knowledgeSpaces: AgentKnowledgeSpaceOption[]
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

const knowledgeSpaceOptions = computed(() => props.knowledgeSpaces.map(space => ({
  label: `${space.name} (${space.slug})`,
  value: space.slug,
})))

const requiredModelKind = computed(() => {
  if (props.node.data.kind === 'llm' || props.node.data.kind === 'understand') {
    return 'chat'
  }
  return props.node.data.kind === 'embedding' || props.node.data.kind === 'rerank'
    ? props.node.data.kind
    : ''
})

const kindModels = computed(() => props.models.filter(model => model.kind.toLowerCase() === requiredModelKind.value))

const providerOptions = computed(() => {
  const providers = new Map<string, { label: string, value: string, disabled: boolean }>()
  for (const model of kindModels.value) {
    const current = providers.get(model.providerCode)
    providers.set(model.providerCode, {
      label: `${model.providerName} (${model.providerCode})`,
      value: model.providerCode,
      disabled: current ? current.disabled && !model.configured : !model.configured,
    })
  }
  return [...providers.values()]
})

const modelOptions = computed(() => kindModels.value
  .filter(model => model.providerCode === props.node.data.providerCode)
  .map(model => ({
    label: `${model.modelName}${model.vision ? ' · Vision' : ''}${model.configured ? '' : ' · 未配置 Key'}`,
    value: model.modelCode,
    disabled: !model.configured,
  })))

const selectedModel = computed(() => kindModels.value.find(model => model.providerCode === props.node.data.providerCode && model.modelCode === props.node.data.modelCode))
const selectedKnowledgeSpace = computed(() => props.knowledgeSpaces.find(space => space.slug === props.node.data.knowledgeSpaceSlug))
const isKnowledgeNode = computed(() => props.node.data.kind === 'search' || props.node.data.kind === 'vector')
const isPromptNode = computed(() => props.node.data.kind === 'llm' || props.node.data.kind === 'understand')

const modelSectionTitle = computed(() => {
  if (props.node.data.kind === 'embedding') {
    return 'Embedding 模型'
  }
  if (props.node.data.kind === 'rerank') {
    return '重排模型'
  }
  return '对话模型'
})

const documentModeOptions = [{ label: '提取纯文本', value: 'text' }]
const citationFormatOptions = [
  { label: 'Markdown 引用', value: 'markdown' },
  { label: 'JSON 引用列表', value: 'json' },
]

function updateField(key: keyof AgentNodeData, value: unknown) {
  emit('update', { [key]: value })
}

function updateTopK(value: unknown) {
  const numberValue = Number(value)
  updateField('topK', Number.isFinite(numberValue) ? Math.min(100, Math.max(1, Math.round(numberValue))) : 5)
}

function changeProvider(providerCode: string) {
  const model = kindModels.value.find(item => item.providerCode === providerCode && item.configured && item.defaultModel)
    || kindModels.value.find(item => item.providerCode === providerCode && item.configured)
  emit('update', {
    providerCode,
    modelCode: model?.modelCode || '',
    vision: model?.vision || false,
  })
}

function changeModel(modelCode: string) {
  const model = kindModels.value.find(item => item.providerCode === props.node.data.providerCode && item.modelCode === modelCode)
  emit('update', { modelCode, vision: model?.vision || false })
}

function changeKnowledgeSpace(slug: string) {
  const space = props.knowledgeSpaces.find(item => item.slug === slug)
  emit('update', {
    knowledgeSpaceSlug: slug,
    topK: space?.topK || props.node.data.topK || 5,
    graphExpansion: space?.graphEnabled ? props.node.data.graphExpansion : false,
  })
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
          <FaTextarea :model-value="node.data.description" class="w-full" :autosize="{ minRows: 2, maxRows: 4 }" maxlength="160" placeholder="说明该节点负责处理的业务内容" @update:model-value="updateField('description', $event)" />
        </label>
      </section>

      <section v-if="requiredModelKind" class="form-section">
        <h3>{{ modelSectionTitle }}</h3>
        <label class="form-field required">
          <span>模型供应商</span>
          <FaSelect :model-value="node.data.providerCode" class="w-full" :options="providerOptions" placeholder="选择已配置的模型供应商" @update:model-value="changeProvider(String($event || ''))" />
        </label>
        <label class="form-field required">
          <span>{{ modelSectionTitle }}</span>
          <FaSelect :model-value="node.data.modelCode" class="w-full" :options="modelOptions" :disabled="!node.data.providerCode" placeholder="选择该节点实际调用的模型" @update:model-value="changeModel(String($event || ''))" />
          <small v-if="selectedModel">{{ selectedModel.vision ? '支持文本和图片输入' : '支持文本输入' }}</small>
          <small v-else class="field-error">尚未选择可用模型，该节点无法运行</small>
        </label>
        <label v-if="node.data.kind === 'llm'" class="toggle-option">
          <span><b>允许文本附件</b><small>调试时把 TXT、Markdown、JSON、CSV 等文本内容加入模型上下文</small></span>
          <FaSwitch :model-value="node.data.acceptFiles" @update:model-value="updateField('acceptFiles', $event)" />
        </label>
      </section>

      <section v-if="isKnowledgeNode" class="form-section">
        <h3>知识检索</h3>
        <label class="form-field required">
          <span>知识空间</span>
          <FaSelect :model-value="node.data.knowledgeSpaceSlug" class="w-full" :options="knowledgeSpaceOptions" placeholder="选择要检索的知识空间" @update:model-value="changeKnowledgeSpace(String($event || ''))" />
          <small v-if="!knowledgeSpaceOptions.length" class="field-error">暂无可用知识空间，请先在知识库中完成配置</small>
        </label>
        <div class="form-grid">
          <label class="form-field required">
            <span>召回数量</span>
            <FaInput :model-value="String(node.data.topK)" class="w-full" type="number" placeholder="1 - 100" @update:model-value="updateTopK" />
          </label>
          <label class="form-field">
            <span>路径前缀</span>
            <FaInput :model-value="node.data.pathPrefix" class="w-full" placeholder="例如 /产品/手册" @update:model-value="updateField('pathPrefix', $event)" />
          </label>
        </div>
        <label class="toggle-option" :class="{ disabled: selectedKnowledgeSpace && !selectedKnowledgeSpace.graphEnabled }">
          <span><b>图谱扩展</b><small>沿知识图谱补充关联内容，仅对已启用图谱的空间生效</small></span>
          <FaSwitch :model-value="node.data.graphExpansion" :disabled="Boolean(selectedKnowledgeSpace && !selectedKnowledgeSpace.graphEnabled)" @update:model-value="updateField('graphExpansion', $event)" />
        </label>
      </section>

      <section v-if="node.data.kind === 'tool'" class="form-section">
        <h3>工具调用</h3>
        <label class="form-field required">
          <span>调用工具</span>
          <FaSelect :model-value="node.data.toolCode" class="w-full" :options="toolOptions" placeholder="选择系统工具或 Python 工具" @update:model-value="updateField('toolCode', $event)" />
          <small>保存时会自动把该工具加入 Agent 应用授权范围</small>
        </label>
      </section>

      <section v-if="node.data.kind === 'condition'" class="form-section">
        <h3>分支规则</h3>
        <label class="form-field required">
          <span>条件表达式</span>
          <FaTextarea :model-value="node.data.condition" class="w-full" input-class="font-mono" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="例如：intent.category == 'urgent'" @update:model-value="updateField('condition', $event)" />
          <small>表达式结果为真时走 true 连线，否则走 false 连线</small>
        </label>
      </section>

      <section v-if="node.data.kind === 'code'" class="form-section">
        <h3>Python 代码</h3>
        <label class="form-field required">
          <span>执行脚本</span>
          <FaTextarea :model-value="node.data.code" class="w-full" input-class="font-mono" :autosize="{ minRows: 10, maxRows: 18 }" placeholder="从标准输入读取 JSON，并将处理结果输出到标准输出" @update:model-value="updateField('code', $event)" />
          <small>运行上下文会以 JSON 写入标准输入，请用 print 输出节点结果</small>
        </label>
      </section>

      <section v-if="node.data.kind === 'template'" class="form-section">
        <h3>文本模板</h3>
        <label class="form-field required">
          <span>模板内容</span>
          <FaTextarea :model-value="node.data.template" class="w-full" input-class="font-mono" :autosize="{ minRows: 8, maxRows: 16 }" placeholder="例如：问题：{{query}}&#10;答案：{{answer}}" @update:model-value="updateField('template', $event)" />
          <small>使用双花括号引用运行上下文中的值，例如 <code v-pre>{{query}}</code></small>
        </label>
      </section>

      <section v-if="isPromptNode" class="form-section">
        <h3>{{ node.data.kind === 'understand' ? '理解指令' : '模型指令' }}</h3>
        <label class="form-field required">
          <span>提示词</span>
          <FaTextarea :model-value="node.data.prompt" class="w-full" :autosize="{ minRows: 8, maxRows: 16 }" placeholder="输入该模型节点的任务、输出格式与约束" @update:model-value="updateField('prompt', $event)" />
          <small v-if="node.data.kind === 'understand'">建议明确需要提取的意图、实体字段和 JSON 输出结构</small>
        </label>
      </section>

      <section v-if="node.data.kind === 'document'" class="form-section">
        <h3>文档解析</h3>
        <label class="form-field required">
          <span>文档输入</span>
          <FaInput :model-value="node.data.documentInput" class="w-full" placeholder="attachment 或变量路径，例如 files.contract" @update:model-value="updateField('documentInput', $event)" />
          <small>attachment 表示使用调试附件，也可填写上游节点输出变量</small>
        </label>
        <label class="form-field required">
          <span>解析模式</span>
          <FaSelect :model-value="node.data.documentMode" class="w-full" :options="documentModeOptions" placeholder="选择文档解析模式" @update:model-value="updateField('documentMode', $event)" />
        </label>
      </section>

      <section v-if="node.data.kind === 'citation'" class="form-section">
        <h3>引用输出</h3>
        <label class="form-field required">
          <span>引用来源变量</span>
          <FaInput :model-value="node.data.citationSource" class="w-full" placeholder="例如 documents" @update:model-value="updateField('citationSource', $event)" />
          <small>填写包含检索文档或片段列表的运行变量</small>
        </label>
        <label class="form-field required">
          <span>输出格式</span>
          <FaSelect :model-value="node.data.citationFormat" class="w-full" :options="citationFormatOptions" placeholder="选择引用输出格式" @update:model-value="updateField('citationFormat', $event)" />
        </label>
      </section>

      <section class="form-section">
        <h3>输入与输出映射</h3>
        <label class="form-field">
          <span>输入变量</span>
          <FaInput :model-value="node.data.inputVariable" class="w-full" placeholder="例如 query 或 search.documents" @update:model-value="updateField('inputVariable', $event)" />
          <small>从运行上下文读取，留空时使用直接上游节点输出</small>
        </label>
        <label class="form-field required">
          <span>输出变量</span>
          <FaInput :model-value="node.data.outputVariable" class="w-full" placeholder="例如 answer" @update:model-value="updateField('outputVariable', $event)" />
          <small>执行结果将写入此变量，端口类型为 {{ node.data.outputName }}</small>
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
.form-section { padding: 16px 0; border-bottom: 1px solid var(--color-border-2); }
.form-section:last-child { border-bottom: 0; }
.form-section h3 { margin: 0 0 12px; color: var(--color-text-1); font-size: 12px; }
.form-field { display: grid; min-width: 0; gap: 7px; margin-top: 12px; color: var(--color-text-2); font-size: 11px; }
.form-section > .form-field:first-of-type { margin-top: 0; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 9px; line-height: 1.55; }
.form-field .field-error { color: rgb(var(--danger-6)); }
.form-grid { display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 10px; }
.toggle-option { display: grid; min-height: 54px; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; margin-top: 12px; padding: 9px 10px; border: 1px solid var(--color-border-2); border-radius: 6px; }
.toggle-option > span { display: grid; gap: 3px; }
.toggle-option b { color: var(--color-text-1); font-size: 10px; font-weight: 500; }
.toggle-option small { color: var(--color-text-3); font-size: 8px; line-height: 1.5; }
.toggle-option.disabled { opacity: 0.65; }
.inspector-footer { padding: 12px 16px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
</style>
