<script setup lang="ts">
import type { YdGraphEdge, YdGraphNode, YdGraphNodeEvent } from '@yudream/components'
import { YdGraphCanvas } from '@yudream/components'
import { computed, ref } from 'vue'

// YdGraphCanvas 内部按需动态加载 sigma，模块本身 SSR 安全
interface GraphCanvasApi {
  focus: (id: string) => void
}

/** 确定性伪随机数，保证文档每次打开布局一致 */
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

const clusters = [
  { key: 'core', label: '平台核心', color: '#2563eb', cx: 0, cy: 0 },
  { key: 'visual', label: '数据可视化', color: '#16a34a', cx: 95, cy: 12 },
  { key: 'ai', label: '人工智能', color: '#9333ea', cx: 55, cy: 82 },
  { key: 'knowledge', label: '知识管理', color: '#0891b2', cx: -60, cy: 80 },
  { key: 'engineering', label: '工程效能', color: '#ea580c', cx: -98, cy: -8 },
  { key: 'ecosystem', label: '开放生态', color: '#db2777', cx: -50, cy: -84 },
]

const terms: Record<string, string[]> = {
  core: ['运行时', '插件宿主', '鉴权', '配置', '事件', '调度', '审计', '租户', '权限', '契约', '会话', '菜单'],
  visual: ['图谱', '画布', '图表', '看板', '拓扑', '时间线', '热力', '桑基', '透视', '大屏', '仪表', '布局'],
  ai: ['语义检索', '向量', '推理', '提示词', '工具调用', '记忆', '代理', '评估', '微调', '多模态', '流式', '知识增强'],
  knowledge: ['文档', 'Wiki', '标签', '版本', '引用', '大纲', '全文索引', '批注', '发布', '订阅', '归档', '模板'],
  engineering: ['流水线', '制品', '灰度', '回滚', '观测', '压测', '扫描', '依赖', '镜像', '缓存', '配额', '演练'],
  ecosystem: ['插件市场', 'Webhook', '开放接口', '沙箱', '主题', '适配器', '同步', '迁移', '互联', '凭证', '授权', '社区'],
}

const qualifiers = ['引擎', '服务', '中心', '模块', '协议', '管道', '框架', '面板', '存储', '网关']

function buildAtlas() {
  const rng = mulberry32(20240824)
  const nodes: YdGraphNode[] = []
  const edges: YdGraphEdge[] = []
  const usedLabels = new Set<string>()
  const memberIds = new Map<string, string[]>()

  clusters.forEach((cluster, clusterIndex) => {
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
    const pool = terms[cluster.key] ?? []
    for (let i = 0; i < 20; i++) {
      let label = `${pool[Math.floor(rng() * pool.length)]}${qualifiers[Math.floor(rng() * qualifiers.length)]}`
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
      const prevIds = memberIds.get(clusters[clusterIndex - 1].key) ?? []
      for (let i = 0; i < 3; i++) {
        edges.push({ source: ids[Math.floor(rng() * ids.length)], target: prevIds[Math.floor(rng() * prevIds.length)] })
      }
    }
  })

  const degrees = new Map<string, number>()
  for (const edge of edges) {
    degrees.set(edge.source, (degrees.get(edge.source) ?? 0) + 1)
    degrees.set(edge.target, (degrees.get(edge.target) ?? 0) + 1)
  }
  const maxDegree = Math.max(...degrees.values())
  for (const node of nodes) {
    node.size = 3 + ((degrees.get(node.id) ?? 0) / maxDegree) ** 0.7 * 15
  }
  return { nodes, edges }
}

const atlas = buildAtlas()

const canvasRef = ref<GraphCanvasApi | null>(null)
const selectedNode = ref<YdGraphNode | null>(null)
const query = ref('')

const suggestions = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) {
    return []
  }
  return atlas.nodes.filter(node => node.label?.includes(keyword)).slice(0, 6)
})

function onNodeClick(payload: YdGraphNodeEvent) {
  selectedNode.value = payload.data ?? null
}

function onStageClick() {
  selectedNode.value = null
  query.value = ''
}

function pick(node: YdGraphNode) {
  query.value = ''
  selectedNode.value = node
  canvasRef.value?.focus(node.id)
}
</script>

<template>
  <div class="atlas-demo">
    <YdGraphCanvas
      ref="canvasRef"
      :nodes="atlas.nodes"
      :edges="atlas.edges"
      :selected-id="selectedNode?.id ?? null"
      class="atlas-demo__canvas"
      @click-node="onNodeClick"
      @click-stage="onStageClick"
    >
      <template #overlay>
        <div class="atlas-demo__search">
          <input v-model="query" class="atlas-demo__input" placeholder="搜索节点…">
          <div v-if="query && suggestions.length" class="atlas-demo__suggestions">
            <button
              v-for="item in suggestions"
              :key="item.id"
              type="button"
              class="atlas-demo__suggestion"
              @click="pick(item)"
            >
              <span class="atlas-demo__dot" :style="{ background: item.color }" />
              {{ item.label }}
            </button>
          </div>
        </div>
        <div v-if="selectedNode" class="atlas-demo__selected">
          已选中：{{ selectedNode.label }}
        </div>
      </template>
    </YdGraphCanvas>
  </div>
</template>

<style scoped>
.atlas-demo {
  overflow: hidden;
  border: 1px solid var(--color-border-2, var(--vp-c-divider));
  border-radius: 10px;
  background: var(--color-bg-1, var(--vp-c-bg));
}

.atlas-demo__canvas {
  height: 460px;
}


.atlas-demo__search {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 220px;
}

.atlas-demo__input {
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--color-border-2, var(--vp-c-divider));
  border-radius: 6px;
  background: var(--color-bg-1, var(--vp-c-bg));
  color: var(--color-text-1, var(--vp-c-text-1));
  font-size: 13px;
  outline: none;
}

.atlas-demo__suggestions {
  position: absolute;
  z-index: 3;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-2, var(--vp-c-divider));
  border-radius: 8px;
  background: var(--color-bg-1, var(--vp-c-bg));
  box-shadow: 0 8px 24px rgb(0 0 0 / 10%);
}

.atlas-demo__suggestion {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 0;
  background: transparent;
  color: var(--color-text-2, var(--vp-c-text-2));
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

.atlas-demo__suggestion:hover {
  background: var(--color-fill-2, var(--vp-c-bg-soft));
}

.atlas-demo__dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.atlas-demo__selected {
  position: absolute;
  bottom: 14px;
  right: 14px;
  max-width: 260px;
  overflow: hidden;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2, var(--vp-c-divider));
  border-radius: 8px;
  background: var(--color-bg-1, var(--vp-c-bg));
  box-shadow: 0 2px 10px rgb(0 0 0 / 6%);
  color: var(--color-text-1, var(--vp-c-text-1));
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
