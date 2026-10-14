<script setup lang="ts">
import type { YdWorkflowCanvasInstance, YdWorkflowCanvasNodeSlot, YdWorkflowConnection, YdWorkflowEdge, YdWorkflowNode } from '@yudream/components'
import { YdWorkflowCanvas, YdWorkflowHandle } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

interface DemoData {
  title: string
  description: string
}

const canvas = ref<YdWorkflowCanvasInstance<DemoData, Record<string, unknown>> | null>(null)
const nodes = ref<YdWorkflowNode<DemoData>[]>([
  { id: 'start', type: 'step', position: { x: 80, y: 140 }, data: { title: '开始', description: '受控节点数据' } },
  { id: 'work', type: 'step', position: { x: 370, y: 140 }, data: { title: '处理', description: '拖拽建立连接' } },
  { id: 'finish', type: 'step', position: { x: 660, y: 140 }, data: { title: '结束', description: '由父组件维护模型' } },
])
const edges = ref<YdWorkflowEdge<Record<string, unknown>>[]>([
  { id: 'start-work', source: 'start', target: 'work', type: 'smoothstep' },
  { id: 'work-finish', source: 'work', target: 'finish', type: 'smoothstep' },
])
const lastEvent = ref('等待画布操作')
const readonly = ref(false)
const disabled = ref(false)

function onConnect(connection: YdWorkflowConnection) {
  if (!connection.source || !connection.target || connection.source === connection.target) {
    return
  }
  edges.value = [...edges.value, {
    id: `${connection.source}-${connection.target}-${Date.now()}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
    type: 'smoothstep',
  }]
  lastEvent.value = `连接 ${connection.source} → ${connection.target}`
}

function renderNode(slot: YdWorkflowCanvasNodeSlot<DemoData>) {
  return slot.data.title
}

function addNode() {
  const id = `node-${nodes.value.length + 1}`
  canvas.value?.addNodes({ id, type: 'step', position: { x: 180, y: 320 }, data: { title: `节点 ${nodes.value.length + 1}`, description: '由 YD ref API 添加' } })
}

function fitView() {
  void canvas.value?.fitView({ padding: 0.2, duration: 180 })
}
</script>

<template>
  <DemoCard title="工作流画布" description="通过受控节点/边、类型化连接事件和 ref facade 操作流程；节点插槽使用 YD 规范的可连接点组件。">
    <div class="workflow-demo-toolbar">
      <YdButton type="button" variant="outline" @click="addNode">
        增加节点
      </YdButton>
      <YdButton type="button" variant="outline" @click="fitView">
        适应画布
      </YdButton>
      <YdButton variant="outline" :aria-pressed="readonly" @click="readonly = !readonly">
        {{ readonly ? '恢复编辑' : '只读画布' }}
      </YdButton>
      <YdButton variant="outline" :aria-pressed="disabled" @click="disabled = !disabled">
        {{ disabled ? '启用画布' : '禁用画布' }}
      </YdButton>
      <span>{{ lastEvent }}</span>
    </div>
    <div class="workflow-demo-canvas">
      <YdWorkflowCanvas
        ref="canvas"
        v-model:nodes="nodes"
        v-model:edges="edges"
        :snap-to-grid="true"
        :snap-grid="[16, 16]"
        :readonly="readonly"
        :disabled="disabled"
        delete-key-code="Delete"
        aria-label="YD 工作流画布示例"
        @connect="onConnect"
        @init="lastEvent = '画布初始化完成'"
        @node-click="lastEvent = `选中节点 ${$event.id}`"
        @edge-click="lastEvent = `选中连线 ${$event.id}`"
        @pane-click="lastEvent = '画布空白区域'"
      >
        <template #node-step="node">
          <div class="workflow-demo-node" :class="{ 'workflow-demo-node--selected': node.selected }">
            <YdWorkflowHandle v-if="node.id !== 'start'" type="target" position="left" :connectable="node.connectable" />
            <strong>{{ renderNode(node) }}</strong>
            <small>{{ node.data.description }}</small>
            <YdWorkflowHandle v-if="node.id !== 'finish'" type="source" position="right" :connectable="node.connectable" />
          </div>
        </template>
      </YdWorkflowCanvas>
    </div>
    <p class="workflow-demo-status" role="status">
      {{ nodes.length }} 个节点 · {{ edges.length }} 条连线 · {{ lastEvent }}
    </p>
  </DemoCard>
</template>

<style scoped>
.workflow-demo-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}

.workflow-demo-toolbar span {
  font-size: 12px;
  color: var(--color-text-3);
}

.workflow-demo-canvas {
  height: 440px;
  min-height: 260px;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.workflow-demo-node {
  position: relative;
  display: grid;
  gap: 5px;
  width: 190px;
  padding: 14px;
  color: var(--color-text-1);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  box-shadow: 0 4px 14px rgb(15 23 42 / 10%);
}

.workflow-demo-node--selected {
  border-color: var(--color-border-3);
  box-shadow: 0 0 0 3px var(--color-fill-2);
}

.workflow-demo-node small {
  color: var(--color-text-3);
}

.workflow-demo-status {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
