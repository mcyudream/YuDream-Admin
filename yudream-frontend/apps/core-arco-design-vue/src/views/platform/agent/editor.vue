<script setup lang="ts">
import type { YdWorkflowCanvasInstance, YdWorkflowConnection, YdWorkflowEdge, YdWorkflowNode } from '@yudream/components'
import type { AgentConnectionStyle, AgentDebugAttachment, AgentDebugStatus, AgentFlowEdgeView, AgentFlowNode, AgentNodeData, AgentNodeKind, AgentNodeTemplate } from './components/types'
import type { AgentApplicationPayload, AgentKnowledgeSpaceOption, AgentModelOption, AgentTool, AgentToolCandidate, AiToolModuleCatalog, SystemAgentTool } from '@/api/modules/platform-agent'
import apiAgent from '@/api/modules/platform-agent'
import { useAiRunTask } from '@/composables/use-ai-run-task'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { projectAiRunActivity } from '@/utils/ai-run-projection'
import AgentApplicationInspector from './components/AgentApplicationInspector.vue'
import AgentDebugPanel from './components/AgentDebugPanel.vue'
import AgentEdgeInspector from './components/AgentEdgeInspector.vue'
import AgentNodeInspector from './components/AgentNodeInspector.vue'
import AgentNodePalette from './components/AgentNodePalette.vue'
import AgentWorkflowNode from './components/AgentWorkflowNode.vue'
import { agentNodePaletteGroups, agentNodeTemplates, findAgentNodeTemplate } from './config/agent-node-catalog'
import { agentModelKind, createAgentNodeData, normalizeAgentNodeData } from './config/agent-node-data'
import { buildAgentRunInput } from './config/agent-run-input'
import { deriveAgentApplicationToolCodes, migrateLegacyToolNodes } from './config/agent-workflow-tools'
import { migrateConditionSourceHandle, validateAgentWorkflow } from './config/agent-workflow-validation'

const route = useRoute()
const router = useRouter()
const toast = useYdToast()
const modal = useYdModal()
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
const toolModuleCatalog = ref<AiToolModuleCatalog>()
const toolCandidates = ref<AgentToolCandidate[]>([])
const agentModels = ref<AgentModelOption[]>([])
const knowledgeSpaces = ref<AgentKnowledgeSpaceOption[]>([])
const aiRuntime = useAiAssistantStore()
const debugTask = useAiRunTask(() => id.value ? `agent-debug:${id.value}` : undefined)
const debugPreparing = ref(false)
const debugRunning = computed(() => debugPreparing.value || debugTask.running.value)
const debugMessages = computed(() => id.value ? aiRuntime.taskMessages(`agent-debug:${id.value}`) : [])
const debugHarness = computed(() => ({ name: 'AgentScope Harness', runId: debugTask.run.value?.id, status: debugRunning.value ? debugTask.run.value?.status === 'WAITING' ? 'waiting' as const : 'running' as const : debugTask.run.value?.status === 'FAILED' ? 'error' as const : debugTask.run.value?.status === 'CANCELLED' ? 'cancelled' as const : debugTask.run.value ? 'completed' as const : 'idle' as const }))
const nodeDebugStatus = reactive<Record<string, AgentDebugStatus | undefined>>({})
const form = reactive<AgentApplicationPayload>({
  name: '',
  code: '',
  description: '',
  icon: 'morph:bot',
  systemPrompt: '',
  workflowJson: '',
  toolCodes: [],
  workspaceScope: 'SHARED',
  status: 'DRAFT',
})

const canvas = ref<YdWorkflowCanvasInstance<AgentNodeData, Record<string, unknown>> | null>(null)
const nodes = ref<YdWorkflowNode<AgentNodeData>[]>([])
const edges = ref<YdWorkflowEdge<Record<string, unknown>>[]>([])
const selectedNode = computed<AgentFlowNode | undefined>(() => {
  const node = nodes.value.find(node => node.id === selectedNodeId.value)
  return node ? { ...node, type: node.type || 'agent' } : undefined
})
const selectedEdge = computed<AgentFlowEdgeView | undefined>(() => {
  const views = edges.value as unknown as AgentFlowEdgeView[]
  return views.find(edge => edge.id === selectedEdgeId.value)
})
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

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  await Promise.all([loadTools(), loadCatalog()])
  await loadApplication()
  if (!nodes.value.length) {
    seedWorkflow()
  }
  await nextTick()
  if (nodes.value.length) {
    fitCanvas()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})

async function loadTools() {
  try {
    const [result, system, modules, candidates] = await Promise.all([
      apiAgent.pageTools({ page: 1, size: 200 }),
      apiAgent.systemTools(),
      apiAgent.toolModules(),
      apiAgent.pageToolCandidates({ page: 1, size: 200 }),
    ])
    customTools.value = result.data.records.filter(item => item.enabled)
    systemTools.value = system.data
    toolModuleCatalog.value = modules.data
    toolCandidates.value = candidates.data.records
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
      workspaceScope: application.workspaceScope || 'SHARED',
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

async function importRuntimeApplication() {
  if (!runtimeCode.value || saving.value) {
    return
  }
  saving.value = true
  try {
    const imported = (await apiAgent.importRuntime(runtimeCode.value)).data
    toast.success('插件编排已导入为本地草稿')
    await router.replace({ path: '/platform/agent/editor', query: { id: imported.id } })
  }
  catch {
    toast.error('导入插件编排失败')
  }
  finally {
    saving.value = false
  }
}

function templateOf(kind: AgentNodeKind): AgentNodeTemplate {
  // Unknown legacy data should degrade to a model node, never to an input node.
  return findAgentNodeTemplate(kind) || findAgentNodeTemplate('llm')!
}

function normalizeNode(raw: AgentFlowNode): AgentFlowNode {
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

function normalizeEdge(raw: YdWorkflowEdge<Record<string, unknown>>): YdWorkflowEdge<Record<string, unknown>> {
  const connectionStyle = raw.data?.connectionStyle === 'line' ? 'line' : 'arrow'
  const sourceKind = nodes.value.find(node => node.id === raw.source)?.data.kind
  const sourceHandle = migrateConditionSourceHandle(sourceKind, raw.sourceHandle, raw.label, raw.data)
  return {
    ...raw,
    sourceHandle,
    type: raw.type || 'smoothstep',
    markerEnd: connectionStyle === 'arrow' ? 'arrowclosed' : undefined,
    data: { ...raw.data, connectionStyle },
  }
}

function makeNode(template: AgentNodeTemplate, position: { x: number, y: number }): AgentFlowNode {
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

function makeEdge(source: string, target: string, style = defaultConnectionStyle.value): YdWorkflowEdge<Record<string, unknown>> {
  return {
    id: `edge-${source}-${target}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    source,
    target,
    type: 'smoothstep',
    markerEnd: style === 'arrow' ? 'arrowclosed' : undefined,
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
  canvas.value?.addNodes(node)
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
    const point = canvas.value?.screenToCanvasCoordinate({ x: event.clientX, y: event.clientY })
    if (!point) {
      return
    }
    const node = makeNode(template, { x: point.x - 115, y: point.y - 70 })
    canvas.value?.addNodes(node)
    selectNode(node.id)
  }
  catch {
    toast.error('无法识别拖入的节点')
  }
}

function onConnect(connection: YdWorkflowConnection) {
  if (!connection.source || !connection.target || connection.source === connection.target) {
    return
  }
  canvas.value?.addEdges({
    ...makeEdge(connection.source, connection.target),
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
  })
}

function selectNode(nodeId: string) {
  rightMode.value = 'config'
  selectedNodeId.value = nodeId
  selectedEdgeId.value = ''
  revealInspector()
}

function selectEdge(edgeId: string) {
  rightMode.value = 'config'
  selectedEdgeId.value = edgeId
  selectedNodeId.value = ''
  revealInspector()
}

function clearSelection() {
  selectedNodeId.value = ''
  selectedEdgeId.value = ''
}

function updateSelectedNode(data: Partial<AgentNodeData>) {
  if (!selectedNodeId.value) {
    return
  }
  canvas.value?.updateNodeData(selectedNodeId.value, data)
}

function changeEdgeStyle(style: AgentConnectionStyle) {
  if (!selectedEdgeId.value) {
    return
  }
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  edges.value = edgeRecords.map((edge) => {
    if (edge.id !== selectedEdgeId.value) {
      return edge
    }
    const currentData = edge.data
    const data = currentData && typeof currentData === 'object' && !Array.isArray(currentData)
      ? { ...(currentData as Record<string, unknown>), connectionStyle: style }
      : { connectionStyle: style }
    return { ...edge, markerEnd: style === 'arrow' ? 'arrowclosed' : undefined, data }
  }) as unknown as YdWorkflowEdge<Record<string, unknown>>[]
}

function changeEdgeLabel(label: string) {
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  edges.value = edgeRecords.map(edge => edge.id === selectedEdgeId.value ? { ...edge, label } : edge) as unknown as YdWorkflowEdge<Record<string, unknown>>[]
}

function deleteNode(nodeId: string) {
  if (runtimeReadonly.value) {
    return
  }
  nodes.value = nodes.value.filter(node => node.id !== nodeId)
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  edges.value = edgeRecords.filter(edge => edge.source !== nodeId && edge.target !== nodeId) as unknown as YdWorkflowEdge<Record<string, unknown>>[]
  clearSelection()
}

function deleteEdge(edgeId: string) {
  if (runtimeReadonly.value) {
    return
  }
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  edges.value = edgeRecords.filter(edge => edge.id !== edgeId) as unknown as YdWorkflowEdge<Record<string, unknown>>[]
  clearSelection()
}

function deleteSelection() {
  if (runtimeReadonly.value) {
    return
  }
  if (selectedNodeId.value) {
    deleteNode(selectedNodeId.value)
  }
  else if (selectedEdgeId.value) {
    deleteEdge(selectedEdgeId.value)
  }
}

function onKeydown(event: KeyboardEvent) {
  if (runtimeReadonly.value) {
    return
  }
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
      nodes.value = migrated.nodes.map(node => normalizeNode(node as AgentFlowNode))
      edges.value = migrated.edges.map(edge => normalizeEdge(edge as YdWorkflowEdge<Record<string, unknown>>))
      clearSelection()
      toast.success(`已将 ${migratedCount} 个工具节点迁移到模型节点`)
    },
  })
}

function fitCanvas() {
  nextTick(() => {
    void canvas.value?.fitView({ padding: 0.22, duration: 260 })
  })
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
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  return {
    nodes: nodes.value.map(node => ({ id: node.id, type: 'agent', position: node.position, data: node.data })),
    edges: edgeRecords.map(item => ({
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

function resetDebugCanvas() {
  Object.keys(nodeDebugStatus).forEach(key => delete nodeDebugStatus[key])
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  edges.value = edgeRecords.map(edge => ({ ...edge, class: undefined })) as unknown as YdWorkflowEdge<Record<string, unknown>>[]
}

function refreshDebugEdges() {
  const edgeRecords = edges.value as unknown as Array<Record<string, unknown>>
  edges.value = edgeRecords.map((item) => {
    const sourceId = typeof item.source === 'string' ? item.source : ''
    const targetId = typeof item.target === 'string' ? item.target : ''
    const sourceStatus = nodeDebugStatus[sourceId]
    const targetStatus = nodeDebugStatus[targetId]
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
  }) as unknown as YdWorkflowEdge<Record<string, unknown>>[]
}

function focusDebugNode(nodeId: string) {
  const node = nodes.value.find(item => item.id === nodeId)
  if (!node) {
    return
  }
  void canvas.value?.setCenter(node.position.x + 115, node.position.y + 70, { zoom: 1, duration: 260 })
}

watch(() => debugTask.run.value?.activities, (activities) => {
  for (const key of Object.keys(nodeDebugStatus)) {
    delete nodeDebugStatus[key]
  }
  let activeNode = ''
  for (const raw of activities || []) {
    const activity = projectAiRunActivity(raw, debugTask.run.value?.id || '')
    if (!activity?.nodeId || !['RUNNING', 'COMPLETED', 'SKIPPED', 'FAILED'].includes(activity.nodeStatus || '')) {
      continue
    }
    nodeDebugStatus[activity.nodeId] = activity.nodeStatus as AgentDebugStatus
  }
  activeNode = Object.keys(nodeDebugStatus).find(nodeId => nodeDebugStatus[nodeId] === 'RUNNING') || ''
  refreshDebugEdges()
  if (activeNode) {
    focusDebugNode(activeNode)
  }
}, { deep: true })

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
  debugPreparing.value = true
  const started = aiRuntime.lifecycleVersion()
  try {
    const applicationId = await save(false, false)
    if (started !== aiRuntime.lifecycleVersion()) {
      return
    }
    if (!applicationId) {
      return
    }
    rightMode.value = 'debug'
    resetDebugCanvas()
    const debugInput = buildAgentRunInput(content, attachments)
    const imageDataUrl = attachments.find(item => item.type.startsWith('image/'))?.dataUrl
    aiRuntime.appendTaskInput(`agent-debug:${applicationId}`, debugInput)
    await debugTask.start(() => apiAgent.startDebugRun(applicationId, {
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
    }))
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : 'Agent 调试失败')
  }
  finally {
    debugPreparing.value = false
  }
}

function stopDebug() {
  if (debugPreparing.value) {
    toast.warning('调试任务仍在登记，请稍后再次停止')
    return
  }
  void debugTask.stop()
}

function clearDebug() {
  void debugTask.reconnect()
}

/* ------------------------------------------------------------ 工具模块分组与校验反馈 */

/** 工具选择项：模块目录（toolModules）是模块归属、开关状态与工具清单的唯一来源。 */
interface AgentToolPickerTool {
  code: string
  name: string
  description?: string
  permissionCode?: string
  risk?: 'READ' | 'WRITE'
  requiresConfirmation?: boolean
  /** 当前能否授权：系统工具总开关开启、模块开放且工具启用 */
  selectable: boolean
  reason?: string
}

interface AgentToolPickerGroup {
  code: string
  name: string
  description?: string
  source?: string
  /** 按需加载模块：模型需要先调用 load_tools 加载后才能看到其中的工具 */
  lazy: boolean
  closed: boolean
  closedReason?: string
  /** 常驻 / 按需加载 / 已关闭 */
  stateText: string
  tools: AgentToolPickerTool[]
}

interface AgentEditorIssue {
  key: string
  tone: 'error' | 'warning'
  message: string
  nodeId?: string
}

/**
 * MCP 模块在管理端目录里下发的工具编码是 mcp:<server>.<tool> 的展示形式，
 * 运行期可解析的工具名是 mcp.<server>.<tool>（后端 AiToolModules.mcpToolName）。
 * 授权必须保存运行期编码，否则运行时会解析不到工具。
 */
function runtimeToolCode(moduleCode: string, toolCode: string) {
  return moduleCode.startsWith('mcp:') ? toolCode.replace(/^mcp:/, 'mcp.') : toolCode
}

const toolPickerGroups = computed<AgentToolPickerGroup[]>(() => {
  const catalog = toolModuleCatalog.value
  const systemEnabled = catalog?.systemEnabled !== false
  const groups: AgentToolPickerGroup[] = []
  const indexed = new Set<string>()
  for (const module of catalog?.modules || []) {
    // 系统工具总开关只作用于系统 / 插件 / MCP 工具；Python 自定义工具由应用显式授权，不受总开关影响。
    const globalClosed = !systemEnabled && module.source !== 'python'
    const moduleClosed = globalClosed || module.enabled === false
    const tools = (module.tools || []).map((tool) => {
      const code = runtimeToolCode(module.code, tool.code)
      indexed.add(code)
      const reason = module.enabled === false
        ? '所属模块已关闭，暂不能授权'
        : globalClosed
          ? '系统工具总开关已关闭，暂不能授权'
          : tool.enabled === false
            ? '工具当前已停用，暂不能授权'
            : undefined
      return {
        code,
        name: tool.name || code,
        description: tool.description,
        permissionCode: tool.permissionCode,
        risk: tool.risk,
        requiresConfirmation: tool.requiresConfirmation,
        selectable: !reason,
        reason,
      }
    })
    if (!tools.length) {
      continue
    }
    groups.push({
      code: module.code,
      name: module.name || module.code,
      description: module.description,
      source: module.source,
      lazy: module.lazy === true,
      closed: moduleClosed,
      closedReason: moduleClosed ? (module.enabled === false ? '模块已关闭' : '系统工具总开关已关闭') : undefined,
      stateText: moduleClosed ? '已关闭' : module.lazy ? '按需加载' : '常驻',
      tools,
    })
  }
  // 兜底：候选列表里存在、但未出现在模块目录中的工具，仍允许授权（目录与候选来源不一致时）。
  const extra = toolCandidates.value.filter(candidate => candidate.code && !indexed.has(candidate.code))
  if (extra.length) {
    for (const candidate of extra) {
      indexed.add(candidate.code)
    }
    groups.push({
      code: 'unclassified',
      name: '其他可用工具',
      description: '未归入工具模块的候选工具，可直接授权调用。',
      lazy: false,
      closed: false,
      stateText: '常驻',
      tools: extra.map(candidate => ({
        code: candidate.code,
        name: candidate.name || candidate.code,
        description: candidate.description,
        permissionCode: candidate.permissionCode,
        selectable: true,
      })),
    })
  }
  return groups
})

const toolPickerIndex = computed(() => {
  const index = new Map<string, { group: AgentToolPickerGroup, tool: AgentToolPickerTool }>()
  for (const group of toolPickerGroups.value) {
    for (const tool of group.tools) {
      index.set(tool.code, { group, tool })
    }
  }
  return index
})

const knownToolCodes = computed(() => new Set([
  ...toolPickerIndex.value.keys(),
  ...customTools.value.map(tool => tool.code),
  ...systemTools.value.map(tool => tool.code),
]))

const workflowValidation = computed(() => validateAgentWorkflow(nodes.value, edges.value, {
  models: agentModels.value,
  knowledgeSpaceSlugs: new Set(knowledgeSpaces.value.map(space => space.slug)),
  toolCodes: knownToolCodes.value,
}))

/** 是否已经尝试过保存 / 发布校验：问题面板只在用户触发校验后展示阻断项。 */
const validationAttempted = ref(false)

const applicationIssues = computed<AgentEditorIssue[]>(() => {
  const issues: AgentEditorIssue[] = []
  if (!form.name.trim()) {
    issues.push({ key: 'app-name', tone: 'error', message: '应用名称不能为空，请在右侧「应用设置」中填写应用名称。' })
  }
  if (!form.code.trim()) {
    issues.push({ key: 'app-code', tone: 'error', message: '应用编码不能为空，请在右侧「应用设置」中填写应用编码。' })
  }
  else if (!/^[a-z][a-z0-9-]*$/i.test(form.code.trim())) {
    issues.push({ key: 'app-code-format', tone: 'error', message: '应用编码只能包含英文字母、数字和连字符，并以字母开头。' })
  }
  return issues
})

/** 工具授权提醒：模块关闭或工具已下线时不阻断保存，但必须显式提示运行时风险。 */
const toolWarnings = computed<AgentEditorIssue[]>(() => {
  const warnings: AgentEditorIssue[] = []
  if (!toolPickerGroups.value.length) {
    // 模块目录未加载时不臆测工具状态，避免把正常授权误报为失效。
    return warnings
  }
  const legacyCodes = new Set([...customTools.value.map(tool => tool.code), ...systemTools.value.map(tool => tool.code)])
  for (const node of nodes.value) {
    const codes = node.data.kind === 'tool'
      ? (node.data.toolCode ? [node.data.toolCode] : [])
      : (node.data.toolCodes || [])
    const nodeTitle = node.data.title || node.id
    for (const code of codes) {
      const entry = toolPickerIndex.value.get(code)
      if (!entry) {
        if (legacyCodes.has(code)) {
          continue
        }
        warnings.push({
          key: `${node.id}:${code}`,
          tone: 'warning',
          nodeId: node.id,
          message: `节点“${nodeTitle}”授权的工具“${code}”已不在候选列表中，运行时无法解析，请重新选择。`,
        })
        continue
      }
      if (!entry.tool.selectable) {
        warnings.push({
          key: `${node.id}:${code}`,
          tone: 'warning',
          nodeId: node.id,
          message: `节点“${nodeTitle}”授权的工具“${entry.tool.name}”当前不可用：${entry.tool.reason}。`,
        })
      }
    }
  }
  return warnings
})

const panelIssues = computed<AgentEditorIssue[]>(() => [
  ...(validationAttempted.value
    ? [
        ...applicationIssues.value,
        ...workflowValidation.value.issues.map((issue, index) => ({
          key: `workflow-${issue.nodeId || 'graph'}-${index}`,
          tone: 'error' as const,
          nodeId: issue.nodeId,
          message: issue.message,
        })),
      ]
    : []),
  ...toolWarnings.value,
])

const blockingIssueCount = computed(() => panelIssues.value.filter(issue => issue.tone === 'error').length)

const inspectorPane = ref<HTMLElement | null>(null)
const issuePanel = ref<HTMLElement | null>(null)
/** 三栏在窄屏会退化为堆叠布局，此时选中节点需要把检查器带回可视区。 */
const stackedLayout = useIsMobile('(max-width: 1180px)')

function revealInspector() {
  if (!stackedLayout.value) {
    return
  }
  void nextTick(() => inspectorPane.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
}

function focusNode(nodeId: string) {
  selectNode(nodeId)
  focusDebugNode(nodeId)
}

function focusIssue(issue: AgentEditorIssue) {
  if (issue.nodeId) {
    focusNode(issue.nodeId)
  }
}

function scrollToIssues() {
  void nextTick(() => issuePanel.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }))
}

function dismissIssues() {
  validationAttempted.value = false
}

function openDebugPanel() {
  rightMode.value = 'debug'
  revealInspector()
}

async function save(publish = false, notify = true): Promise<string> {
  if (!form.name.trim() || !form.code.trim()) {
    validationAttempted.value = true
    toast.error('请在右侧应用设置中填写应用名称和编码')
    clearSelection()
    scrollToIssues()
    return ''
  }
  if (!/^[a-z][a-z0-9-]*$/i.test(form.code.trim())) {
    validationAttempted.value = true
    toast.error('应用编码只能包含英文字母、数字和连字符，并以字母开头')
    clearSelection()
    scrollToIssues()
    return ''
  }
  if (!nodes.value.some(node => node.data.kind === 'start') || !nodes.value.some(node => node.data.kind === 'end')) {
    validationAttempted.value = true
    toast.error('工作流至少需要一个开始节点和一个结束节点')
    scrollToIssues()
    return ''
  }
  const emptyToolNode = nodes.value.find(node => node.data.kind === 'tool' && !node.data.toolCode)
  if (emptyToolNode) {
    validationAttempted.value = true
    focusNode(emptyToolNode.id)
    toast.error('请为工具调用节点选择具体工具')
    scrollToIssues()
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
  if (workflowValidation.value.valid) {
    validationAttempted.value = false
    return true
  }
  validationAttempted.value = true
  const issue = workflowValidation.value.issues[0]!
  if (issue.nodeId) {
    focusNode(issue.nodeId)
  }
  toast.error(issue.message)
  scrollToIssues()
  return false
}
</script>

<template>
  <YdLoadingOverlay :loading="loading">
    <div class="agent-editor-page">
      <YdPageHeader :title="runtimeReadonly ? '查看插件 Agent 编排' : id ? '编辑 Agent 应用' : '新建 Agent 应用'" class="editor-page-header mb-0">
        <div class="header-actions">
          <YdButton variant="outline" @click="router.push('/platform/agent')">
            <YdIcon name="morph:arrow-left" /> 返回
          </YdButton>
          <YdButton variant="outline" title="导出工作流 JSON" @click="exportWorkflow">
            <YdIcon name="morph:download" /> 导出
          </YdButton>
          <YdButton v-if="runtimeReadonly" :loading="saving" @click="importRuntimeApplication">
            <YdIcon name="morph:cloud-download" /> 导入并编辑
          </YdButton>
          <YdButton v-if="!runtimeReadonly" :variant="rightMode === 'debug' ? 'default' : 'outline'" @click="openDebugPanel">
            <YdIcon name="morph:bug" /> 调试
          </YdButton>
          <YdButton v-if="!runtimeReadonly" variant="outline" :loading="saving" @click="save(false)">
            <YdIcon name="morph:save" /> 保存
          </YdButton>
          <YdButton v-if="!runtimeReadonly" :loading="saving" @click="save(true)">
            <YdIcon name="morph:send" /> 发布
          </YdButton>
        </div>
      </YdPageHeader>

      <section
        v-if="panelIssues.length"
        ref="issuePanel"
        class="issue-panel"
        :class="{ 'is-warning-only': !blockingIssueCount }"
        aria-live="polite"
      >
        <header class="issue-panel__head">
          <span class="issue-panel__icon">
            <YdIcon :name="blockingIssueCount ? 'morph:circle-alert' : 'morph:triangle-alert'" />
          </span>
          <div class="issue-panel__copy">
            <strong>{{ blockingIssueCount ? `保存前需处理 ${blockingIssueCount} 项问题` : '工具授权提醒' }}</strong>
            <small v-if="blockingIssueCount && toolWarnings.length">
              另有 {{ toolWarnings.length }} 项工具授权提醒；提醒不阻断保存，但相关工具在运行时可能不可用。
            </small>
            <small v-else-if="!blockingIssueCount">下列工具授权在运行时可能不可用，请确认后再发布。</small>
          </div>
          <YdButton variant="ghost" size="sm" @click="dismissIssues">
            关闭
          </YdButton>
        </header>
        <ul class="issue-panel__list">
          <li v-for="issue in panelIssues" :key="issue.key">
            <YdTag :variant="issue.tone === 'error' ? 'destructive' : 'outline'">
              {{ issue.tone === 'error' ? '问题' : '提醒' }}
            </YdTag>
            <span class="issue-panel__message">{{ issue.message }}</span>
            <YdButton v-if="issue.nodeId" variant="outline" size="sm" @click="focusIssue(issue)">
              定位节点
            </YdButton>
          </li>
        </ul>
      </section>

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
              <YdButton type="button" variant="ghost" :aria-pressed="defaultConnectionStyle === 'arrow'" :class="{ active: defaultConnectionStyle === 'arrow' }" title="箭头连线" @click="defaultConnectionStyle = 'arrow'">
                <YdIcon name="morph:arrow-right" /> 箭头
              </YdButton>
              <YdButton type="button" variant="ghost" :aria-pressed="defaultConnectionStyle === 'line'" :class="{ active: defaultConnectionStyle === 'line' }" title="普通线段" @click="defaultConnectionStyle = 'line'">
                <YdIcon name="morph:minus" /> 线段
              </YdButton>
            </div>
            <i v-if="!runtimeReadonly" />
            <YdButton type="button" variant="ghost" size="icon-sm" class="toolbar-icon" title="适应画布" aria-label="适应画布" @click="fitCanvas">
              <YdIcon name="morph:maximize" />
            </YdButton>
            <YdButton v-if="!runtimeReadonly" type="button" variant="ghost" size="icon-sm" class="toolbar-icon" title="重置基础流程" aria-label="重置基础流程" @click="resetWorkflow">
              <YdIcon name="morph:rotate-cw" />
            </YdButton>
            <YdButton v-if="!runtimeReadonly && legacyToolNodeCount" type="button" variant="ghost" size="icon-sm" class="toolbar-icon" title="迁移兼容工具节点" aria-label="迁移兼容工具节点" @click="migrateLegacyTools">
              <YdIcon name="morph:git-merge" />
            </YdButton>
            <YdButton v-if="!runtimeReadonly" type="button" variant="ghost" size="icon-sm" class="toolbar-icon danger" :disabled="!hasSelection" title="删除选中项" aria-label="删除选中项" @click="deleteSelection">
              <YdIcon name="morph:trash-2" />
            </YdButton>
          </div>

          <div v-if="isDragOver" class="drop-overlay">
            <YdIcon name="i-ri:drag-drop-line" />
            <strong>释放以添加节点</strong>
          </div>

          <YdWorkflowCanvas
            ref="canvas"
            v-model:nodes="nodes"
            v-model:edges="edges"
            class="agent-flow"
            :readonly="runtimeReadonly"
            :snap-to-grid="true"
            :snap-grid="[16, 16]"
            :min-zoom="0.25"
            :max-zoom="1.8"
            :delete-key-code="null"
            @connect="onConnect"
            @node-click="selectNode($event.id)"
            @edge-click="selectEdge($event.id)"
            @pane-click="clearSelection"
            @init="fitCanvas"
          >
            <template #node-agent="nodeProps">
              <AgentWorkflowNode v-bind="nodeProps" :debug-status="nodeDebugStatus[nodeProps.id]" :readonly="runtimeReadonly" @delete="deleteNode" />
            </template>
          </YdWorkflowCanvas>

          <div class="canvas-status">
            <span>{{ nodes.length }} 个节点</span>
            <i />
            <span>{{ edges.length }} 条连线</span>
            <span>{{ debugRunning ? '正在实时调试' : '拖动节点两侧圆点建立连接' }}</span>
          </div>
        </main>

        <div ref="inspectorPane" class="inspector-panel">
          <AgentDebugPanel
            v-if="rightMode === 'debug'"
            :messages="debugMessages"
            :harness="debugHarness"
            :run-id="debugTask.run.value?.id"
            :failure="debugTask.failure.value"
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
            :node="selectedNode"
            :system-tools="systemTools"
            :custom-tools="customTools"
            :tool-groups="toolPickerGroups"
            :models="agentModels"
            :knowledge-spaces="knowledgeSpaces"
            @update="updateSelectedNode"
            @delete="deleteNode(selectedNode.id)"
            @close="clearSelection"
          />
          <AgentEdgeInspector
            v-else-if="selectedEdge"
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
            :form="form"
            :tool-codes="applicationToolCodes"
            :system-tools="systemTools"
            :custom-tools="customTools"
            :tool-groups="toolPickerGroups"
            :code-disabled="Boolean(id)"
            @update-field="updateApplicationField"
          />
        </div>
      </section>
    </div>
  </YdLoadingOverlay>
</template>

<style scoped>
.agent-editor-page { min-width: 0; }
.header-actions { display: flex; min-width: 0; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.issue-panel { display: grid; gap: 8px; margin-bottom: 12px; padding: 12px 14px; border: 1px solid var(--color-border-2); border-left: 3px solid rgb(var(--danger-6)); border-radius: 7px; background: var(--color-bg-1); }
.issue-panel.is-warning-only { border-left-color: rgb(var(--warning-6)); }
.issue-panel__head { display: flex; align-items: flex-start; gap: 9px; }
.issue-panel__icon { flex: none; margin-top: 1px; color: rgb(var(--danger-6)); font-size: 16px; }
.issue-panel.is-warning-only .issue-panel__icon { color: rgb(var(--warning-6)); }
.issue-panel__copy { display: grid; min-width: 0; gap: 3px; flex: 1; }
.issue-panel__copy strong { color: var(--color-text-1); font-size: 12px; }
.issue-panel__copy small { color: var(--color-text-3); font-size: 10px; line-height: 1.55; }
.issue-panel__list { display: grid; max-height: 220px; gap: 6px; margin: 0; padding: 0; overflow: auto; list-style: none; }
.issue-panel__list li { display: grid; grid-template-columns: max-content minmax(0, 1fr) max-content; align-items: center; gap: 8px; }
.issue-panel__message { color: var(--color-text-2); font-size: 11px; line-height: 1.55; word-break: break-word; }
.agent-editor { display: grid; height: calc(100vh - 174px); min-height: 650px; overflow: hidden; grid-template-columns: 242px minmax(0, 1fr) 360px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.agent-editor.is-runtime-readonly { grid-template-columns: minmax(0, 1fr) 360px; }
.inspector-panel { display: flex; min-width: 0; min-height: 0; overflow: hidden; }
.agent-editor.is-runtime-readonly .inspector-panel { opacity: 0.86; pointer-events: none; }
.flow-canvas { position: relative; min-width: 0; overflow: hidden; background: var(--color-bg-2); }
.agent-flow { width: 100%; height: 100%; }
.canvas-toolbar { position: absolute; top: 14px; left: 50%; z-index: 10; display: flex; min-height: 40px; align-items: center; gap: 8px; padding: 5px 7px 5px 12px; border: 1px solid var(--color-border-2); border-radius: 7px; background: color-mix(in srgb, var(--color-bg-1), transparent 4%); box-shadow: 0 7px 24px rgb(15 23 42 / 12%); transform: translateX(-50%); backdrop-filter: blur(8px); }
.canvas-toolbar > span { color: var(--color-text-3); font-size: 10px; font-weight: 600; white-space: nowrap; }
.canvas-toolbar > i { width: 1px; height: 24px; background: var(--color-border-2); }
.connection-switch { display: flex; gap: 2px; padding: 3px; border-radius: 6px; background: var(--color-fill-2); }
.connection-switch button { display: flex; height: 26px; align-items: center; gap: 4px; padding: 0 8px; border: 0; border-radius: 4px; color: var(--color-text-3); background: transparent; font-size: 10px; cursor: pointer; }
.connection-switch button.active { color: var(--color-text-1); background: var(--color-bg-1); box-shadow: 0 2px 6px rgb(15 23 42 / 9%); }
.toolbar-icon { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 5px; color: var(--color-text-2); background: transparent; cursor: pointer; }
.toolbar-icon:hover { background: var(--color-fill-2); }
.toolbar-icon.danger { color: rgb(var(--danger-6)); }
.toolbar-icon:disabled { color: var(--color-text-4); cursor: not-allowed; }
.drop-overlay { position: absolute; z-index: 20; display: grid; inset: 10px; place-items: center; align-content: center; gap: 9px; border: 2px dashed var(--color-text-2); border-radius: 8px; color: var(--color-text-1); background: color-mix(in srgb, var(--color-fill-2) 86%, transparent); pointer-events: none; }
.drop-overlay > :first-child { font-size: 30px; }
.drop-overlay strong { font-size: 13px; }
.canvas-status { position: absolute; bottom: 15px; left: 76px; z-index: 8; display: flex; min-height: 28px; align-items: center; gap: 8px; padding: 0 9px; border: 1px solid var(--color-border-2); border-radius: 6px; color: var(--color-text-3); background: color-mix(in srgb, var(--color-bg-1), transparent 4%); box-shadow: 0 3px 12px rgb(15 23 42 / 7%); font-size: 9px; }
.canvas-status i { width: 1px; height: 12px; background: var(--color-border-2); }
.canvas-status span:last-child { margin-left: 3px; }
.agent-flow :deep(.vue-flow__edge-path) { stroke: var(--color-text-4); stroke-width: 1.6; }
.agent-flow :deep(.vue-flow__edge.selected .vue-flow__edge-path), .agent-flow :deep(.vue-flow__edge:hover .vue-flow__edge-path) { stroke: var(--color-text-1); stroke-width: 2.2; }
.agent-flow :deep(.vue-flow__edge.debug-active .vue-flow__edge-path) { stroke: var(--color-text-1); stroke-width: 2.6; stroke-dasharray: 8 5; animation: edge-flow 0.8s linear infinite; }
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
  /* 窄屏退化为两段堆叠：画布在上、检查器在下，两段各自内部滚动，检查器始终可达。 */
  .agent-editor { height: auto; min-height: 0; grid-template-columns: 220px minmax(0, 1fr); grid-template-rows: minmax(420px, 58vh) minmax(300px, 52vh); }
  .agent-editor.is-runtime-readonly { grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(320px, 58vh) auto; }
  .inspector-panel { grid-column: 1 / -1; border-top: 1px solid var(--color-border-2); }
  .inspector-panel :deep(.inspector), .inspector-panel :deep(.debug-panel) { border-left: 0; }
  /* 只读编排面板不接收交互，改为按内容展开、由页面滚动承载，保证内容可完整阅读。 */
  .agent-editor.is-runtime-readonly .inspector-panel { overflow: visible; }
  .agent-editor.is-runtime-readonly .inspector-panel :deep(.inspector) { height: auto; }
  .agent-editor.is-runtime-readonly .inspector-panel :deep(.inspector-body) { overflow: visible; }
  .issue-panel__list li { grid-template-columns: max-content minmax(0, 1fr); }
}
@media (max-width: 760px) {
  .header-actions { justify-content: flex-start; }
  .agent-editor { display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(220px, 38vh) minmax(320px, 58vh) minmax(320px, 62vh); }
  .agent-editor.is-runtime-readonly { grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(320px, 58vh) auto; }
  .palette-panel, .flow-canvas, .inspector-panel { grid-column: 1; }
  .palette-panel { border-right: 0 !important; border-bottom: 1px solid var(--color-border-2); }
  .canvas-toolbar { left: 10px; max-width: calc(100% - 20px); transform: none; }
  .canvas-toolbar > span { display: none; }
  .canvas-status span:last-child { display: none; }
  .agent-flow :deep(.vue-flow__minimap) { width: 130px; height: 90px; }
  .issue-panel__list li { grid-template-columns: minmax(0, 1fr); }
}
</style>
