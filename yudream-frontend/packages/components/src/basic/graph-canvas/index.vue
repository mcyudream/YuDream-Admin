<script setup lang="ts">
import type { NodeHoverDrawingFunction, NodeLabelDrawingFunction } from 'sigma/rendering'
import type { Settings } from 'sigma/settings'
import type { EdgeDisplayData, NodeDisplayData, SigmaEdgeEventPayload, SigmaNodeEventPayload, SigmaStageEventPayload } from 'sigma/types'
import { createNodeImageProgram } from '@sigma/node-image'
import { omit } from 'es-toolkit'
import Graph from 'graphology'
import Sigma from 'sigma'
import { EdgeArrowProgram, EdgeDoubleArrowProgram, EdgeLineProgram } from 'sigma/rendering'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from '../icon/index.vue'

defineOptions({
  name: 'YdGraphCanvas',
})

const props = withDefaults(defineProps<{
  nodes: YdGraphNode[]
  edges: YdGraphEdge[]
  /** 当前选中节点 id，选中节点以高亮环 + 强制标签呈现 */
  selectedId?: string | null
  /** sigma 原生 settings 透传（https://www.sigmajs.org/docs/advanced/customization），运行时变更自动生效 */
  settings?: Partial<Settings>
  nodeReducer?: YdGraphNodeReducer | null
  edgeReducer?: YdGraphEdgeReducer | null
  /** 悬停节点时隐藏无关边并弱化非邻居节点（sigma 官方 events 示例行为） */
  highlightNeighbors?: boolean
  /** focus() 定位节点时的相机 ratio */
  focusRatio?: number
  /** 是否在左下角渲染内置缩放控制条（全屏 / 放大 / 缩小 / 复位），可用 #controls 插槽覆盖 */
  controls?: boolean
}>(), {
  selectedId: null,
  nodeReducer: null,
  edgeReducer: null,
  highlightNeighbors: true,
  focusRatio: 0.3,
  controls: true,
})

const emit = defineEmits<{
  clickNode: [payload: YdGraphNodeEvent]
  doubleClickNode: [payload: YdGraphNodeEvent]
  rightClickNode: [payload: YdGraphNodeEvent]
  downNode: [payload: YdGraphNodeEvent]
  upNode: [payload: YdGraphNodeEvent]
  enterNode: [payload: YdGraphNodeEvent]
  leaveNode: [payload: YdGraphNodeEvent]
  clickEdge: [payload: YdGraphEdgeEvent]
  doubleClickEdge: [payload: YdGraphEdgeEvent]
  rightClickEdge: [payload: YdGraphEdgeEvent]
  enterEdge: [payload: YdGraphEdgeEvent]
  leaveEdge: [payload: YdGraphEdgeEvent]
  clickStage: [payload: YdGraphStageEvent]
  doubleClickStage: [payload: YdGraphStageEvent]
  rightClickStage: [payload: YdGraphStageEvent]
}>()

/** 节点：字段与 sigma 识别的节点属性一一对应（https://www.sigmajs.org/docs/advanced/data） */
export interface YdGraphNode {
  id: string
  x: number
  y: number
  label?: string
  size?: number
  color?: string
  /** 渲染程序名，内置 'image'（无 image 时退化为圆形），也可指向 settings.nodeProgramClasses 注册的程序 */
  type?: string
  /** 节点图片地址，配合 type: 'image' 使用 */
  image?: string
  hidden?: boolean
  forceLabel?: boolean
  zIndex?: number
  /** 自定义业务数据，随图写入并可经事件回传，也可供 nodeReducer 与悬停卡消费（支持 subLabel / clusterLabel） */
  data?: Record<string, unknown>
}

/** 边：字段与 sigma 识别的边属性一一对应 */
export interface YdGraphEdge {
  id?: string
  source: string
  target: string
  label?: string
  size?: number
  color?: string
  /** 渲染程序名，内置 'line' | 'arrow' | 'doubleArrow'，也可指向 settings.edgeProgramClasses 注册的程序 */
  type?: string
  hidden?: boolean
  forceLabel?: boolean
  zIndex?: number
  data?: Record<string, unknown>
}

export interface YdGraphNodeEvent {
  node: string
  data?: YdGraphNode
  event: SigmaNodeEventPayload['event']
}

export interface YdGraphEdgeEvent {
  edge: string
  data?: YdGraphEdge
  event: SigmaEdgeEventPayload['event']
}

export interface YdGraphStageEvent {
  event: SigmaStageEventPayload['event']
}

/** 与 sigma settings.nodeReducer 同签名，组件会先应用内置高亮，再将结果交给自定义 reducer */
export type YdGraphNodeReducer = (node: string, data: Record<string, unknown>) => Partial<NodeDisplayData>
export type YdGraphEdgeReducer = (edge: string, data: Record<string, unknown>) => Partial<EdgeDisplayData>

const host = ref<HTMLElement | null>(null)
const root = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
let graph: Graph | undefined
let renderer: Sigma | undefined
let resizeObserver: ResizeObserver | undefined

const hoveredId = ref<string | null>(null)
let hoveredNeighbors = new Set<string>()
const nodeIndex = new Map<string, YdGraphNode>()
const edgeIndex = new Map<string, YdGraphEdge>()

function resolveCssVar(name: string, fallback: string) {
  const value = root.value ? getComputedStyle(root.value).getPropertyValue(name).trim() : ''
  return value || fallback
}

/** sigma.js 官方 demo 的标签绘制：半透明底盒 + 文本，保证密集图谱中的可读性 */
function createDrawLabel(boxColor: string, textColor: string): NodeLabelDrawingFunction {
  return (context, data, settings) => {
    if (!data.label) {
      return
    }
    const size = settings.labelSize
    context.font = `${settings.labelWeight} ${size}px ${settings.labelFont}`
    const width = context.measureText(data.label).width + 8
    context.globalAlpha = 0.85
    context.fillStyle = boxColor
    context.fillRect(data.x + data.size, data.y + size / 3 - 15, width, 20)
    context.globalAlpha = 1
    context.textBaseline = 'middle'
    context.fillStyle = textColor
    context.fillText(data.label, data.x + data.size + 3, data.y + size / 3)
  }
}

function drawRoundRect(context: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  context.beginPath()
  context.moveTo(x + radius, y)
  context.lineTo(x + width - radius, y)
  context.quadraticCurveTo(x + width, y, x + width, y + radius)
  context.lineTo(x + width, y + height - radius)
  context.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
  context.lineTo(x + radius, y + height)
  context.quadraticCurveTo(x, y + height, x, y + height - radius)
  context.lineTo(x, y + radius)
  context.quadraticCurveTo(x, y, x + radius, y)
  context.closePath()
}

/** sigma.js 官方 demo 的悬停卡：圆角信息盒（主标签 + subLabel + clusterLabel）+ 节点描边环 */
function createDrawHover(boxColor: string, textColor: string): NodeHoverDrawingFunction {
  return (context, data, settings) => {
    const size = settings.labelSize
    const font = settings.labelFont
    const weight = settings.labelWeight
    const subLabelSize = size - 2
    const record = data as Record<string, unknown>
    const label = typeof record.label === 'string' ? record.label : ''
    const subLabel = typeof record.subLabel === 'string' ? record.subLabel : ''
    const clusterLabel = typeof record.clusterLabel === 'string' ? record.clusterLabel : ''
    if (!label) {
      return
    }

    context.font = `${weight} ${size}px ${font}`
    const labelWidth = context.measureText(label).width
    context.font = `${weight} ${subLabelSize}px ${font}`
    const subLabelWidth = subLabel ? context.measureText(subLabel).width : 0
    const clusterLabelWidth = clusterLabel ? context.measureText(clusterLabel).width : 0
    const textWidth = Math.max(labelWidth, subLabelWidth, clusterLabelWidth)

    const x = Math.round(data.x)
    const y = Math.round(data.y)
    const w = Math.round(textWidth) + size / 2 + data.size + 6
    const hLabel = Math.round(size / 2 + 4)
    const hSubLabel = subLabel ? Math.round(subLabelSize / 2 + 9) : 0
    const hClusterLabel = Math.round(subLabelSize / 2 + 9)

    drawRoundRect(context, x, y - hSubLabel - 12, w, hClusterLabel + hLabel + hSubLabel + 12, 5)
    context.shadowOffsetY = 2
    context.shadowBlur = 8
    context.shadowColor = 'rgba(0, 0, 0, 0.25)'
    context.fillStyle = boxColor
    context.fill()
    context.shadowOffsetY = 0
    context.shadowBlur = 0
    context.shadowColor = 'transparent'

    let offsetY = y - hSubLabel - 4
    if (subLabel) {
      context.font = `${weight} ${subLabelSize}px ${font}`
      context.fillStyle = textColor
      context.textBaseline = 'middle'
      context.fillText(subLabel, x + data.size + 3, offsetY)
      offsetY += hSubLabel
    }
    context.font = `${weight} ${size}px ${font}`
    context.fillStyle = textColor
    context.textBaseline = 'middle'
    context.fillText(label, x + data.size + 3, offsetY)
    context.font = `${weight} ${subLabelSize}px ${font}`
    context.fillStyle = typeof record.color === 'string' ? record.color : textColor
    context.fillText(clusterLabel, x + data.size + 3, offsetY + hLabel)

    context.beginPath()
    context.arc(data.x, data.y, data.size + 3, 0, Math.PI * 2)
    context.strokeStyle = typeof record.color === 'string' ? record.color : textColor
    context.lineWidth = 2
    context.stroke()
  }
}

function resolveSettings(): Partial<Settings> {
  const textColor = resolveCssVar('--color-text-1', '#1f2328')
  const boxColor = resolveCssVar('--color-bg-1', '#ffffff')
  return {
    renderLabels: true,
    labelDensity: 0.07,
    labelGridCellSize: 60,
    labelRenderedSizeThreshold: 15,
    labelFont: 'Lato, "PingFang SC", "Microsoft YaHei", sans-serif',
    labelColor: { color: resolveCssVar('--color-text-2', '#4e5969') },
    defaultNodeColor: '#64748b',
    defaultEdgeColor: '#cbd5e1',
    defaultNodeType: 'image',
    defaultEdgeType: 'line',
    defaultDrawNodeLabel: createDrawLabel(boxColor, textColor),
    defaultDrawNodeHover: createDrawHover(boxColor, textColor),
    nodeProgramClasses: { image: createNodeImageProgram({ size: { mode: 'force', value: 256 } }) },
    edgeProgramClasses: { line: EdgeLineProgram, arrow: EdgeArrowProgram, doubleArrow: EdgeDoubleArrowProgram },
    allowInvalidContainer: true,
    zIndex: true,
    ...props.settings,
  }
}

function syncData() {
  if (!graph) {
    return
  }
  graph.clear()
  nodeIndex.clear()
  edgeIndex.clear()
  for (const node of props.nodes) {
    const { id, data, ...attributes } = node
    graph.addNode(id, { size: 8, ...attributes, ...data })
    nodeIndex.set(id, node)
  }
  for (const [index, edge] of props.edges.entries()) {
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) {
      continue
    }
    const { id, source, target, data, ...attributes } = edge
    const key = id || `${source}->${target}:${index}`
    graph.addEdgeWithKey(key, source, target, { size: 1, ...attributes, ...data })
    edgeIndex.set(key, edge)
  }
}

/** 按官方 events 示例实现悬停邻域高亮与选中强调；自定义 reducer 在内置结果之后叠加 */
function applyReducers() {
  if (!renderer || !graph) {
    return
  }
  renderer.setSetting('nodeReducer', (node, data) => {
    let result: Record<string, unknown> = { ...data }
    const hovered = hoveredId.value
    if (props.highlightNeighbors && hovered && graph?.hasNode(hovered)) {
      if (node === hovered || hoveredNeighbors.has(node)) {
        result = { ...result, forceLabel: true }
      }
      else {
        result = { ...result, label: '', color: 'rgba(148, 163, 184, 0.35)' }
      }
    }
    if (node === props.selectedId) {
      result = { ...result, highlighted: true, forceLabel: true, zIndex: 2 }
    }
    if (props.nodeReducer) {
      result = { ...result, ...props.nodeReducer(node, result) }
    }
    return result as Partial<NodeDisplayData>
  })
  renderer.setSetting('edgeReducer', (edge, data) => {
    let result: Record<string, unknown> = { ...data }
    const hovered = hoveredId.value
    if (props.highlightNeighbors && hovered && graph?.hasNode(hovered) && !graph.hasExtremity(edge, hovered)) {
      result = { ...result, hidden: true }
    }
    if (props.edgeReducer) {
      result = { ...result, ...props.edgeReducer(edge, result) }
    }
    return result as Partial<EdgeDisplayData>
  })
}

function toNodeEvent(payload: SigmaNodeEventPayload): YdGraphNodeEvent {
  return { node: payload.node, data: nodeIndex.get(payload.node), event: payload.event }
}

function toEdgeEvent(payload: SigmaEdgeEventPayload): YdGraphEdgeEvent {
  return { edge: payload.edge, data: edgeIndex.get(payload.edge), event: payload.event }
}

function bindEvents() {
  if (!renderer || !graph) {
    return
  }
  renderer.on('enterNode', (payload) => {
    hoveredId.value = payload.node
    hoveredNeighbors = graph?.hasNode(payload.node) ? new Set(graph.neighbors(payload.node)) : new Set()
    if (host.value) {
      host.value.style.cursor = 'pointer'
    }
    renderer?.refresh()
    emit('enterNode', toNodeEvent(payload))
  })
  renderer.on('leaveNode', (payload) => {
    hoveredId.value = null
    hoveredNeighbors = new Set()
    if (host.value) {
      host.value.style.cursor = ''
    }
    renderer?.refresh()
    emit('leaveNode', toNodeEvent(payload))
  })
  renderer.on('clickNode', payload => emit('clickNode', toNodeEvent(payload)))
  renderer.on('doubleClickNode', payload => emit('doubleClickNode', toNodeEvent(payload)))
  renderer.on('rightClickNode', payload => emit('rightClickNode', toNodeEvent(payload)))
  renderer.on('downNode', payload => emit('downNode', toNodeEvent(payload)))
  renderer.on('upNode', payload => emit('upNode', toNodeEvent(payload)))
  renderer.on('clickEdge', payload => emit('clickEdge', toEdgeEvent(payload)))
  renderer.on('doubleClickEdge', payload => emit('doubleClickEdge', toEdgeEvent(payload)))
  renderer.on('rightClickEdge', payload => emit('rightClickEdge', toEdgeEvent(payload)))
  renderer.on('enterEdge', payload => emit('enterEdge', toEdgeEvent(payload)))
  renderer.on('leaveEdge', payload => emit('leaveEdge', toEdgeEvent(payload)))
  renderer.on('clickStage', payload => emit('clickStage', { event: payload.event }))
  renderer.on('doubleClickStage', payload => emit('doubleClickStage', { event: payload.event }))
  renderer.on('rightClickStage', payload => emit('rightClickStage', { event: payload.event }))
}

function reset() {
  renderer?.getCamera().animatedReset({ duration: 220 })
}

function zoomIn() {
  renderer?.getCamera().animatedZoom({ duration: 160 })
}

function zoomOut() {
  renderer?.getCamera().animatedUnzoom({ duration: 160 })
}

function focus(id: string) {
  if (!renderer || !graph?.hasNode(id)) {
    return
  }
  // sigma 渲染前会把节点坐标归一化到 [0,1]，相机动画必须使用归一化后的显示坐标（与官方 demo 一致）
  const displayData = renderer.getNodeDisplayData(id)
  if (!displayData) {
    return
  }
  renderer.getCamera().animate(
    { x: displayData.x, y: displayData.y, ratio: props.focusRatio },
    { duration: 220 },
  )
}

function refresh() {
  renderer?.refresh()
}

function resize() {
  renderer?.resize()
}

function onFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === root.value
}

async function toggleFullscreen() {
  if (!root.value) {
    return
  }
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    }
    else {
      await root.value.requestFullscreen()
    }
  }
  catch {}
}

defineExpose({
  reset,
  zoomIn,
  zoomOut,
  focus,
  refresh,
  resize,
  toggleFullscreen,
  getCamera: () => renderer?.getCamera(),
  getSigma: () => renderer,
  getGraph: () => graph,
})

onMounted(() => {
  if (!host.value) {
    return
  }
  graph = new Graph()
  renderer = new Sigma(graph, host.value, resolveSettings())
  applyReducers()
  bindEvents()
  syncData()
  resizeObserver = new ResizeObserver(() => renderer?.resize())
  resizeObserver.observe(host.value)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  resizeObserver?.disconnect()
  resizeObserver = undefined
  renderer?.kill()
  renderer = undefined
  graph?.clear()
  graph = undefined
})

watch(() => [props.nodes, props.edges], syncData, { deep: true })
watch(() => props.selectedId, () => renderer?.refresh())
watch(() => props.settings, (value) => {
  if (!renderer || !value) {
    return
  }
  // 内置 reducer、绘制函数与渲染程序由组件托管，仅透传其余 settings
  renderer.setSettings(omit(value, ['nodeReducer', 'edgeReducer', 'nodeProgramClasses', 'edgeProgramClasses', 'defaultDrawNodeLabel', 'defaultDrawNodeHover']))
}, { deep: true })
watch(() => props.highlightNeighbors, () => renderer?.refresh())
</script>

<template>
  <div ref="root" class="yd-graph-canvas" :class="{ 'is-fullscreen': isFullscreen }">
    <div ref="host" class="yd-graph-canvas__stage" aria-label="关系图画布" />

    <div v-if="controls" class="yd-graph-canvas__controls">
      <slot name="controls" :zoom-in="zoomIn" :zoom-out="zoomOut" :reset="reset" :toggle-fullscreen="toggleFullscreen" :is-fullscreen="isFullscreen">
        <button
          type="button"
          class="yd-graph-canvas__control"
          :aria-label="isFullscreen ? '退出全屏' : '全屏'"
          :title="isFullscreen ? '退出全屏' : '全屏'"
          @click="toggleFullscreen"
        >
          <Icon :name="isFullscreen ? 'i-ri:fullscreen-exit-line' : 'i-ri:fullscreen-line'" />
        </button>
        <button type="button" class="yd-graph-canvas__control" aria-label="放大" title="放大" @click="zoomIn">
          <Icon name="i-ri:add-line" />
        </button>
        <button type="button" class="yd-graph-canvas__control" aria-label="缩小" title="缩小" @click="zoomOut">
          <Icon name="i-ri:subtract-line" />
        </button>
        <button type="button" class="yd-graph-canvas__control" aria-label="复位视图" title="复位视图" @click="reset">
          <Icon name="i-ri:aim-line" />
        </button>
      </slot>
    </div>

    <slot name="overlay" />
  </div>
</template>

<style scoped>
.yd-graph-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 1px;
  overflow: hidden;
  background: var(--color-bg-1);
}

.yd-graph-canvas__stage {
  position: absolute;
  inset: 0;
}

.yd-graph-canvas__stage :deep(canvas) {
  display: block;
  outline: none;
}

.yd-graph-canvas__controls {
  position: absolute;
  bottom: 14px;
  left: 14px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.yd-graph-canvas__control {
  display: flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border-2);
  border-radius: 50%;
  background: var(--color-bg-1);
  box-shadow: 0 2px 8px rgb(0 0 0 / 10%);
  color: var(--color-text-2);
  cursor: pointer;
  font-size: 15px;
  transition: background 0.15s ease, color 0.15s ease;
}

.yd-graph-canvas__control:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}
</style>
