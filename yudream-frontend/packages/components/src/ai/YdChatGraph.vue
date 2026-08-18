<script setup lang="ts">
import type {EChartsOption} from 'echarts'
import type {YdChatGraph, YdChatGraphNode} from './useYdChatStream'
import * as echarts from 'echarts'
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

const props = withDefaults(defineProps<{
  graph: YdChatGraph
  compact?: boolean
}>(), {
  compact: false,
})

const emits = defineEmits<{
  nodeSelect: [node: YdChatGraphNode]
}>()

const chartEl = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const graphNodes = computed(() => props.graph.nodes || [])
const graphEdges = computed(() => props.graph.edges || [])
const hasEdges = computed(() => graphEdges.value.length > 0)

function renderChart() {
  if (!chartEl.value || !hasEdges.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value)
    chart.on('click', (params: any) => {
      const nodeId = params.dataType === 'node' && params.data?.id != null ? String(params.data.id) : ''
      if (!nodeId) return
      const node = graphNodes.value.find(item => item.id === nodeId)
      if (node && node.role !== 'query' && node.role !== 'source') emits('nodeSelect', node)
    })
  }
  const primaryColor = resolveColor('rgb(var(--primary-6))', 'rgb(128 128 128)')
  const surfaceColor = resolveColor('var(--color-bg-1)', 'rgb(255 255 255)')
  const textColor = resolveColor('var(--color-text-2)', 'rgb(128 128 128)')
  const neutralColor = resolveColor('var(--color-text-3)', 'rgb(160 160 160)')
  const maxScore = Math.max(1, ...graphNodes.value.map(node => node.score || 0))
  const maxWeight = Math.max(1, ...graphEdges.value.map(edge => edge.weight || 0))
  const option: EChartsOption = {
    animationDurationUpdate: 240,
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      confine: true,
      renderMode: 'richText',
      formatter: (params: any) => {
        if (params.dataType === 'edge') {
          return [params.data.sourceTitle, params.data.targetTitle].filter(Boolean).join(' - ')
            + (params.data.signal ? `\n${params.data.signal}` : '')
        }
        const node = params.data
        return [node.title, node.type, node.role].filter(Boolean).join('\n')
      },
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      label: {
        show: !props.compact,
        position: 'right',
        color: textColor,
        fontSize: 11,
        formatter: (params: any) => params.data.title,
      },
      emphasis: {
        focus: 'adjacency',
        label: {show: true, fontWeight: 'bold'},
        lineStyle: {width: 2},
      },
      force: {
        repulsion: props.compact ? 130 : 210,
        edgeLength: props.compact ? [36, 76] : [58, 120],
        gravity: 0.13,
      },
      data: graphNodes.value.map(node => ({
        id: node.id,
        name: node.title,
        title: node.title,
        type: node.type,
        role: node.role,
        symbolSize: node.role === 'query'
          ? (props.compact ? 24 : 28)
          : 12 + ((node.score || 0) / maxScore) * (props.compact ? 13 : 20),
        itemStyle: {
          color: node.role === 'focus' || node.role === 'query' ? primaryColor : neutralColor,
          borderColor: surfaceColor,
          borderWidth: 1,
        },
      })),
      links: graphEdges.value.map(edge => ({
        source: edge.source,
        target: edge.target,
        sourceTitle: graphNodes.value.find(node => node.id === edge.source)?.title || edge.source,
        targetTitle: graphNodes.value.find(node => node.id === edge.target)?.title || edge.target,
        signal: edge.signal,
        lineStyle: {
          width: 0.8 + ((edge.weight || 0) / maxWeight) * 2.2,
          opacity: 0.45,
        },
      })),
    }],
  }
  chart.setOption(option, {notMerge: true})
}

function resolveColor(value: string, fallback: string): string {
  if (!chartEl.value) return fallback
  const probe = document.createElement('span')
  probe.style.color = value
  probe.style.position = 'absolute'
  probe.style.visibility = 'hidden'
  chartEl.value.appendChild(probe)
  const resolved = getComputedStyle(probe).color
  probe.remove()
  return resolved || fallback
}

function disposeChart() {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart?.dispose()
  chart = null
}

async function syncChart() {
  if (!hasEdges.value) {
    disposeChart()
    return
  }
  await nextTick()
  if (!chartEl.value) {
    return
  }
  if (chart && chart.getDom() !== chartEl.value) {
    disposeChart()
  }
  if (!resizeObserver) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
  renderChart()
}

onMounted(syncChart)

watch(() => [props.graph, props.compact, hasEdges.value], syncChart, {deep: true})

onBeforeUnmount(disposeChart)
</script>

<template>
  <div class="yd-chat-graph">
    <div v-if="!hasEdges" class="yd-chat-graph__empty">
      <FaIcon name="i-ri:node-tree"/>
      <span>未返回关联关系</span>
    </div>
    <div v-else ref="chartEl" class="yd-chat-graph__canvas" :class="{ 'is-compact': compact }"/>
  </div>
</template>

<style scoped>
.yd-chat-graph {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}

.yd-chat-graph__canvas {
  height: 300px;
}

.yd-chat-graph__canvas.is-compact {
  height: 220px;
}

.yd-chat-graph__empty {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 8px 10px;
  color: var(--color-text-3);
  font-size: 12px;
}

.yd-chat-graph__empty :deep(svg) {
  color: var(--color-text-2);
}
</style>
