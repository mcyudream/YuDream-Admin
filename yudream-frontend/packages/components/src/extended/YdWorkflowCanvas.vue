<script setup lang="ts" generic="TNodeData = Record<string, unknown>, TEdgeData = Record<string, unknown>">
import type { Connection, Edge, Node, NodeProps } from '@vue-flow/core'
import type {
  YdWorkflowBackgroundOptions,
  YdWorkflowCanvasInstance,
  YdWorkflowCanvasNodeSlot,
  YdWorkflowCenterOptions,
  YdWorkflowClickEdge,
  YdWorkflowClickNode,
  YdWorkflowConnection,
  YdWorkflowControlsOptions,
  YdWorkflowEdge,
  YdWorkflowMiniMapOptions,
  YdWorkflowNode,
  YdWorkflowPoint,
  YdWorkflowViewportOptions,
} from './workflow-canvas-model'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { useVueFlow, VueFlow } from '@vue-flow/core'
import { MiniMap } from '@vue-flow/minimap'
import { computed, onBeforeUnmount, ref, useId, useSlots } from 'vue'
import { canEditYdWorkflowCanvas, getYdWorkflowNodeSlotNames, normalizeYdWorkflowConnection, resolveYdWorkflowDeleteKeyCode } from './workflow-canvas-model'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import './extended.css'

defineOptions({ name: 'YdWorkflowCanvas', inheritAttrs: false })

const props = withDefaults(defineProps<{
  id?: string
  disabled?: boolean
  readonly?: boolean
  ariaLabel?: string
  theme?: 'inherit' | 'light' | 'dark'
  showBackground?: boolean
  backgroundOptions?: YdWorkflowBackgroundOptions
  showControls?: boolean
  controlsOptions?: YdWorkflowControlsOptions
  showMiniMap?: boolean
  miniMapOptions?: YdWorkflowMiniMapOptions
  snapToGrid?: boolean
  snapGrid?: [number, number]
  minZoom?: number
  maxZoom?: number
  fitViewOnInit?: boolean
  deleteKeyCode?: string | null
}>(), {
  disabled: false,
  readonly: false,
  ariaLabel: '工作流画布',
  theme: 'inherit',
  showBackground: true,
  backgroundOptions: () => ({ patternColor: 'var(--color-border-3)', gap: 18, size: 1 }),
  showControls: true,
  controlsOptions: () => ({ position: 'bottom-left' }),
  showMiniMap: true,
  miniMapOptions: () => ({ position: 'bottom-right', pannable: true, zoomable: true }),
  snapToGrid: false,
  snapGrid: () => [16, 16],
  minZoom: 0.25,
  maxZoom: 1.8,
  fitViewOnInit: false,
  deleteKeyCode: null,
})

const emit = defineEmits<{
  connect: [connection: YdWorkflowConnection]
  nodeClick: [node: YdWorkflowClickNode]
  edgeClick: [edge: YdWorkflowClickEdge]
  paneClick: []
  init: []
}>()

defineSlots<{
  default?: () => unknown
  [slotName: `node-${string}`]: (props: YdWorkflowCanvasNodeSlot<TNodeData>) => unknown
}>()

const nodes = defineModel<YdWorkflowNode<TNodeData>[]>('nodes', { required: true })
const edges = defineModel<YdWorkflowEdge<TEdgeData>[]>('edges', { required: true })
const slots = useSlots()
function getNodeSlotNames() {
  // Read slot keys during each render so conditionally supplied node renderers can appear/disappear.
  return getYdWorkflowNodeSlotNames(Object.keys(slots))
}
const generatedFlowId = useId()
const flowId = props.id || generatedFlowId
const ready = ref(false)
let disposed = false
const flow = useVueFlow(flowId)
const flowNodes = computed({
  get: () => nodes.value as unknown as Node[],
  set: (value: Node[]) => { nodes.value = value as unknown as YdWorkflowNode<TNodeData>[] },
})
const flowEdges = computed({
  get: () => edges.value as unknown as Edge[],
  set: (value: Edge[]) => { edges.value = value as unknown as YdWorkflowEdge<TEdgeData>[] },
})
const canEdit = computed(() => canEditYdWorkflowCanvas(props))

function normalizeConnection(connection: Connection): YdWorkflowConnection {
  return normalizeYdWorkflowConnection({
    source: connection.source ?? null,
    target: connection.target ?? null,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
  })
}

function onConnect(connection: Connection) {
  if (!canEdit.value) {
    return
  }
  emit('connect', normalizeConnection(connection))
}

function onNodeClick(event: { node: Node }) {
  emit('nodeClick', { id: event.node.id })
}

function onEdgeClick(event: { edge: Edge }) {
  emit('edgeClick', { id: event.edge.id })
}

function nodeSlotProps(value: Pick<NodeProps, 'id' | 'data' | 'selected' | 'connectable'>): YdWorkflowCanvasNodeSlot<TNodeData> {
  return {
    id: String(value.id ?? ''),
    data: value.data as TNodeData,
    selected: Boolean(value.selected),
    connectable: canEdit.value ? value.connectable as YdWorkflowCanvasNodeSlot<TNodeData>['connectable'] : false,
  }
}

function onInit() {
  if (disposed) {
    return
  }
  ready.value = true
  emit('init')
}

function addNodes(value: YdWorkflowNode<TNodeData> | YdWorkflowNode<TNodeData>[]) {
  if (!disposed && ready.value && canEdit.value) {
    flow.addNodes(value as unknown as Node | Node[])
  }
}

function addEdges(value: YdWorkflowEdge<TEdgeData> | YdWorkflowEdge<TEdgeData>[]) {
  if (!disposed && ready.value && canEdit.value) {
    flow.addEdges(value as unknown as Edge | Edge[])
  }
}

function updateNodeData(id: string, patch: Partial<TNodeData>) {
  if (!disposed && ready.value && canEdit.value) {
    flow.updateNodeData(id, patch)
  }
}

function screenToCanvasCoordinate(point: YdWorkflowPoint): YdWorkflowPoint {
  if (disposed || !ready.value) {
    throw new Error('工作流画布尚未就绪或已销毁')
  }
  return flow.screenToFlowCoordinate(point)
}

function fitView(options: YdWorkflowViewportOptions = {}): Promise<boolean> {
  return ready.value ? flow.fitView(options) : Promise.resolve(false)
}

function setCenter(x: number, y: number, options: YdWorkflowCenterOptions = {}): Promise<boolean> {
  return ready.value ? flow.setCenter(x, y, options) : Promise.resolve(false)
}

function onDispose() {
  disposed = true
  ready.value = false
}

defineExpose({ addNodes, addEdges, updateNodeData, screenToCanvasCoordinate, fitView, setCenter } satisfies YdWorkflowCanvasInstance<TNodeData, TEdgeData>)
onBeforeUnmount(onDispose)
</script>

<template>
  <div role="region" class="yd-workflow-canvas" :class="[`yd-workflow-canvas--${theme}`, { 'yd-workflow-canvas--disabled': disabled, 'yd-workflow-canvas--readonly': readonly }]" :aria-label="ariaLabel" :aria-disabled="disabled || undefined" :aria-readonly="readonly || undefined" :inert="disabled || undefined" v-bind="$attrs">
    <VueFlow
      :id="flowId"
      v-model:nodes="flowNodes"
      v-model:edges="flowEdges"
      :nodes-draggable="canEdit"
      :nodes-connectable="canEdit"
      :elements-selectable="!disabled"
      :snap-to-grid="snapToGrid"
      :snap-grid="snapGrid"
      :min-zoom="minZoom"
      :max-zoom="maxZoom"
      :delete-key-code="resolveYdWorkflowDeleteKeyCode(props, deleteKeyCode)"
      :fit-view-on-init="fitViewOnInit"
      @connect="onConnect"
      @node-click="onNodeClick"
      @edge-click="onEdgeClick"
      @pane-click="emit('paneClick')"
      @init="onInit"
    >
      <template v-for="name in getNodeSlotNames()" :key="name" #[name]="node">
        <slot :name="name" v-bind="nodeSlotProps(node)" />
      </template>
      <Background v-if="showBackground" :pattern-color="backgroundOptions.patternColor" :gap="backgroundOptions.gap" :size="backgroundOptions.size" />
      <Controls v-if="showControls" :position="controlsOptions.position || 'bottom-left'" :show-interactive="canEdit" />
      <MiniMap
        v-if="showMiniMap"
        :position="miniMapOptions.position || 'bottom-right'"
        :pannable="miniMapOptions.pannable ?? true"
        :zoomable="miniMapOptions.zoomable ?? true"
        :width="miniMapOptions.width"
        :height="miniMapOptions.height"
      />
    </VueFlow>
  </div>
</template>

<style scoped>
.yd-workflow-canvas,
.yd-workflow-canvas :deep(.vue-flow) {
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
}

.yd-workflow-canvas--inherit,
.yd-workflow-canvas--light {
  --vf-node-bg: var(--color-bg-1, #fff);
  --vf-node-color: var(--color-border-3, #b1b1b7);
  --vf-node-text: var(--color-text-1, #222);
  --vf-connection-path: var(--color-border-3, #b1b1b7);
  --vf-handle: var(--color-text-2, #555);
}

.yd-workflow-canvas--dark {
  --vf-node-bg: var(--color-bg-2, #1f2937);
  --vf-node-color: var(--color-border-3, #64748b);
  --vf-node-text: var(--color-text-1, #e5e7eb);
  --vf-connection-path: var(--color-border-3, #64748b);
  --vf-handle: var(--color-text-2, #cbd5e1);
}

.yd-workflow-canvas :deep(.vue-flow__controls) {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  box-shadow: 0 5px 18px rgb(15 23 42 / 10%);
}

.yd-workflow-canvas :deep(.vue-flow__controls-button) {
  background: var(--color-bg-1);
  border-bottom: 1px solid var(--color-border-2);
  fill: var(--color-text-2);
}

.yd-workflow-canvas :deep(.vue-flow__controls-button:hover) {
  background: var(--color-fill-2);
}

.yd-workflow-canvas :deep(.vue-flow__minimap) {
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 7px;
  box-shadow: 0 7px 22px rgb(15 23 42 / 11%);
}

.yd-workflow-canvas :deep(.vue-flow__minimap-mask) {
  fill: var(--color-fill-3);
}

.yd-workflow-canvas--disabled :deep(.vue-flow) {
  pointer-events: none;
  cursor: not-allowed;
}
</style>
