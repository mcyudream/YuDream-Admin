<template>
  <div ref="chartRef" class="dataviz-sankey-chart" :style="containerStyle" />
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue'
import * as d3 from 'd3'
import { computed, ref, watch } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import type { ChartDataset, ChartLink, ChartNode, ChartTheme } from '../types'
import { adaptThemeForD3 } from '../utils'

const props = withDefaults(defineProps<{
  dataset: ChartDataset
  theme?: ChartTheme
  height?: number | string
}>(), {
  theme: 'light',
  height: 400,
})

const chartRef = ref<HTMLElement | null>(null)

const containerStyle = computed<CSSProperties>(() => ({
  width: '100%',
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
}))

function extractSankeyData(dataset: ChartDataset): { nodes: ChartNode[], links: ChartLink[] } {
  const series = dataset.series?.[0]
  if (series && typeof series === 'object' && Array.isArray(series.nodes) && Array.isArray(series.links)) {
    return {
      nodes: series.nodes as ChartNode[],
      links: series.links as ChartLink[],
    }
  }
  if (dataset.nodes && dataset.links) {
    return { nodes: dataset.nodes, links: dataset.links }
  }
  return { nodes: [], links: [] }
}

function computeLayers(nodes: ChartNode[], links: ChartLink[]): number[] {
  const nodeIndex = new Map(nodes.map((n, i) => [n.id, i]))
  const inDegrees = new Array(nodes.length).fill(0)
  const outDegrees = new Array(nodes.length).fill(0)
  const adjacency: number[][] = Array.from({ length: nodes.length }, () => [])

  for (const link of links) {
    const s = nodeIndex.get(link.source)
    const t = nodeIndex.get(link.target)
    if (s === undefined || t === undefined) continue
    inDegrees[t]++
    outDegrees[s]++
    adjacency[s].push(t)
  }

  const layers = new Array(nodes.length).fill(-1)
  const queue: number[] = []

  for (let i = 0; i < nodes.length; i++) {
    if (inDegrees[i] === 0) {
      layers[i] = 0
      queue.push(i)
    }
  }

  while (queue.length) {
    const u = queue.shift()!
    for (const v of adjacency[u]) {
      layers[v] = Math.max(layers[v], layers[u] + 1)
      queue.push(v)
    }
  }

  for (let i = 0; i < nodes.length; i++) {
    if (layers[i] < 0) layers[i] = 0
  }

  return layers
}

function render() {
  const el = chartRef.value
  if (!el) return

  const { nodes, links } = extractSankeyData(props.dataset)
  const theme = adaptThemeForD3(props.theme)

  const width = el.clientWidth || 600
  const height = el.clientHeight || 400

  el.innerHTML = ''
  const svg = d3.select(el)
    .append('svg')
    .attr('width', width)
    .attr('height', height)
    .attr('viewBox', [0, 0, width, height])

  const margin = { top: 20, right: 20, bottom: 20, left: 20 }
  const innerWidth = width - margin.left - margin.right
  const innerHeight = height - margin.top - margin.bottom

  const g = svg.append('g').attr('transform', `translate(${margin.left},${margin.top})`)

  const layers = computeLayers(nodes, links)
  const layerCount = Math.max(1, ...layers) + 1
  const layerNodes = new Map<number, ChartNode[]>()
  for (let i = 0; i < nodes.length; i++) {
    const layer = layers[i]
    if (!layerNodes.has(layer)) layerNodes.set(layer, [])
    layerNodes.get(layer)!.push(nodes[i])
  }

  const nodeHeight = 28
  const xScale = d3.scaleLinear().domain([0, layerCount - 1]).range([0, innerWidth])
  const nodeMap = new Map<string, { x: number, y: number, node: ChartNode }>()

  for (const [layer, list] of layerNodes.entries()) {
    const gap = list.length > 1 ? innerHeight / (list.length + 1) : innerHeight / 2
    list.forEach((node, i) => {
      const x = xScale(layer)
      const y = list.length > 1 ? gap * (i + 1) : gap
      nodeMap.set(node.id, { x, y, node })
    })
  }

  const linkGen = d3.linkHorizontal()
    .x(d => (d as [number, number])[0])
    .y(d => (d as [number, number])[1])

  g.append('g')
    .attr('fill', 'none')
    .attr('stroke-opacity', 0.35)
    .selectAll('path')
    .data(links)
    .join('path')
    .attr('stroke', theme.grid)
    .attr('stroke-width', d => Math.max(1, Math.sqrt((d.value as number) || 1) * 2))
    .attr('d', d => {
      const source = nodeMap.get(d.source)
      const target = nodeMap.get(d.target)
      if (!source || !target) return ''
      return linkGen({
        source: [source.x + 12, source.y + nodeHeight / 2],
        target: [target.x - 12, target.y + nodeHeight / 2],
      } as any)
    })

  const nodeGroups = g.append('g')
    .selectAll('g')
    .data(nodes)
    .join('g')
    .attr('transform', d => {
      const pos = nodeMap.get(d.id)
      return `translate(${pos?.x ?? 0},${(pos?.y ?? 0) - nodeHeight / 2})`
    })

  nodeGroups
    .append('rect')
    .attr('width', 24)
    .attr('height', nodeHeight)
    .attr('rx', 4)
    .attr('fill', (_d, i) => theme.colors[i % theme.colors.length])

  nodeGroups
    .append('text')
    .attr('x', 30)
    .attr('y', nodeHeight / 2 + 4)
    .attr('fill', theme.text)
    .attr('font-size', 12)
    .text(d => d.name)
}

useResizeObserver(chartRef, render)
watch(() => [props.dataset, props.theme], render, { deep: true })
</script>

<style scoped>
.dataviz-sankey-chart {
  position: relative;
}
</style>
