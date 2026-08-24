<script setup lang="ts">
import Graph from 'graphology'
import { createNodeImageProgram } from '@sigma/node-image'
import Sigma from 'sigma'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface GraphCanvasNode {
  id: string
  x: number
  y: number
  label?: string
  kind?: string
  color?: string
  size?: number
  image?: string
}

export interface GraphCanvasEdge {
  id?: string
  source: string
  target: string
  color?: string
  size?: number
}

const props = withDefaults(defineProps<{
  nodes: GraphCanvasNode[]
  edges: GraphCanvasEdge[]
  selectedId?: string | null
  labelDensity?: number
}>(), {
  selectedId: null,
  labelDensity: 0.6,
})
const emit = defineEmits<{ select: [node: GraphCanvasNode | null] }>()
const host = ref<HTMLElement | null>(null)
let graph: Graph | undefined
let renderer: Sigma | undefined

function nodeAttributes(node: GraphCanvasNode) {
  return {
    x: node.x,
    y: node.y,
    label: node.label || '',
    color: node.color || '#64748b',
    size: node.size || 8,
    image: node.image,
    kind: node.kind,
  }
}

function sync() {
  if (!graph || !renderer) return
  graph.clear()
  for (const node of props.nodes) graph.addNode(node.id, nodeAttributes(node))
  for (const [index, edge] of props.edges.entries()) {
    if (!graph.hasNode(edge.source) || !graph.hasNode(edge.target)) continue
    graph.addEdgeWithKey(edge.id || `${edge.source}:${edge.target}:${index}`, edge.source, edge.target, {
      color: edge.color || '#cbd5e1',
      size: edge.size || 1,
    })
  }
  renderer.refresh()
}

function reset() { renderer?.getCamera().animatedReset({ duration: 220 }) }
function zoomIn() { renderer?.getCamera().animatedZoom({ duration: 160 }) }
function zoomOut() { renderer?.getCamera().animatedUnzoom({ duration: 160 }) }
function focus(id: string) {
  if (!renderer || !graph?.hasNode(id)) return
  renderer.getCamera().animate({ x: graph.getNodeAttribute(id, 'x'), y: graph.getNodeAttribute(id, 'y'), ratio: 0.35 }, { duration: 220 })
}
defineExpose({ reset, zoomIn, zoomOut, focus })

onMounted(() => {
  if (!host.value) return
  graph = new Graph()
  renderer = new Sigma(graph, host.value, {
    allowInvalidContainer: false,
    renderLabels: true,
    labelDensity: props.labelDensity,
    labelRenderedSizeThreshold: 10,
    defaultNodeType: 'image',
    nodeProgramClasses: { image: createNodeImageProgram() },
  })
  renderer.on('clickNode', ({ node }) => emit('select', props.nodes.find(value => value.id === node) || null))
  renderer.on('clickStage', () => emit('select', null))
  sync()
})
onBeforeUnmount(() => { renderer?.kill(); renderer = undefined; graph?.clear(); graph = undefined })
watch(() => [props.nodes, props.edges], sync, { deep: true })
watch(() => props.selectedId, value => { if (value) focus(value) })
</script>

<template>
  <div ref="host" class="fa-graph-canvas" role="img" aria-label="关系图画布" />
</template>

<style scoped>
.fa-graph-canvas { width: 100%; height: 100%; min-height: 1px; overflow: hidden; }
</style>
