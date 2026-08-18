<script setup lang="ts">
import type {WikiGraphSnapshot} from '@/api/modules/platform-wiki'
import * as echarts from 'echarts'
import {computed, inject, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {fetchWikiGraph} from '@/api/modules/platform-wiki'
import {pageTypeColor, pageTypeLabel, wikiWorkbenchKey} from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!

const graph = ref<WikiGraphSnapshot | null>(null)
const loading = ref(false)
const chartEl = ref<HTMLElement | null>(null)
const activeCommunity = ref('')
const activeInsight = ref(-1)
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

const paletteTokens = ['rgb(var(--primary-2))', 'rgb(var(--primary-3))', 'rgb(var(--primary-4))', 'rgb(var(--primary-5))', 'rgb(var(--primary-6))', 'rgb(var(--primary-7))', 'rgb(var(--primary-8))', 'rgb(var(--primary-9))', 'rgb(var(--primary-10))', 'var(--color-text-2)']

const insightKindMeta: Record<string, { label: string, icon: string }> = {
  SURPRISING_CONNECTION: {label: '惊奇连接', icon: 'i-ri:sparkling-2-line'},
  ORPHAN: {label: '孤立页面', icon: 'i-ri:ghost-line'},
  SPARSE_COMMUNITY: {label: '稀疏社区', icon: 'i-ri:bubble-chart-line'},
  BRIDGE: {label: '桥接页面', icon: 'i-ri:links-line'},
}

const nodeIndex = computed(() => {
  const map = new Map<string, number>()
  graph.value?.nodes.forEach((node, index) => map.set(node.id, index))
  return map
})

async function load() {
  if (!store.spaceId.value) {
    graph.value = null
    return
  }
  loading.value = true
  try {
    const res = await fetchWikiGraph(store.spaceId.value)
    graph.value = res.data || null
    activeCommunity.value = ''
    activeInsight.value = -1
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function resolveColor(value: string, fallback: string) {
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

function palette() {
  return paletteTokens.map(token => resolveColor(token, 'rgb(128 128 128)'))
}

function communityColor(index: number) {
  return paletteTokens[index % paletteTokens.length]
}

function nodeColor(node: WikiGraphSnapshot['nodes'][number], colors: string[]): string {
  const communities = graph.value?.communities || []
  if (communities.length) {
    const index = communities.findIndex(item => item.id === node.community)
    if (index >= 0) {
      return colors[index % colors.length]
    }
  }
  return resolveColor(pageTypeColor(node.type), colors[4])
}

function renderChart() {
  if (!chartEl.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(chartEl.value)
    chart.on('click', (params: any) => {
      if (params.dataType === 'node' && params.data?.id != null) {
        store.openPage({nodeId: String(params.data.id), title: params.data.title})
      }
    })
  }
  const data = graph.value
  if (!data || !data.nodes.length) {
    chart.clear()
    return
  }
  const communities = data.communities || []
  const colors = palette()
  const useCommunity = communities.length > 0
  const categories = useCommunity
    ? communities.map((item, index) => ({name: item.label, itemStyle: {color: colors[index % colors.length]}}))
    : [...new Set(data.nodes.map(node => node.type || 'concept'))].map(type => ({
      name: pageTypeLabel(type),
      itemStyle: {color: resolveColor(pageTypeColor(type), colors[4])},
    }))

  const maxDegree = Math.max(1, ...data.nodes.map(node => node.degree))
  const maxWeight = Math.max(1, ...data.edges.map(edge => edge.weight))

  const textColor = resolveColor('var(--color-text-2)', 'rgb(128 128 128)')
  const surfaceColor = resolveColor('var(--color-bg-1)', 'rgb(255 255 255)')
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      renderMode: 'richText',
      formatter: (params: any) => {
        if (params.dataType === 'edge') {
          const edge = params.data
          return `${edge.sourceTitle} ↔ ${edge.targetTitle}\n权重 ${edge.weight.toFixed(2)} · ${edge.signal || ''}`
        }
        const node = params.data
        return [
          node.title,
          `类型：${pageTypeLabel(node.type)}`,
          `连接度：${node.degree}`,
          node.communityLabel ? `社区：${node.communityLabel}` : '',
        ].filter(Boolean).join('\n')
      },
    },
    legend: {
      data: categories.map(item => item.name),
      top: 8,
      left: 8,
      orient: 'vertical',
      textStyle: {fontSize: 11, color: textColor},
      itemWidth: 10,
      itemHeight: 10,
      icon: 'circle',
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      categories,
      force: {
        repulsion: 260,
        edgeLength: [60, 140],
        gravity: 0.12,
        friction: 0.25,
      },
      label: {
        show: true,
        position: 'right',
        fontSize: 11,
        color: textColor,
        formatter: (params: any) => params.data.title,
      },
      emphasis: {
        focus: 'adjacency',
        label: {fontWeight: 'bold'},
        lineStyle: {width: 3},
      },
      blur: {
        itemStyle: {opacity: 0.12},
        lineStyle: {opacity: 0.04},
        label: {opacity: 0.15},
      },
      data: data.nodes.map((node) => {
        const communityIndex = communities.findIndex(item => item.id === node.community)
        return {
          id: node.id,
          name: node.title,
          title: node.title,
          type: node.type,
          degree: node.degree,
          communityLabel: communityIndex >= 0 ? communities[communityIndex].label : '',
          category: useCommunity
            ? (communityIndex >= 0 ? communityIndex : 0)
            : [...new Set(data.nodes.map(item => item.type || 'concept'))].indexOf(node.type || 'concept'),
          symbolSize: 14 + (node.degree / maxDegree) * 30,
          itemStyle: {
            color: nodeColor(node, colors),
            borderColor: surfaceColor,
            borderWidth: 1.5,
            shadowBlur: 8,
            shadowColor: 'rgba(0,0,0,0.15)',
          },
        }
      }),
      links: data.edges.map(edge => ({
        source: edge.source,
        target: edge.target,
        weight: edge.weight,
        signal: edge.signal,
        sourceTitle: data.nodes.find(node => node.id === edge.source)?.title || edge.source,
        targetTitle: data.nodes.find(node => node.id === edge.target)?.title || edge.target,
        lineStyle: {
          width: 0.6 + (edge.weight / maxWeight) * 3.4,
          opacity: 0.25 + (edge.weight / maxWeight) * 0.55,
          curveness: 0.08,
        },
      })),
    }],
  }, {notMerge: true})
}

// echarts 的 source/target 需要与 data 的 name 或 id 匹配；graph series 用 id 匹配 links
function highlightNodes(nodeIds: string[]) {
  if (!chart) {
    return
  }
  chart.dispatchAction({type: 'downplay', seriesIndex: 0})
  const indexes = nodeIds
    .map(id => nodeIndex.value.get(id))
    .filter((index): index is number => index !== undefined)
  if (indexes.length) {
    chart.dispatchAction({type: 'highlight', seriesIndex: 0, dataIndex: indexes})
  }
}

function focusCommunity(communityId: string) {
  if (activeCommunity.value === communityId) {
    activeCommunity.value = ''
    chart?.dispatchAction({type: 'downplay', seriesIndex: 0})
    return
  }
  activeCommunity.value = communityId
  activeInsight.value = -1
  const community = graph.value?.communities.find(item => item.id === communityId)
  if (community) {
    highlightNodes(community.nodeIds)
  }
}

function focusInsight(index: number) {
  if (activeInsight.value === index) {
    activeInsight.value = -1
    chart?.dispatchAction({type: 'downplay', seriesIndex: 0})
    return
  }
  activeInsight.value = index
  activeCommunity.value = ''
  const insight = graph.value?.insights[index]
  if (insight) {
    highlightNodes(insight.nodeIds)
  }
}

function insightMeta(kind: string) {
  return insightKindMeta[kind] || {label: kind, icon: 'i-ri:lightbulb-line'}
}

onMounted(() => {
  load()
  if (chartEl.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  }
})

onActivated(() => {
  if (!graph.value && !loading.value) {
    load()
  }
  nextTick(() => chart?.resize())
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})

watch(() => store.spaceId.value, load)
</script>

<template>
  <div class="graph-panel">
    <div class="graph-canvas-card">
      <div class="graph-canvas__toolbar">
        <span class="graph-canvas__hint">
          <FaIcon name="i-ri:drag-move-2-line"/> 拖拽平移 / 滚轮缩放 / 悬停高亮邻居 / 点击节点打开页面
        </span>
        <FaButton size="sm" variant="outline" :loading="loading" @click="load">
          <FaIcon name="i-ri:refresh-line"/>
          刷新图谱
        </FaButton>
      </div>
      <div ref="chartEl" class="graph-canvas"/>
      <div v-if="!loading && (!graph || !graph.nodes.length)" class="graph-canvas__empty">
        <FaIcon name="i-ri:mind-map"/>
        <strong>暂无图谱数据</strong>
        <p>摄入更多资料后，系统会自动构建知识图谱</p>
      </div>
      <div v-if="loading" class="graph-canvas__loading">
        <FaIcon name="i-ri:loader-4-line" class="graph-spin"/>
        正在加载图谱…
      </div>
    </div>

    <aside class="graph-side">
      <FaScrollArea class="graph-side__scroll">
        <FaCard title="社区" class="graph-side__card">
          <template v-if="graph?.communities?.length">
            <button
              v-for="(community, index) in graph.communities"
              :key="community.id"
              type="button"
              class="graph-community"
              :class="{ 'graph-community--active': activeCommunity === community.id }"
              @click="focusCommunity(community.id)"
            >
              <span class="graph-community__dot" :style="{ background: communityColor(index) }"/>
              <span class="graph-community__label">{{ community.label }}</span>
              <span class="graph-community__meta">{{ community.size }} 页 · 内聚 {{ (community.cohesion * 100).toFixed(0) }}%</span>
              <span v-if="community.lowCohesion" class="graph-community__warn">低内聚</span>
            </button>
          </template>
          <p v-else class="graph-side__empty">暂无社区划分</p>
        </FaCard>

        <FaCard title="图谱洞察" class="graph-side__card">
          <template v-if="graph?.insights?.length">
            <button
              v-for="(insight, index) in graph.insights"
              :key="`${insight.kind}-${index}`"
              type="button"
              class="graph-insight"
              :class="{ 'graph-insight--active': activeInsight === index }"
              @click="focusInsight(index)"
            >
              <div class="graph-insight__head">
                <span class="graph-insight__kind">
                  <FaIcon :name="insightMeta(insight.kind).icon"/>
                  {{ insightMeta(insight.kind).label }}
                </span>
                <strong>{{ insight.title }}</strong>
              </div>
              <p>{{ insight.description }}</p>
            </button>
          </template>
          <p v-else class="graph-side__empty">暂无洞察</p>
        </FaCard>
      </FaScrollArea>
    </aside>
  </div>
</template>

<style scoped>
.graph-panel {
  display: flex;
  gap: 14px;
  height: 100%;
  min-height: 0;
  padding: 16px;
  background: var(--color-fill-1);
}

.graph-canvas-card {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  background: var(--color-bg-1);
  box-shadow: 0 2px 10px rgb(0 0 0 / 4%);
}

.graph-canvas__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border-2);
}

.graph-canvas__hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-3);
  font-size: 12px;
}

.graph-canvas {
  flex: 1;
  min-height: 0;
}

.graph-canvas__empty,
.graph-canvas__loading {
  position: absolute;
  inset: 48px 0 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 8px;
  color: var(--color-text-3);
  pointer-events: none;
}

.graph-canvas__empty :deep(svg) {
  color: rgb(var(--primary-6));
  font-size: 40px;
}

.graph-canvas__empty strong {
  color: var(--color-text-1);
}

.graph-canvas__empty p {
  margin: 0;
  font-size: 12px;
}

.graph-spin {
  animation: graph-rotate 1s linear infinite;
}

@keyframes graph-rotate {
  to {
    transform: rotate(360deg);
  }
}

.graph-side {
  width: 320px;
  flex-shrink: 0;
}

.graph-side__scroll {
  max-height: 100%;
}

.graph-side__card {
  margin-bottom: 14px;
}

.graph-side__empty {
  margin: 0;
  padding: 12px 0;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}

.graph-community {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
}

.graph-community:hover {
  background: var(--color-fill-2);
}

.graph-community--active {
  border-color: rgba(var(--primary-6), 0.35);
  background: var(--color-fill-2);
  color: var(--color-text-2);
}

.graph-community__dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: 50%;
}

.graph-community__label {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.graph-community__meta {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 11px;
}

.graph-community__warn {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 10px;
  font-weight: 600;
}

.graph-insight {
  display: grid;
  gap: 6px;
  width: 100%;
  margin-bottom: 8px;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.graph-insight:hover {
  border-color: rgba(var(--primary-6), 0.35);
}

.graph-insight--active {
  border-color: rgb(var(--primary-6));
  background: rgba(var(--primary-6), 0.06);
}

.graph-insight__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.graph-insight__kind {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.graph-insight__head strong {
  font-size: 13px;
}

.graph-insight p {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .graph-panel {
    flex-direction: column;
    overflow: auto;
  }

  .graph-canvas-card {
    min-height: 480px;
  }

  .graph-side {
    width: 100%;
  }
}
</style>
