<script setup lang="ts">
import type { AgentFlowNode, AgentNodeData } from './types'
import type { AgentKnowledgeSpaceOption, AgentModelOption, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'
import { agentModelKind, isAgentChatModelNode, isAgentToolConfigModelNode } from '../config/agent-node-data'

const props = defineProps<{
  node: AgentFlowNode
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

const toolSearch = ref('')
const toolModeOptions = [
  { label: '禁用工具', value: 'NONE' },
  { label: '模型自主调用', value: 'AUTO' },
  { label: '必须调用工具', value: 'REQUIRED' },
]

const modelToolOptions = computed(() => {
  const keyword = toolSearch.value.trim().toLocaleLowerCase()
  const matches = (tool: { name: string, code: string, description?: string }) => !keyword
    || `${tool.name} ${tool.code} ${tool.description || ''}`.toLocaleLowerCase().includes(keyword)
  const system = props.systemTools.filter(matches).map(tool => ({ label: `${tool.name} (${tool.code})`, value: tool.code }))
  const custom = props.customTools.filter(matches).map(tool => ({ label: `${tool.name} (${tool.code})`, value: tool.code }))
  return [
    ...(system.length ? [{ label: '系统工具', options: system }] : []),
    ...(custom.length ? [{ label: '自定义 Python 工具', options: custom }] : []),
  ]
})

const knowledgeSpaceOptions = computed(() => props.knowledgeSpaces.map(space => ({
  label: `${space.name} (${space.slug})`,
  value: space.slug,
})))

const requiredModelKind = computed(() => agentModelKind(props.node.data.kind))

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
const isPromptNode = computed(() => isAgentChatModelNode(props.node.data.kind))
const isModelToolNode = computed(() => isAgentToolConfigModelNode(props.node.data.kind))
const isExtractNode = computed(() => props.node.data.kind === 'extract')
const isClassifyNode = computed(() => props.node.data.kind === 'classify')
const isVisionNode = computed(() => props.node.data.kind === 'vision')
const isAttachmentChatNode = computed(() => ['llm', 'extract', 'classify', 'vision'].includes(props.node.data.kind))

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

function changeToolMode(value: unknown) {
  const toolMode = value === 'AUTO' || value === 'REQUIRED' ? value : 'NONE'
  emit('update', {
    toolMode,
    toolCodes: toolMode === 'NONE' ? [] : [...props.node.data.toolCodes],
    toolConfigDeclared: true,
  })
}

function changeToolCodes(value: unknown) {
  const codes = Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && Boolean(item.trim())).map(item => item.trim())
    : []
  emit('update', { toolCodes: [...new Set(codes)], toolConfigDeclared: true })
}

function changeClasses(value: unknown) {
  const classes = typeof value === 'string'
    ? value.split(/[\n,，]/).map(item => item.trim()).filter(Boolean)
    : []
  emit('update', { classes: [...new Set(classes)] })
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
        <label v-if="isAttachmentChatNode" class="toggle-option">
          <span><b>允许文本附件</b><small>调试时把 TXT、Markdown、JSON、CSV 等文本内容加入模型上下文</small></span>
          <FaSwitch :model-value="node.data.acceptFiles" @update:model-value="updateField('acceptFiles', $event)" />
        </label>
      </section>

      <section v-if="isModelToolNode" class="form-section">
        <h3>模型工具调用</h3>
        <label class="form-field required">
          <span>调用策略</span>
          <FaSelect :model-value="node.data.toolMode" class="w-full" :options="toolModeOptions" placeholder="选择模型调用工具的策略" @update:model-value="changeToolMode" />
          <small>自主调用由模型按任务判断；必须调用要求模型至少完成一次工具调用。</small>
        </label>
        <label class="form-field" :class="{ disabled: node.data.toolMode === 'NONE' }">
          <span>可用工具</span>
          <FaInput v-model="toolSearch" class="w-full" placeholder="搜索系统工具或 Python 工具" :disabled="node.data.toolMode === 'NONE'" />
          <FaSelect
            :model-value="node.data.toolCodes"
            class="w-full"
            multiple
            :disabled="node.data.toolMode === 'NONE'"
            :options="modelToolOptions"
            placeholder="按分组选择此模型可调用的工具"
            @update:model-value="changeToolCodes"
          />
          <small v-if="node.data.toolMode === 'NONE'">当前模型不会调用工具。</small>
          <small v-else-if="!modelToolOptions.length" class="field-error">没有匹配的已启用工具，请调整搜索条件或先在工具页启用工具。</small>
          <small v-else>应用授权会从所有模型节点的选择自动汇总。</small>
        </label>
      </section>

      <section v-if="node.data.kind === 'understand'" class="form-section compatibility-section">
        <h3>兼容理解节点</h3>
        <p class="compatibility-copy">
          该历史节点仅保留原有理解逻辑，不能配置模型工具。请使用意图分类或结构化提取节点编排新的模型任务。
        </p>
      </section>

      <section v-if="isExtractNode" class="form-section">
        <h3>结构化输出</h3>
        <label class="form-field required">
          <span>输出 JSON Schema</span>
          <FaTextarea :model-value="node.data.outputSchema" class="w-full" input-class="font-mono" :autosize="{ minRows: 8, maxRows: 16 }" placeholder='例如：{"type":"object","properties":{"title":{"type":"string"}}}' @update:model-value="updateField('outputSchema', $event)" />
          <small>必须是 JSON 对象；模型结果将按该结构写入输出变量。</small>
        </label>
      </section>

      <section v-if="isClassifyNode" class="form-section">
        <h3>分类配置</h3>
        <label class="form-field required">
          <span>分类标签</span>
          <FaTextarea :model-value="node.data.classes.join('\n')" class="w-full" :autosize="{ minRows: 4, maxRows: 10 }" placeholder="每行一个标签，例如：\n咨询\n投诉\n其他" @update:model-value="changeClasses($event)" />
          <small>至少填写两个不重复标签，模型会从中选择一个分类。</small>
        </label>
      </section>

      <section v-if="isVisionNode" class="form-section">
        <h3>图片输入</h3>
        <label class="form-field required">
          <span>图片变量</span>
          <FaInput :model-value="node.data.imageVariable" class="w-full" placeholder="例如 attachments[0] 或 product.coverImage" @update:model-value="updateField('imageVariable', $event)" />
          <small>填写调试图片或运行上下文中图片 Data URL 的变量路径。</small>
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

      <section v-if="node.data.kind === 'tool'" class="form-section compatibility-section">
        <h3>兼容工具节点</h3>
        <label class="form-field required">
          <span>历史调用工具</span>
          <FaSelect :model-value="node.data.toolCode" class="w-full" :options="modelToolOptions" placeholder="选择该历史节点调用的工具" @update:model-value="updateField('toolCode', $event)" />
          <small>新工作流不再创建该节点。可通过画布工具栏迁移安全路径，将工具配置并入前驱模型节点。</small>
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
        <h3>模型指令</h3>
        <label class="form-field required">
          <span>提示词</span>
          <FaTextarea :model-value="node.data.prompt" class="w-full" :autosize="{ minRows: 8, maxRows: 16 }" placeholder="输入该模型节点的任务、输出格式与约束" @update:model-value="updateField('prompt', $event)" />
          <small v-if="isExtractNode">明确字段含义、缺失值处理和与 JSON Schema 对应的输出约束。</small>
          <small v-else-if="isClassifyNode">说明分类标准、边界条件以及无法判断时应使用的标签。</small>
          <small v-else-if="isVisionNode">说明图片与文本如何结合分析，不要假设图片中不存在的信息。</small>
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
.form-field.disabled { opacity: 0.65; }
.form-grid { display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 10px; }
.toggle-option { display: grid; min-height: 54px; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 10px; margin-top: 12px; padding: 9px 10px; border: 1px solid var(--color-border-2); border-radius: 6px; }
.toggle-option > span { display: grid; gap: 3px; }
.toggle-option b { color: var(--color-text-1); font-size: 10px; font-weight: 500; }
.toggle-option small { color: var(--color-text-3); font-size: 8px; line-height: 1.5; }
.toggle-option.disabled { opacity: 0.65; }
.compatibility-section h3 { color: rgb(var(--warning-6)); }
.compatibility-copy { margin: 0; color: var(--color-text-3); font-size: 10px; line-height: 1.6; }
.inspector-footer { padding: 12px 16px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
</style>
