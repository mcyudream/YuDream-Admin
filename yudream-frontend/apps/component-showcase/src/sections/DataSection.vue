<script setup lang="ts">
import type { YdGraphEdge, YdGraphNode, YdGraphNodeEvent, YdGraphNodeReducer } from '@yudream/components'
import { YdGraphCanvas } from '@yudream/components'
import { computed, ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const activeTab = ref('profile')
const tabList = [
  { label: '资料', value: 'profile' },
  { label: '账号', value: 'account' },
  { label: '通知', value: 'notice' },
]

const page = ref(1)
const size = ref(10)
const total = ref(100)

const progress = ref(33)

interface GraphCanvasApi {
  reset: () => void
  zoomIn: () => void
  zoomOut: () => void
  focus: (id: string) => void
  refresh: () => void
}

/** 确定性伪随机数，保证每次打开页面图谱布局一致 */
function mulberry32(seed: number) {
  let a = seed
  return () => {
    a |= 0
    a = (a + 0x6D2B79F5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const atlasClusters = [
  { key: 'core', label: '平台核心', color: '#2563eb', cx: 0, cy: 0 },
  { key: 'visual', label: '数据可视化', color: '#16a34a', cx: 95, cy: 12 },
  { key: 'ai', label: '人工智能', color: '#9333ea', cx: 55, cy: 82 },
  { key: 'knowledge', label: '知识管理', color: '#0891b2', cx: -60, cy: 80 },
  { key: 'engineering', label: '工程效能', color: '#ea580c', cx: -98, cy: -8 },
  { key: 'ecosystem', label: '开放生态', color: '#db2777', cx: -50, cy: -84 },
]

const atlasTerms: Record<string, string[]> = {
  core: ['运行时', '插件宿主', '鉴权', '配置', '事件', '调度', '审计', '租户', '权限', '契约', '会话', '菜单'],
  visual: ['图谱', '画布', '图表', '看板', '拓扑', '时间线', '热力', '桑基', '透视', '大屏', '仪表', '布局'],
  ai: ['语义检索', '向量', '推理', '提示词', '工具调用', '记忆', '代理', '评估', '微调', '多模态', '流式', '知识增强'],
  knowledge: ['文档', 'Wiki', '标签', '版本', '引用', '大纲', '全文索引', '批注', '发布', '订阅', '归档', '模板'],
  engineering: ['流水线', '制品', '灰度', '回滚', '观测', '压测', '扫描', '依赖', '镜像', '缓存', '配额', '演练'],
  ecosystem: ['插件市场', 'Webhook', '开放接口', '沙箱', '主题', '适配器', '同步', '迁移', '互联', '凭证', '授权', '社区'],
}

const atlasQualifiers = ['引擎', '服务', '中心', '模块', '协议', '管道', '框架', '面板', '存储', '网关']

/** 生成官方 demo 风格的聚类图谱：枢纽 + 成员，边连回枢纽并带少量簇内/跨簇关联 */
function buildAtlas() {
  const rng = mulberry32(20240824)
  const nodes: YdGraphNode[] = []
  const edges: YdGraphEdge[] = []
  const usedLabels = new Set<string>()
  const memberIds = new Map<string, string[]>()

  atlasClusters.forEach((cluster, clusterIndex) => {
    const hubId = `hub-${cluster.key}`
    nodes.push({
      id: hubId,
      x: cluster.cx,
      y: cluster.cy,
      label: cluster.label,
      color: cluster.color,
      data: { clusterKey: cluster.key, clusterLabel: cluster.label },
    })
    const ids: string[] = []
    const terms = atlasTerms[cluster.key] ?? []
    for (let i = 0; i < 40; i++) {
      const term = terms[Math.floor(rng() * terms.length)]
      const qualifier = atlasQualifiers[Math.floor(rng() * atlasQualifiers.length)]
      let label = `${term}${qualifier}`
      if (usedLabels.has(label)) {
        label = `${label} ${i + 1}`
      }
      usedLabels.add(label)
      const angle = rng() * Math.PI * 2
      const radius = 12 + rng() * 36
      const id = `${cluster.key}-${i}`
      nodes.push({
        id,
        x: cluster.cx + Math.cos(angle) * radius,
        y: cluster.cy + Math.sin(angle) * radius,
        label,
        color: cluster.color,
        data: { clusterKey: cluster.key, clusterLabel: cluster.label },
      })
      ids.push(id)
      edges.push({ source: id, target: hubId })
      if (rng() < 0.3 && ids.length > 1) {
        edges.push({ source: id, target: ids[Math.floor(rng() * (ids.length - 1))] })
      }
    }
    memberIds.set(cluster.key, ids)
    if (clusterIndex > 0) {
      const prevIds = memberIds.get(atlasClusters[clusterIndex - 1].key) ?? []
      for (let i = 0; i < 4; i++) {
        edges.push({ source: ids[Math.floor(rng() * ids.length)], target: prevIds[Math.floor(rng() * prevIds.length)] })
      }
    }
  })

  // 与官方 demo 一致：按度数把节点大小归一化到 3~20
  const degrees = new Map<string, number>()
  for (const edge of edges) {
    degrees.set(edge.source, (degrees.get(edge.source) ?? 0) + 1)
    degrees.set(edge.target, (degrees.get(edge.target) ?? 0) + 1)
  }
  const maxDegree = Math.max(...degrees.values())
  for (const node of nodes) {
    const degree = degrees.get(node.id) ?? 0
    node.size = 3 + (degree / maxDegree) ** 0.7 * 17
  }
  return { nodes, edges, degrees }
}

const atlas = buildAtlas()
const atlasNodes = atlas.nodes
const atlasEdges = atlas.edges

const atlasCanvas = ref<GraphCanvasApi | null>(null)
const selectedNode = ref<YdGraphNode | null>(null)
const query = ref('')
const hiddenClusters = ref<Set<string>>(new Set())

const atlasClusterCounts = computed(() => atlasClusters.map(cluster => ({
  ...cluster,
  count: atlasNodes.filter(node => node.data?.clusterKey === cluster.key).length,
})))

const suggestions = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) {
    return []
  }
  return atlasNodes.filter(node => node.label?.includes(keyword)).slice(0, 6)
})

const selectedDegree = computed(() => selectedNode.value ? atlas.degrees.get(selectedNode.value.id) ?? 0 : 0)

function clusterLabelOf(node: YdGraphNode) {
  return typeof node.data?.clusterLabel === 'string' ? node.data.clusterLabel : ''
}

function onAtlasNodeClick(payload: YdGraphNodeEvent) {
  selectedNode.value = payload.data ?? null
}

function onAtlasStageClick() {
  selectedNode.value = null
  query.value = ''
}

function pickSuggestion(node: YdGraphNode) {
  query.value = ''
  selectedNode.value = node
  atlasCanvas.value?.focus(node.id)
}

function toggleCluster(key: string) {
  const next = new Set(hiddenClusters.value)
  if (next.has(key)) {
    next.delete(key)
  }
  else {
    next.add(key)
  }
  hiddenClusters.value = next
}

/** 聚类筛选：隐藏的聚类经自定义 nodeReducer 置为 hidden，组件会在内置高亮之后叠加该结果 */
const atlasNodeReducer: YdGraphNodeReducer = (_node, data) => {
  const key = typeof data.clusterKey === 'string' ? data.clusterKey : ''
  return hiddenClusters.value.has(key) ? { hidden: true } : {}
}

watch(hiddenClusters, () => atlasCanvas.value?.refresh())

const graphCanvasCode = `<script setup lang="ts">
import type { YdGraphEdge, YdGraphNode } from '@yudream/components'
import { YdGraphCanvas } from '@yudream/components'

const nodes: YdGraphNode[] = [
  { id: 'platform', x: 0, y: 0, label: '平台', size: 16, color: '#2563eb' },
  { id: 'wiki', x: 2, y: 1, label: '知识库', size: 10, color: '#0891b2' },
]
const edges: YdGraphEdge[] = [
  { source: 'platform', target: 'wiki', type: 'arrow', label: '挂载' },
]
<\/script>

<template>
  <YdGraphCanvas
    :nodes="nodes"
    :edges="edges"
    :settings="{ labelDensity: 0.07, enableEdgeEvents: true }"
    style="height: 560px"
    @click-node="({ data }) => console.log('选中', data)"
    @click-stage="() => console.log('点击空白')"
  >
    <template #overlay>
      <!-- 悬浮搜索框、说明面板等自定义图层 -->
    </template>
  </YdGraphCanvas>
</template>`
</script>

<template>
  <DemoCard title="FaCard" description="标题 / 描述 / 自定义头尾">
    <div class="demo-row !items-start">
      <FaCard title="卡片标题" description="这是一段卡片描述文字" class="w-72">
        卡片内容区域，可放置任意内容。
      </FaCard>
      <FaCard class="w-96">
        <template #header>
          <div class="flex items-center justify-between gap-4">
            <div>
              <div class="text-base font-semibold">
                自定义头部
              </div>
              <div class="text-sm text-muted-foreground">
                header slot 会覆盖 title 和 description
              </div>
            </div>
            <FaIcon name="i-ri:badge-check-line" class="size-5 text-primary" />
          </div>
        </template>

        卡片内容区域

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <FaButton variant="outline">
              取消
            </FaButton>
            <FaButton>确定</FaButton>
          </div>
        </template>
      </FaCard>
    </div>
  </DemoCard>

  <DemoCard title="FaTabs" description="list + 插槽面板">
    <FaTabs v-model="activeTab" :list="tabList" class="max-w-xl">
      <template #profile>
        <div class="rounded-md bg-muted/50 p-4 text-sm">
          这里展示用户基础资料。
        </div>
      </template>
      <template #account>
        <div class="rounded-md bg-muted/50 p-4 text-sm">
          这里展示账号安全设置。
        </div>
      </template>
      <template #notice>
        <div class="rounded-md bg-muted/50 p-4 text-sm">
          这里展示消息通知偏好。
        </div>
      </template>
    </FaTabs>
  </DemoCard>

  <DemoCard title="FaPagination" description="页码 / 每页条数 / 总数">
    <FaPagination v-model:page="page" v-model:size="size" :total="total" />
  </DemoCard>

  <DemoCard title="FaProgress" description="不同进度值">
    <div class="grid max-w-xl gap-3">
      <FaProgress v-model="progress" />
      <FaProgress :model-value="66" />
      <FaProgress :model-value="100" />
    </div>
    <div class="demo-row">
      <FaButton size="sm" variant="outline" @click="progress = Math.max(0, progress - 10)">
        -10%
      </FaButton>
      <FaButton size="sm" variant="outline" @click="progress = Math.min(100, progress + 10)">
        +10%
      </FaButton>
    </div>
  </DemoCard>

  <DemoCard
    title="YdGraphCanvas"
    description="Sigma.js 官方 demo 同款知识地图：聚类着色、度数映射节点大小、悬停信息卡、邻域高亮、搜索定位、聚类筛选、缩放/全屏控制"
    :code="graphCanvasCode"
  >
    <div class="atlas">
      <YdGraphCanvas
        ref="atlasCanvas"
        class="atlas__canvas"
        :nodes="atlasNodes"
        :edges="atlasEdges"
        :selected-id="selectedNode?.id ?? null"
        :node-reducer="atlasNodeReducer"
        @click-node="onAtlasNodeClick"
        @click-stage="onAtlasStageClick"
      >
        <template #overlay>
          <div class="atlas__side">
            <div class="atlas__search">
              <FaInput v-model="query" clearable placeholder="搜索节点…" />
              <div v-if="query && suggestions.length" class="atlas__suggestions">
                <button
                  v-for="item in suggestions"
                  :key="item.id"
                  type="button"
                  class="atlas__suggestion"
                  @click="pickSuggestion(item)"
                >
                  <span class="atlas__suggestion-dot" :style="{ background: item.color }" />
                  <span class="atlas__suggestion-label">{{ item.label }}</span>
                  <span class="atlas__suggestion-cluster">{{ clusterLabelOf(item) }}</span>
                </button>
              </div>
            </div>

            <div class="atlas__panel">
              <div class="atlas__panel-title">
                <FaIcon name="i-ri:information-line" />
                图谱说明
              </div>
              <p class="atlas__panel-text">
                {{ atlasNodes.length }} 个节点 · {{ atlasEdges.length }} 条关系。节点大小由关联度数决定，颜色代表所属聚类；悬停查看邻域，点击节点查看详情。
              </p>
              <div v-if="selectedNode" class="atlas__selection">
                <span>当前选中</span>
                <strong>{{ selectedNode.label }}</strong>
                <span>{{ clusterLabelOf(selectedNode) }} · {{ selectedDegree }} 条关联</span>
              </div>
            </div>

            <div class="atlas__panel">
              <div class="atlas__panel-title">
                <FaIcon name="i-ri:apps-2-line" />
                聚类
              </div>
              <button
                v-for="cluster in atlasClusterCounts"
                :key="cluster.key"
                type="button"
                class="atlas__cluster"
                :class="{ 'is-hidden': hiddenClusters.has(cluster.key) }"
                @click="toggleCluster(cluster.key)"
              >
                <span class="atlas__cluster-dot" :style="{ background: cluster.color }" />
                <span class="atlas__cluster-label">{{ cluster.label }}</span>
                <span class="atlas__cluster-count">{{ cluster.count }}</span>
                <FaIcon :name="hiddenClusters.has(cluster.key) ? 'i-ri:eye-off-line' : 'i-ri:eye-line'" />
              </button>
            </div>
          </div>
        </template>
      </YdGraphCanvas>
    </div>
  </DemoCard>

  <DemoCard title="FaScrollArea" description="固定高度纵向滚动">
    <FaScrollArea class="h-56 w-64 border rounded-md">
      <div v-for="item in 20" :key="item" class="p-3 text-sm">
        滚动内容 {{ item }}
      </div>
    </FaScrollArea>
  </DemoCard>
</template>

<style scoped>
.atlas {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-1);
}

.atlas__canvas {
  height: 620px;
}

.atlas__side {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  display: flex;
  width: 264px;
  max-height: calc(100% - 28px);
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
}

.atlas__search {
  position: relative;
}

.atlas__suggestions {
  position: absolute;
  z-index: 3;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
  box-shadow: 0 8px 24px rgb(0 0 0 / 10%);
}

.atlas__suggestion {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 0;
  border-bottom: 1px solid var(--color-border-2);
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

.atlas__suggestion:last-child {
  border-bottom: 0;
}

.atlas__suggestion:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.atlas__suggestion-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.atlas__suggestion-label {
  overflow: hidden;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.atlas__suggestion-cluster {
  color: var(--color-text-3);
  font-size: 11px;
}

.atlas__panel {
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  background: var(--color-bg-1);
  box-shadow: 0 2px 10px rgb(0 0 0 / 6%);
}

.atlas__panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border-2);
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 600;
}

.atlas__panel-text {
  margin: 0;
  padding: 10px 12px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.7;
}

.atlas__selection {
  display: grid;
  gap: 4px;
  margin: 0 12px 12px;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-2);
  color: var(--color-text-3);
  font-size: 11px;
}

.atlas__selection strong {
  color: var(--color-text-1);
  font-size: 13px;
}

.atlas__cluster {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

.atlas__cluster:hover {
  background: var(--color-fill-2);
}

.atlas__cluster.is-hidden {
  opacity: 0.45;
}

.atlas__cluster-dot {
  width: 10px;
  height: 10px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.atlas__cluster-label {
  flex: 1;
}

.atlas__cluster-count {
  color: var(--color-text-3);
  font-size: 11px;
}

@media (max-width: 720px) {
  .atlas__canvas {
    height: 480px;
  }

  .atlas__side {
    width: 220px;
  }
}
</style>
