<script setup lang="ts">
import type { Connection, Edge, Node as FlowNode } from '@vue-flow/core'
import type { AgentConnectionStyle, AgentDebugAttachment, AgentDebugMessage, AgentDebugStatus, AgentNodeData, AgentNodeKind, AgentNodeTemplate } from './components/types'
import type { AgentApplicationPayload, AgentDebugStreamEvent, AgentKnowledgeSpaceOption, AgentModelOption, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MarkerType, useVueFlow, VueFlow } from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import apiAgent from '@/api/modules/platform-agent'
import AgentApplicationInspector from './components/AgentApplicationInspector.vue'
import AgentDebugPanel from './components/AgentDebugPanel.vue'
import AgentEdgeInspector from './components/AgentEdgeInspector.vue'
import AgentNodeInspector from './components/AgentNodeInspector.vue'
import AgentNodePalette from './components/AgentNodePalette.vue'
import AgentWorkflowNode from './components/AgentWorkflowNode.vue'
import { consumeAgentDebugStream } from './config/agent-debug-stream'
import { agentNodePaletteGroups, agentNodeTemplates, findAgentNodeTemplate } from './config/agent-node-catalog'
import { agentModelKind, createAgentNodeData, normalizeAgentNodeData } from './config/agent-node-data'
import { buildAgentRunInput } from './config/agent-run-input'
import { deriveAgentApplicationToolCodes, migrateLegacyToolNodes } from './config/agent-workflow-tools'
import { migrateConditionSourceHandle, validateAgentWorkflow } from './config/agent-workflow-validation'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

const route = useRoute()
const router = useRouter()
const toast = useFaToast()
const modal = useFaModal()
const id = computed(() => typeof route.query.id === 'string' ? route.query.id : '')
const runtimeCode = computed(() => typeof route.query.runtimeCode === 'string' ? route.query.runtimeCode : '')
const runtimeReadonly = computed(() => Boolean(runtimeCode.value))
const saving = ref(false)
const loading = ref(false)
const isDragOver = ref(false)
const rightMode = ref<'config' | 'debug'>('config')
const selectedNodeId = ref('')
const selectedEdgeId = ref('')
const defaultConnectionStyle = ref<AgentConnectionStyle>('arrow')
const customTools = ref<AgentTool[]>([])
const systemTools = ref<SystemAgentTool[]>([])
const agentModels = ref<AgentModelOption[]>([])
const knowledgeSpaces = ref<AgentKnowledgeSpaceOption[]>([])
const debugRunning = ref(false)
const debugMessages = ref<AgentDebugMessage[]>([])
const activeDebugMessageId = ref('')
const nodeDebugStatus = reactive<Record<string, AgentDebugStatus | undefined>>({})
let debugAbortController: AbortController | null = null
const form = reactive<AgentApplicationPayload>({
  name: '',
  code: '',
  description: '',
  icon: 'i-ri:robot-2-line',
  systemPrompt: '',
  workflowJson: '',
  toolCodes: [],
  status: 'DRAFT',
})

const nodes = ref<FlowNode<AgentNodeData>[]>([])
const edges = ref<Edge[]>([])
const selectedNode = computed(() => nodes.value.find(node => node.id === selectedNodeId.value))
const selectedEdge = computed(() => edges.value.find(item => item.id === selectedEdgeId.value))
const selectedSourceName = computed(() => nodeName(selectedEdge.value?.source))
const selectedTargetName = computed(() => nodeName(selectedEdge.value?.target))
const hasSelection = computed(() => Boolean(selectedNodeId.value || selectedEdgeId.value))
const applicationToolCodes = computed(() => deriveAgentApplicationToolCodes(nodes.value))
const legacyToolNodeCount = computed(() => nodes.value.filter(node => node.data.kind === 'tool').length)
const debugModelNodes = computed(() => nodes.value.filter(node => agentModelKind(node.data.kind) === 'chat'))
const debugModels = computed(() => debugModelNodes.value.map(node => agentModels.value.find(model => model.providerCode === node.data.providerCode && model.modelCode === node.data.modelCode)))
const debugModelLabel = computed(() => debugModelNodes.value.length ? `${debugModelNodes.value.length} 个模型节点` : '无需模型')
const debugAllowsImage = computed(() => debugModelNodes.value.some((node, index) => node.data.kind === 'vision' && node.data.vision && debugModels.value[index]?.vision))
const debugAllowsTextFiles = computed(() => Boolean(
  debugModelNodes.value.some(node => ['llm', 'extract', 'classify', 'vision'].includes(node.data.kind) && node.data.acceptFiles)
  || nodes.value.some(node => node.data.kind === 'document'),
))
const { addNodes, addEdges, screenToFlowCoordinate, fitView, setCenter, updateNodeData } = useVueFlow()

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  await Promise.all([loadTools(), loadCatalog()])
  await loadApplication()
  if (!nodes.value.length) {
    seedWorkflow()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  debugAbortController?.abort()
})

async function loadTools() {
  try {
    const [result, system] = await Promise.all([apiAgent.pageTools({ page: 1, size: 200 }), apiAgent.systemTools()])
    customTools.value = result.data.records.filter(item => item.enabled)
    systemTools.value = system.data
  }
  catch {
    toast.error('加载 Agent 工具失败')
  }
}

async function loadCatalog() {
  try {
    const catalog = (await apiAgent.catalog()).data
    agentModels.value = catalog.models || []
    knowledgeSpaces.value = catalog.knowledgeSpaces || []
  }
  catch {
    agentModels.value = []
    knowledgeSpaces.value = []
    toast.error('加载 Agent 节点目录失败')
  }
}

async function loadApplication() {
  if (!id.value && !runtimeCode.value) {
    return
  }
  loading.value = true
  try {
    const application = (await (runtimeCode.value
      ? apiAgent.runtimeDetail(runtimeCode.value)
      : apiAgent.detail(id.value))).data
    Object.assign(form, {
      ...application,
      description: application.description || '',
      systemPrompt: application.systemPrompt || '',
      toolCodes: application.toolCodes || [],
    })
    const flow = application.workflowJson ? JSON.parse(application.workflowJson) : {}
    nodes.value = Array.isArray(flow.nodes) ? flow.nodes.map(normalizeNode) : []
    edges.value = Array.isArray(flow.edges) ? flow.edges.map(normalizeEdge) : []
  }
  catch {
    toast.error('加载 Agent 应用失败，请检查工作流数据')
  }
  finally {
    loading.value = false
  }
}

function templateOf(kind: AgentNodeKind): AgentNodeTemplate {
  // Unknown legacy data should degrade to a model node, never to an input node.
  return findAgentNodeTemplate(kind) || findAgentNodeTemplate('llm')!
}

function normalizeNode(raw: FlowNode<AgentNodeData>): FlowNode<AgentNodeData> {
  const template = templateOf((raw.data?.kind || 'llm') as AgentNodeKind)
  const model = defaultAgentModel(agentModelKind(template.kind))
  return {
    id: raw.id,
    type: 'agent',
    position: raw.position || { x: 0, y: 0 },
    data: normalizeAgentNodeData(template, raw.data, model
      ? { providerCode: model.providerCode, modelCode: model.modelCode, vision: model.vision }
      : {}),
  }
}

function normalizeEdge(raw: Edge): Edge {
  const connectionStyle = raw.data?.connectionStyle === 'line' ? 'line' : 'arrow'
  const sourceKind = nodes.value.find(node => node.id === raw.source)?.data.kind
  const sourceHandle = migrateConditionSourceHandle(sourceKind, raw.sourceHandle, raw.label, raw.data)
  return {
    ...raw,
    sourceHandle,
    type: raw.type || 'smoothstep',
    markerEnd: connectionStyle === 'arrow' ? MarkerType.ArrowClosed : undefined,
    data: { ...raw.data, connectionStyle },
  }
}

function makeNode(template: AgentNodeTemplate, position: { x: number, y: number }): FlowNode<AgentNodeData> {
  const model = defaultAgentModel(agentModelKind(template.kind))
  return {
    id: `${template.kind}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    type: 'agent',
    position,
    data: createAgentNodeData(template, model
      ? { providerCode: model.providerCode, modelCode: model.modelCode, vision: model.vision }
      : {}),
  }
}

function defaultAgentModel(kind = 'chat') {
  const models = agentModels.value.filter(model => model.kind.toLowerCase() === kind)
  return models.find(model => model.configured && model.defaultModel)
    || models.find(model => model.configured)
}

function makeEdge(source: string, target: string, style = defaultConnectionStyle.value): Edge {
  return {
    id: `edge-${source}-${target}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    source,
    target,
    type: 'smoothstep',
    markerEnd: style === 'arrow' ? MarkerType.ArrowClosed : undefined,
    data: { connectionStyle: style },
  }
}

function seedWorkflow() {
  const start = makeNode(templateOf('start'), { x: 70, y: 230 })
  const llm = makeNode(templateOf('llm'), { x: 390, y: 230 })
  const end = makeNode(templateOf('end'), { x: 710, y: 230 })
  nodes.value = [start, llm, end]
  edges.value = [makeEdge(start.id, llm.id), makeEdge(llm.id, end.id)]
}

function addNode(template: AgentNodeTemplate) {
  if (!agentNodeTemplates.some(item => item.kind === template.kind)) {
    toast.error('兼容节点不能从节点面板新建')
    return
  }
  const offset = (nodes.value.length % 5) * 34
  const node = makeNode(template, { x: 250 + offset, y: 150 + offset })
  addNodes(node)
  selectNode(node.id)
}

function onDrop(event: DragEvent) {
  isDragOver.value = false
  const raw = event.dataTransfer?.getData('application/agent-node')
  if (!raw) {
    return
  }
  try {
    const template = JSON.parse(raw) as AgentNodeTemplate
    if (!agentNodeTemplates.some(item => item.kind === template.kind)) {
      toast.error('兼容节点不能从节点面板新建')
      return
    }
    const point = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
    const node = makeNode(template, { x: point.x - 115, y: point.y - 70 })
    addNodes(node)
    selectNode(node.id)
  }
  catch {
    toast.error('无法识别拖入的节点')
  }
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target || connection.source === connection.target) {
    return
  }
  addEdges({
    ...makeEdge(connection.source, connection.target),
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
  })
}

function selectNode(nodeId: string) {
  rightMode.value = 'config'
  selectedNodeId.value = nodeId
  selectedEdgeId.value = ''
}

function selectEdge(edgeId: string) {
  rightMode.value = 'config'
  selectedEdgeId.value = edgeId
  selectedNodeId.value = ''
}

function clearSelection() {
  selectedNodeId.value = ''
  selectedEdgeId.value = ''
}

function updateSelectedNode(data: Partial<AgentNodeData>) {
  if (!selectedNodeId.value) {
    return
  }
  updateNodeData(selectedNodeId.value, data)
}

function changeEdgeStyle(style: AgentConnectionStyle) {
  if (!selectedEdgeId.value) {
    return
  }
  edges.value = edges.value.map(item => item.id === selectedEdgeId.value
    ? { ...item, markerEnd: style === 'arrow' ? MarkerType.ArrowClosed : undefined, data: { ...item.data, connectionStyle: style } }
    : item)
}

function changeEdgeLabel(label: string) {
  edges.value = edges.value.map(item => item.id === selectedEdgeId.value ? { ...item, label } : item)
}

function deleteNode(nodeId: string) {
  nodes.value = nodes.value.filter(node => node.id !== nodeId)
  edges.value = edges.value.filter(item => item.source !== nodeId && item.target !== nodeId)
  clearSelection()
}

function deleteEdge(edgeId: string) {
  edges.value = edges.value.filter(item => item.id !== edgeId)
  clearSelection()
}

function deleteSelection() {
  if (selectedNodeId.value) {
    deleteNode(selectedNodeId.value)
  }
  else if (selectedEdgeId.value) {
    deleteEdge(selectedEdgeId.value)
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Delete' && event.key !== 'Backspace') {
    return
  }
  const target = event.target as HTMLElement | null
  if (target?.matches('input, textarea, [contenteditable="true"]')) {
    return
  }
  if (!hasSelection.value) {
    return
  }
  event.preventDefault()
  deleteSelection()
}

function updateApplicationField(key: keyof AgentApplicationPayload, value: unknown) {
  Object.assign(form, { [key]: value })
}

function migrateLegacyTools() {
  if (!legacyToolNodeCount.value) {
    return
  }
  modal.confirm({
    title: '迁移兼容工具节点',
    content: '仅迁移直接连接在单个模型节点之后且只有一个后继的工具节点；存在分支或多个前驱的节点会保留，避免改变原有工作流。',
    onConfirm: () => {
      const migrated = migrateLegacyToolNodes({ nodes: nodes.value, edges: edges.value })
      const migratedCount = legacyToolNodeCount.value - migrated.nodes.filter(node => node.data?.kind === 'tool').length
      if (!migratedCount) {
        toast.warning('没有可安全迁移的兼容工具节点')
        return
      }
      nodes.value = migrated.nodes.map(node => normalizeNode(node as FlowNode<AgentNodeData>))
      edges.value = migrated.edges.map(edge => normalizeEdge(edge as Edge))
      clearSelection()
      toast.success(`已将 ${migratedCount} 个工具节点迁移到模型节点`)
    },
  })
}

function fitCanvas() {
  nextTick(() => fitView({ padding: 0.22, duration: 260 }))
}

function resetWorkflow() {
  modal.confirm({
    title: '重置工作流',
    content: '当前画布内容将替换为“开始 → 大模型 → 结束”的基础流程。',
    onConfirm: () => {
      clearSelection()
      seedWorkflow()
      fitCanvas()
    },
  })
}

function workflowData() {
  return {
    nodes: nodes.value.map(node => ({ id: node.id, type: 'agent', position: node.position, data: node.data })),
    edges: edges.value.map(item => ({
      id: item.id,
      source: item.source,
      target: item.target,
      sourceHandle: item.sourceHandle,
      targetHandle: item.targetHandle,
      type: item.type || 'smoothstep',
      label: item.label || undefined,
      markerEnd: item.markerEnd,
      data: item.data,
    })),
  }
}

function exportWorkflow() {
  const blob = new Blob([JSON.stringify(workflowData(), null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${form.code || 'agent-workflow'}.json`
  anchor.click()
  URL.revokeObjectURL(url)
}

function nodeName(nodeId?: string) {
  return nodes.value.find(node => node.id === nodeId)?.data.title || nodeId || '-'
}

function activeDebugMessage() {
  return debugMessages.value.find(message => message.id === activeDebugMessageId.value)
}

function resetDebugCanvas() {
  Object.keys(nodeDebugStatus).forEach(key => delete nodeDebugStatus[key])
  edges.value = edges.value.map(item => ({ ...item, class: undefined }))
}

function refreshDebugEdges() {
  edges.value = edges.value.map((item) => {
    const sourceStatus = nodeDebugStatus[item.source]
    const targetStatus = nodeDebugStatus[item.target]
    let debugClass: string | undefined
    if (targetStatus === 'FAILED') {
      debugClass = 'debug-failed'
    }
    else if (targetStatus === 'RUNNING') {
      debugClass = 'debug-active'
    }
    else if (sourceStatus === 'COMPLETED' && targetStatus === 'COMPLETED') {
      debugClass = 'debug-completed'
    }
    return { ...item, class: debugClass }
  })
}

function focusDebugNode(nodeId: string) {
  const node = nodes.value.find(item => item.id === nodeId)
  if (!node) {
    return
  }
  setCenter(node.position.x + 115, node.position.y + 70, { zoom: 1, duration: 260 })
}

function handleDebugEvent(event: AgentDebugStreamEvent) {
  const message = activeDebugMessage()
  if (!message) {
    return
  }
  if (event.type.startsWith('NODE_') && event.nodeId && event.status) {
    nodeDebugStatus[event.nodeId] = event.status
    const step = message.steps?.find(item => item.nodeId === event.nodeId)
    const nextStep = {
      nodeId: event.nodeId,
      nodeTitle: event.nodeTitle || nodeName(event.nodeId),
      nodeKind: event.nodeKind || 'unknown',
      status: event.status,
      message: event.message || '节点状态已更新',
    }
    if (step) {
      Object.assign(step, nextStep)
    }
    else {
      message.steps?.push(nextStep)
    }
    refreshDebugEdges()
    focusDebugNode(event.nodeId)
    return
  }
  if (event.type === 'TEXT_MESSAGE_CHUNK' && event.delta) {
    message.content += event.delta
    return
  }
  if (event.type === 'TOOL_CALL_RESULT' && event.tool) {
    message.tools?.push({
      toolName: event.tool.toolName || '工具',
      action: event.tool.action,
      message: event.tool.message,
    })
    return
  }
  if (event.type === 'RUN_FINISHED') {
    if (!message.content && event.result?.content) {
      message.content = event.result.content
    }
    message.status = 'completed'
    return
  }
  if (event.type === 'RUN_ERROR') {
    message.status = 'failed'
    message.content = message.content || event.message || 'Agent 调试失败'
  }
}

async function startDebug(content: string, attachments: AgentDebugAttachment[] = []) {
  if (debugRunning.value) {
    return
  }
  if (!validateCurrentWorkflow()) {
    return
  }
  const invalidModelIndex = debugModels.value.findIndex(model => !model?.configured)
  if (invalidModelIndex >= 0) {
    selectNode(debugModelNodes.value[invalidModelIndex]!.id)
    toast.error('请先为大模型节点选择已配置 API Key 的模型')
    return
  }
  if (attachments.some(item => item.type.startsWith('image/')) && !debugAllowsImage.value) {
    toast.error('当前模型不支持图片输入')
    return
  }
  if (attachments.some(item => !item.type.startsWith('image/')) && !debugAllowsTextFiles.value) {
    toast.error('当前工作流未开启文本附件输入')
    return
  }
  const applicationId = await save(false, false)
  if (!applicationId) {
    return
  }

  rightMode.value = 'debug'
  resetDebugCanvas()
  const assistantId = `assistant-${Date.now()}`
  const debugInput = buildAgentRunInput(content, attachments)
  const imageDataUrl = attachments.find(item => item.type.startsWith('image/'))?.dataUrl
  const messageAttachments = attachments.map(item => ({
    id: item.id,
    name: item.name,
    type: item.type,
    size: item.size,
    dataUrl: item.type.startsWith('image/') ? item.dataUrl : undefined,
  }))
  debugMessages.value.push(
    { id: `user-${Date.now()}`, role: 'user', content, attachments: messageAttachments },
    { id: assistantId, role: 'assistant', content: '', status: 'streaming', steps: [], tools: [] },
  )
  activeDebugMessageId.value = assistantId
  debugRunning.value = true
  debugAbortController = new AbortController()

  try {
    const request = await apiAgent.debugStreamRequest(applicationId, {
      input: debugInput,
      imageDataUrl,
      attachments: attachments
        .filter(item => !item.type.startsWith('image/') && item.dataUrl)
        .map(item => ({
          name: item.name,
          contentType: item.type || 'application/octet-stream',
          size: item.size,
          dataUrl: item.dataUrl!,
        })),
    })
    const response = await fetch(apiAgent.debugStreamEndpoint(applicationId), {
      ...request,
      signal: debugAbortController.signal,
    })
    await consumeAgentDebugStream(response, raw => handleDebugEvent(raw as AgentDebugStreamEvent))
    const message = activeDebugMessage()
    if (message?.status === 'streaming') {
      message.status = 'failed'
      message.content = message.content || '调试连接已结束，但未收到完成事件'
    }
  }
  catch (error) {
    const message = activeDebugMessage()
    if (error instanceof DOMException && error.name === 'AbortError') {
      if (message) {
        message.status = 'cancelled'
      }
    }
    else {
      if (message) {
        message.status = 'failed'
        message.content = message.content || (error instanceof Error ? error.message : 'Agent 调试失败')
      }
      toast.error('Agent 调试失败')
    }
  }
  finally {
    debugRunning.value = false
    debugAbortController = null
  }
}

function stopDebug() {
  debugAbortController?.abort()
}

function clearDebug() {
  debugMessages.value = []
  activeDebugMessageId.value = ''
  resetDebugCanvas()
}

async function save(publish = false, notify = true): Promise<string> {
  if (!form.name.trim() || !form.code.trim()) {
    toast.error('请在右侧应用设置中填写应用名称和编码')
    clearSelection()
    return ''
  }
  if (!/^[a-z][a-z0-9-]*$/i.test(form.code.trim())) {
    toast.error('应用编码只能包含英文字母、数字和连字符，并以字母开头')
    clearSelection()
    return ''
  }
  if (!nodes.value.some(node => node.data.kind === 'start') || !nodes.value.some(node => node.data.kind === 'end')) {
    toast.error('工作流至少需要一个开始节点和一个结束节点')
    return ''
  }
  const emptyToolNode = nodes.value.find(node => node.data.kind === 'tool' && !node.data.toolCode)
  if (emptyToolNode) {
    selectNode(emptyToolNode.id)
    toast.error('请为工具调用节点选择具体工具')
    return ''
  }
  if (publish && !validateCurrentWorkflow()) {
    return ''
  }

  saving.value = true
  try {
    // Old applications authorized tools at the application level. Preserve that
    // contract until a model or legacy tool node explicitly defines tools.
    form.toolCodes = [...applicationToolCodes.value]
    form.workflowJson = JSON.stringify(workflowData())
    const payload = { ...form, status: form.status === 'DISABLED' ? 'DISABLED' : 'DRAFT' } as AgentApplicationPayload
    const response = id.value ? await apiAgent.update(id.value, payload) : await apiAgent.create(payload)
    if (publish) {
      await apiAgent.publish(response.data.id)
      form.status = 'PUBLISHED'
    }
    else {
      form.status = response.data.status
    }
    if (notify) {
      toast.success(publish ? 'Agent 应用已发布' : 'Agent 编排已保存')
    }
    await router.replace({ path: '/platform/agent/editor', query: { id: response.data.id } })
    return response.data.id
  }
  catch {
    toast.error(publish ? '发布失败，请检查应用配置' : '保存失败，请检查应用配置')
    return ''
  }
  finally {
    saving.value = false
  }
}

function validateCurrentWorkflow() {
  const validation = validateAgentWorkflow(nodes.value, edges.value, {
    models: agentModels.value,
    knowledgeSpaceSlugs: new Set(knowledgeSpaces.value.map(space => space.slug)),
    toolCodes: new Set([...customTools.value.map(tool => tool.code), ...systemTools.value.map(tool => tool.code)]),
  })
  if (validation.valid) {
    return true
  }
  const issue = validation.issues[0]!
  if (issue.nodeId) {
    selectNode(issue.nodeId)
  }
  toast.error(issue.message)
  return false
}
</script>

<template>
  <div v-loading="loading" class="agent-editor-page">
    <FaPageHeader :title="runtimeReadonly ? '查看插件 Agent 编排' : id ? '编辑 Agent 应用' : '新建 Agent 应用'" class="editor-page-header mb-0">
      <div class="header-actions">
        <FaButton variant="outline" @click="router.push('/platform/agent')">
          <FaIcon name="i-ri:arrow-left-line" /> 返回
        </FaButton>
        <FaButton variant="outline" title="导出工作流 JSON" @click="exportWorkflow">
          <FaIcon name="i-ri:download-2-line" /> 导出
        </FaButton>
        <FaButton v-if="!runtimeReadonly" :variant="rightMode === 'debug' ? 'default' : 'outline'" @click="rightMode = 'debug'">
          <FaIcon name="i-ri:bug-line" /> 调试
        </FaButton>
        <FaButton v-if="!runtimeReadonly" variant="outline" :loading="saving" @click="save(false)">
          <FaIcon name="i-ri:save-3-line" /> 保存
        </FaButton>
        <FaButton v-if="!runtimeReadonly" :loading="saving" @click="save(true)">
          <FaIcon name="i-ri:send-plane-line" /> 发布
        </FaButton>
      </div>
    </FaPageHeader>

    <section class="agent-editor" :class="{ 'is-runtime-readonly': runtimeReadonly }">
      <AgentNodePalette v-if="!runtimeReadonly" class="palette-panel" :groups="agentNodePaletteGroups" @add="addNode" />

      <main
        class="flow-canvas"
        :class="{ 'is-drag-over': isDragOver }"
        @dragenter.prevent="isDragOver = true"
        @dragleave.self="isDragOver = false"
        @dragover.prevent
        @drop.prevent="onDrop"
      >
        <div class="canvas-toolbar">
          <span>{{ runtimeReadonly ? '插件运行时编排' : '新建连线' }}</span>
          <div v-if="!runtimeReadonly" class="connection-switch" aria-label="连线样式">
            <button type="button" :class="{ active: defaultConnectionStyle === 'arrow' }" title="箭头连线" @click="defaultConnectionStyle = 'arrow'">
              <FaIcon name="i-ri:arrow-right-line" /> 箭头
            </button>
            <button type="button" :class="{ active: defaultConnectionStyle === 'line' }" title="普通线段" @click="defaultConnectionStyle = 'line'">
              <FaIcon name="i-ri:subtract-line" /> 线段
            </button>
          </div>
          <i v-if="!runtimeReadonly" />
          <button type="button" class="toolbar-icon" title="适应画布" aria-label="适应画布" @click="fitCanvas">
            <FaIcon name="i-ri:fullscreen-line" />
          </button>
          <button v-if="!runtimeReadonly" type="button" class="toolbar-icon" title="重置基础流程" aria-label="重置基础流程" @click="resetWorkflow">
            <FaIcon name="i-ri:restart-line" />
          </button>
          <button v-if="!runtimeReadonly && legacyToolNodeCount" type="button" class="toolbar-icon" title="迁移兼容工具节点" aria-label="迁移兼容工具节点" @click="migrateLegacyTools">
            <FaIcon name="i-ri:git-merge-line" />
          </button>
          <button v-if="!runtimeReadonly" type="button" class="toolbar-icon danger" :disabled="!hasSelection" title="删除选中项" aria-label="删除选中项" @click="deleteSelection">
            <FaIcon name="i-ri:delete-bin-line" />
          </button>
        </div>

        <div v-if="isDragOver" class="drop-overlay">
          <FaIcon name="i-ri:drag-drop-line" />
          <strong>释放以添加节点</strong>
        </div>

        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          class="agent-flow"
          :snap-to-grid="true"
          :snap-grid="[16, 16]"
          :min-zoom="0.25"
          :max-zoom="1.8"
          :delete-key-code="null"
          :nodes-draggable="!runtimeReadonly"
          :nodes-connectable="!runtimeReadonly"
          @connect="onConnect"
          @node-click="selectNode($event.node.id)"
          @edge-click="selectEdge($event.edge.id)"
          @pane-click="clearSelection"
          @init="fitCanvas"
        >
          <Background pattern-color="var(--color-border-3)" :gap="18" :size="1" />
          <Controls position="bottom-left" />
          <MiniMap position="bottom-right" :pannable="true" :zoomable="true" />
          <template #node-agent="nodeProps">
            <AgentWorkflowNode v-bind="nodeProps" :debug-status="nodeDebugStatus[nodeProps.id]" :readonly="runtimeReadonly" @delete="deleteNode" />
          </template>
        </VueFlow>

        <div class="canvas-status">
          <span>{{ nodes.length }} 个节点</span>
          <i />
          <span>{{ edges.length }} 条连线</span>
          <span>{{ debugRunning ? '正在实时调试' : '拖动节点两侧圆点建立连接' }}</span>
        </div>
      </main>

      <AgentDebugPanel
        v-if="rightMode === 'debug'"
        class="inspector-panel"
        :messages="debugMessages"
        :running="debugRunning"
        :allow-image="debugAllowsImage"
        :allow-files="debugAllowsTextFiles"
        :model-label="debugModelLabel"
        @send="startDebug"
        @stop="stopDebug"
        @clear="clearDebug"
        @close="rightMode = 'config'"
      />
      <AgentNodeInspector
        v-else-if="selectedNode"
        class="inspector-panel"
        :node="selectedNode"
        :system-tools="systemTools"
        :custom-tools="customTools"
        :models="agentModels"
        :knowledge-spaces="knowledgeSpaces"
        @update="updateSelectedNode"
        @delete="deleteNode(selectedNode.id)"
        @close="clearSelection"
      />
      <AgentEdgeInspector
        v-else-if="selectedEdge"
        class="inspector-panel"
        :edge="selectedEdge"
        :source-name="selectedSourceName"
        :target-name="selectedTargetName"
        @change-style="changeEdgeStyle"
        @change-label="changeEdgeLabel"
        @delete="deleteEdge(selectedEdge.id)"
        @close="clearSelection"
      />
      <AgentApplicationInspector
        v-else
        class="inspector-panel"
        :form="form"
        :tool-codes="applicationToolCodes"
        :system-tools="systemTools"
        :custom-tools="customTools"
        :code-disabled="Boolean(id)"
        @update-field="updateApplicationField"
      />
    </section>
  </div>
</template>

<style scoped>
.agent-editor-page { min-width: 0; }
.header-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.agent-editor { display: grid; height: calc(100vh - 174px); min-height: 650px; overflow: hidden; grid-template-columns: 242px minmax(0, 1fr) 360px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.agent-editor.is-runtime-readonly { grid-template-columns: minmax(0, 1fr) 360px; }
.agent-editor.is-runtime-readonly .inspector-panel { opacity: 0.86; pointer-events: none; }
.flow-canvas { position: relative; min-width: 0; overflow: hidden; background: var(--color-bg-2); }
.agent-flow { width: 100%; height: 100%; }
.canvas-toolbar { position: absolute; top: 14px; left: 50%; z-index: 10; display: flex; min-height: 40px; align-items: center; gap: 8px; padding: 5px 7px 5px 12px; border: 1px solid var(--color-border-2); border-radius: 7px; background: color-mix(in srgb, var(--color-bg-1), transparent 4%); box-shadow: 0 7px 24px rgb(15 23 42 / 12%); transform: translateX(-50%); backdrop-filter: blur(8px); }
.canvas-toolbar > span { color: var(--color-text-3); font-size: 10px; font-weight: 600; white-space: nowrap; }
.canvas-toolbar > i { width: 1px; height: 24px; background: var(--color-border-2); }
.connection-switch { display: flex; gap: 2px; padding: 3px; border-radius: 6px; background: var(--color-fill-2); }
.connection-switch button { display: flex; height: 26px; align-items: center; gap: 4px; padding: 0 8px; border: 0; border-radius: 4px; color: var(--color-text-3); background: transparent; font-size: 10px; cursor: pointer; }
.connection-switch button.active { color: rgb(var(--primary-6)); background: var(--color-bg-1); box-shadow: 0 2px 6px rgb(15 23 42 / 9%); }
.toolbar-icon { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 5px; color: var(--color-text-2); background: transparent; cursor: pointer; }
.toolbar-icon:hover { background: var(--color-fill-2); }
.toolbar-icon.danger { color: rgb(var(--danger-6)); }
.toolbar-icon:disabled { color: var(--color-text-4); cursor: not-allowed; }
.drop-overlay { position: absolute; z-index: 20; display: grid; inset: 10px; place-items: center; align-content: center; gap: 9px; border: 2px dashed rgb(var(--primary-5)); border-radius: 8px; color: rgb(var(--primary-6)); background: rgb(var(--primary-1) / 86%); pointer-events: none; }
.drop-overlay > :first-child { font-size: 30px; }
.drop-overlay strong { font-size: 13px; }
.canvas-status { position: absolute; bottom: 15px; left: 76px; z-index: 8; display: flex; min-height: 28px; align-items: center; gap: 8px; padding: 0 9px; border: 1px solid var(--color-border-2); border-radius: 6px; color: var(--color-text-3); background: color-mix(in srgb, var(--color-bg-1), transparent 4%); box-shadow: 0 3px 12px rgb(15 23 42 / 7%); font-size: 9px; }
.canvas-status i { width: 1px; height: 12px; background: var(--color-border-2); }
.canvas-status span:last-child { margin-left: 3px; }
.agent-flow :deep(.vue-flow__edge-path) { stroke: var(--color-text-4); stroke-width: 1.6; }
.agent-flow :deep(.vue-flow__edge.selected .vue-flow__edge-path), .agent-flow :deep(.vue-flow__edge:hover .vue-flow__edge-path) { stroke: rgb(var(--primary-6)); stroke-width: 2.2; }
.agent-flow :deep(.vue-flow__edge.debug-active .vue-flow__edge-path) { stroke: rgb(var(--primary-6)); stroke-width: 2.6; stroke-dasharray: 8 5; animation: edge-flow 0.8s linear infinite; }
.agent-flow :deep(.vue-flow__edge.debug-completed .vue-flow__edge-path) { stroke: rgb(var(--success-6)); stroke-width: 2.2; }
.agent-flow :deep(.vue-flow__edge.debug-failed .vue-flow__edge-path) { stroke: rgb(var(--danger-6)); stroke-width: 2.4; }
.agent-flow :deep(.vue-flow__edge-textbg) { fill: var(--color-bg-1); }
.agent-flow :deep(.vue-flow__edge-text) { fill: var(--color-text-2); font-size: 10px; }
.agent-flow :deep(.vue-flow__controls) { overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 6px; box-shadow: 0 5px 18px rgb(15 23 42 / 10%); }
.agent-flow :deep(.vue-flow__controls-button) { border-bottom-color: var(--color-border-2); background: var(--color-bg-1); fill: var(--color-text-2); }
.agent-flow :deep(.vue-flow__minimap) { width: 190px; height: 125px; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 7px; background: var(--color-bg-1); box-shadow: 0 7px 22px rgb(15 23 42 / 11%); }
.agent-flow :deep(.vue-flow__minimap-mask) { fill: color-mix(in srgb, var(--color-fill-3), transparent 18%); }
@keyframes edge-flow { to { stroke-dashoffset: -13; } }
@media (max-width: 1180px) {
  .agent-editor { height: auto; min-height: 0; grid-template-columns: 220px minmax(0, 1fr); grid-template-rows: 640px 480px; }
  .inspector-panel { grid-column: 1 / -1; border-top: 1px solid var(--color-border-2); border-left: 0 !important; }
}
@media (max-width: 760px) {
  .header-actions { justify-content: flex-start; }
  .agent-editor { display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: 350px 620px 520px; }
  .agent-editor.is-runtime-readonly { grid-template-columns: minmax(0, 1fr); grid-template-rows: 620px 520px; }
  .palette-panel, .flow-canvas, .inspector-panel { grid-column: 1; }
  .palette-panel { border-right: 0 !important; border-bottom: 1px solid var(--color-border-2); }
  .canvas-toolbar { left: 10px; max-width: calc(100% - 20px); transform: none; }
  .canvas-toolbar > span { display: none; }
  .canvas-status span:last-child { display: none; }
  .agent-flow :deep(.vue-flow__minimap) { width: 130px; height: 90px; }
}
</style>
