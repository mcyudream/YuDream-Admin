<script setup lang="ts">
import type { Edge, Node } from '@vue-flow/core'
import type { AgentApplicationPayload, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MarkerType, VueFlow, useVueFlow } from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import apiAgent from '@/api/modules/platform-agent'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

const route = useRoute()
const router = useRouter()
const toast = useFaToast()
const id = computed(() => typeof route.query.id === 'string' ? route.query.id : '')
const saving = ref(false)
const loading = ref(false)
const selectedId = ref('')
const customTools = ref<AgentTool[]>([])
const systemTools = ref<SystemAgentTool[]>([])
const form = reactive<AgentApplicationPayload>({ name: '', code: '', description: '', icon: 'i-ri:robot-2-line', systemPrompt: '', workflowJson: '', toolCodes: [], status: 'DRAFT' })

const nodeKinds = [
  { kind: 'start', label: '开始', icon: 'i-ri:play-circle-line', color: '#2563eb', description: '接收用户输入' },
  { kind: 'llm', label: '大模型', icon: 'i-ri:sparkling-line', color: '#7c3aed', description: '调用模型处理上下文' },
  { kind: 'tool', label: '工具', icon: 'i-ri:tools-line', color: '#d97706', description: '调用已选择的 Python 工具' },
  { kind: 'condition', label: '条件', icon: 'i-ri:git-branch-line', color: '#0f766e', description: '按规则分支' },
  { kind: 'end', label: '结束', icon: 'i-ri:stop-circle-line', color: '#475569', description: '输出结果' },
]
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const { addNodes, addEdges, screenToFlowCoordinate, fitView, updateNodeData } = useVueFlow()
const selectedNode = computed(() => nodes.value.find(node => node.id === selectedId.value))

onMounted(async () => {
  await Promise.all([loadTools(), loadApplication()])
  if (!nodes.value.length) seedWorkflow()
  nextTick(() => fitView({ padding: 0.2 }))
})

async function loadTools() {
  const [result, system] = await Promise.all([apiAgent.pageTools({ page: 1, size: 200 }), apiAgent.systemTools()])
  customTools.value = result.data.records.filter(item => item.enabled)
  systemTools.value = system.data
}
async function loadApplication() {
  if (!id.value) return
  loading.value = true
  try {
    const application = (await apiAgent.detail(id.value)).data
    Object.assign(form, application)
    const flow = JSON.parse(application.workflowJson)
    nodes.value = Array.isArray(flow.nodes) ? flow.nodes : []
    edges.value = Array.isArray(flow.edges) ? flow.edges : []
  }
  catch { toast.error('加载 Agent 应用失败') }
  finally { loading.value = false }
}
function seedWorkflow() {
  nodes.value = [
    makeNode('start', { x: 80, y: 240 }),
    makeNode('llm', { x: 390, y: 240 }),
    makeNode('end', { x: 700, y: 240 }),
  ]
  edges.value = [edge('start', 'llm'), edge('llm', 'end')]
}
function makeNode(kind: string, position: { x: number; y: number }): Node {
  const config = nodeKinds.find(item => item.kind === kind) || nodeKinds[0]
  const node: Node = { id: `${kind}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`, type: 'agent', position, data: { ...config, title: config.label, prompt: '', toolCode: '' } }
  return node
}
function edge(source: string, target: string): Edge { return { id: `edge-${source}-${target}-${Date.now()}`, source, target, type: 'smoothstep', markerEnd: MarkerType.ArrowClosed } }
function addNode(kind: string) {
  const offset = nodes.value.length * 32
  const node = makeNode(kind, { x: 260 + offset, y: 180 + offset })
  addNodes(node); selectedId.value = node.id
}
function onDrop(event: DragEvent) {
  const kind = event.dataTransfer?.getData('application/agent-node')
  if (!kind) return
  const point = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  const node = makeNode(kind, { x: point.x - 110, y: point.y - 42 })
  addNodes(node); selectedId.value = node.id
}
function onConnect(connection: Edge) { addEdges({ ...connection, id: `edge-${Date.now()}`, type: 'smoothstep', markerEnd: MarkerType.ArrowClosed }) }
function dragStart(event: DragEvent, kind: string) { event.dataTransfer?.setData('application/agent-node', kind); if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move' }
function updateSelected(key: string, value: unknown) { if (!selectedId.value) return; updateNodeData(selectedId.value, { [key]: value }) }
function toggleTool(code: string, enabled: boolean) { form.toolCodes = enabled ? [...new Set([...form.toolCodes, code])] : form.toolCodes.filter(item => item !== code) }
function deleteSelected() { if (!selectedId.value) return; nodes.value = nodes.value.filter(node => node.id !== selectedId.value); edges.value = edges.value.filter(edge => edge.source !== selectedId.value && edge.target !== selectedId.value); selectedId.value = '' }
function workflowJson() { return JSON.stringify({ nodes: nodes.value, edges: edges.value }) }
async function save(publish = false) {
  if (!form.name.trim() || !form.code.trim()) { toast.error('请填写应用名称和编码'); return }
  if (!nodes.value.some(node => node.data.kind === 'start') || !nodes.value.some(node => node.data.kind === 'end')) { toast.error('工作流需要开始和结束节点'); return }
  saving.value = true
  try {
    form.workflowJson = workflowJson(); form.status = publish ? 'PUBLISHED' : form.status
    const payload = { ...form }
    const response = id.value ? await apiAgent.update(id.value, payload) : await apiAgent.create(payload)
    if (publish) await apiAgent.publish(response.data.id)
    toast.success(publish ? '应用已发布' : '编排已保存')
    router.replace({ path: '/platform/agent/editor', query: { id: response.data.id } })
  }
  finally { saving.value = false }
}
</script>

<template>
  <div class="agent-editor-page" v-loading="loading">
    <FaPageHeader :title="id ? '编辑 Agent 应用' : '新建 Agent 应用'" class="mb-0">
      <div class="flex gap-2"><FaButton variant="outline" @click="router.push('/platform/agent')">返回</FaButton><FaButton :loading="saving" variant="outline" @click="save(false)">保存</FaButton><FaButton :loading="saving" @click="save(true)">发布</FaButton></div>
    </FaPageHeader>
    <section class="agent-editor">
      <aside class="node-palette"><strong>节点</strong><button v-for="item in nodeKinds" :key="item.kind" type="button" draggable="true" class="palette-node" @dragstart="dragStart($event, item.kind)" @click="addNode(item.kind)"><FaIcon :name="item.icon" :style="{ color: item.color }" /><span><b>{{ item.label }}</b><small>{{ item.description }}</small></span></button></aside>
      <main class="flow-canvas" @dragover.prevent @drop.prevent="onDrop">
        <VueFlow v-model:nodes="nodes" v-model:edges="edges" :snap-to-grid="true" :snap-grid="[16, 16]" :min-zoom="0.2" @connect="onConnect" @node-click="({ node }) => selectedId = node.id" @pane-click="selectedId = ''">
          <Background pattern-color="#cbd5e1" :gap="16" /><Controls position="bottom-left" /><MiniMap position="bottom-right" />
          <template #node-agent="nodeProps"><div class="agent-node" :class="{ selected: nodeProps.selected }" :style="{ '--node-color': nodeProps.data.color }"><div><FaIcon :name="nodeProps.data.icon" /><strong>{{ nodeProps.data.title }}</strong></div><small>{{ nodeProps.data.description }}</small><em v-if="nodeProps.data.kind === 'tool'">{{ nodeProps.data.toolCode || '未选择工具' }}</em></div></template>
        </VueFlow>
      </main>
      <aside class="inspector">
        <template v-if="selectedNode"><h3>节点配置</h3><FaInput :model-value="selectedNode.data.title" label="名称" @update:model-value="updateSelected('title', $event)" /><FaTextarea :model-value="selectedNode.data.prompt" :autosize="{ minRows: 4 }" label="指令" @update:model-value="updateSelected('prompt', $event)" /><FaSelect v-if="selectedNode.data.kind === 'tool'" :model-value="selectedNode.data.toolCode" label="Python 工具" :options="customTools.map(tool => ({ label: `${tool.name} (${tool.code})`, value: tool.code }))" @update:model-value="updateSelected('toolCode', $event)" /><FaButton variant="destructive" size="sm" @click="deleteSelected">删除节点</FaButton></template>
        <template v-else><h3>应用配置</h3><FaInput v-model="form.name" label="名称" placeholder="例如：运营内容助手" /><FaInput v-model="form.code" label="编码" placeholder="content-agent" /><FaInput v-model="form.description" label="描述" /><FaTextarea v-model="form.systemPrompt" :autosize="{ minRows: 6 }" label="系统提示词" placeholder="定义该 Agent 应用的角色、边界和输出要求" /><div class="tool-checks"><span>允许使用的系统工具</span><label v-for="tool in systemTools" :key="tool.code"><input type="checkbox" :checked="form.toolCodes.includes(tool.code)" @change="toggleTool(tool.code, ($event.target as HTMLInputElement).checked)"> {{ tool.name }}</label></div><div class="tool-checks"><span>允许使用的 Python 工具</span><label v-for="tool in customTools" :key="tool.code"><input type="checkbox" :checked="form.toolCodes.includes(tool.code)" @change="toggleTool(tool.code, ($event.target as HTMLInputElement).checked)"> {{ tool.name }}</label></div></template>
      </aside>
    </section>
  </div>
</template>

<style scoped>
.agent-editor { display: grid; height: calc(100vh - 174px); min-height: 620px; grid-template-columns: 230px minmax(0, 1fr) 310px; border-top: 1px solid var(--color-border-2); }.node-palette, .inspector { display: grid; align-content: start; gap: 12px; padding: 16px; overflow: auto; background: var(--color-bg-1); }.node-palette { border-right: 1px solid var(--color-border-2); }.inspector { border-left: 1px solid var(--color-border-2); }.palette-node { display: flex; gap: 10px; align-items: flex-start; width: 100%; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; background: transparent; text-align: left; cursor: grab; }.palette-node span { display: grid; gap: 3px; }.palette-node small { color: var(--color-text-3); font-size: 11px; }.flow-canvas { min-width: 0; background: var(--color-bg-2); }.agent-node { display: grid; gap: 8px; width: 210px; padding: 12px; border: 2px solid var(--node-color); border-radius: 6px; background: var(--color-bg-1); box-shadow: 0 3px 12px rgb(15 23 42 / 8%); }.agent-node.selected { box-shadow: 0 0 0 3px color-mix(in srgb, var(--node-color), transparent 75%); }.agent-node div { display: flex; gap: 8px; align-items: center; }.agent-node small, .agent-node em { color: var(--color-text-3); font-size: 12px; font-style: normal; }.agent-node em { color: var(--node-color); }.tool-checks { display: grid; gap: 8px; color: var(--color-text-2); font-size: 13px; }.tool-checks label { display: flex; gap: 8px; align-items: center; }@media (max-width: 1100px) { .agent-editor { grid-template-columns: 190px minmax(0, 1fr); }.inspector { grid-column: 1 / -1; max-height: 260px; border-top: 1px solid var(--color-border-2); border-left: 0; } }
</style>
