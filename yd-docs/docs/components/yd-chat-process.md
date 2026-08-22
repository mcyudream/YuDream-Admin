<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingAiDemos from '../.vitepress/theme/components/demos/InteractiveRemainingAiDemos.vue'

const srcBasic = `<script setup>
import { YdChatProcess } from '@yudream/components'
import { ref } from 'vue'

const activities = ref([
  {
    messageId: '1827364512002',
    activityType: 'wiki-retrieval',
    status: 'complete',
    title: '检索知识库',
    hits: [
      { title: '部署指南', kind: 'page', score: 0.92, excerpt: '生产环境使用 Docker Compose 部署…' },
      { title: 'Nginx 配置', kind: 'page', score: 0.87, path: '/ops/nginx' },
    ],
  },
  {
    activityType: 'wiki-progress',
    status: 'running',
    phase: '整理要点',
    content: '正在汇总检索结果并生成回答…',
  },
])

function onRetrievalSelect(hit) {
  // 跳转到命中的知识库页面
}
<\/script>

<template>
  <YdChatProcess :activities="activities" @retrieval-select="onRetrievalSelect" />
</template>`

const srcGraph = `<script setup>
import { YdChatProcess } from '@yudream/components'

const activities = [
  {
    activityType: 'wiki-graph',
    status: 'complete',
    title: '分析关联图谱',
    graph: {
      query: '部署流程',
      nodes: [
        { id: 'n1', title: '部署流程', role: 'query' },
        { id: 'n2', title: 'Docker Compose', type: 'concept', score: 0.9, role: 'focus' },
        { id: 'n3', title: 'Nginx 配置', type: 'page', score: 0.6 },
      ],
      edges: [
        { source: 'n1', target: 'n2', weight: 0.9 },
        { source: 'n2', target: 'n3', weight: 0.4, signal: '引用' },
      ],
    },
  },
]
<\/script>

<template>
  <!-- 有图谱的活动默认展开；compact 用于窄窗口 -->
  <YdChatProcess :activities="activities" compact @graph-node-select="node => console.log(node)" />
</template>`

const props = [
  ['<code>activities</code>', '<code>YdChatActivity[]</code>', '—（必填）', '活动数组，来自 <code>useYdChatStream</code> 推送的 <code>wiki-progress / wiki-retrieval / wiki-graph</code> 事件'],
  ['<code>compact</code>', '<code>boolean</code>', '<code>false</code>', '紧凑模式，透传给内部 <code>YdChatGraph</code>（图谱高度与力导参数收窄），适用于窄窗口助手'],
]

const emits = [
  ['<code>retrievalSelect</code>', '<code>(hit: YdChatRetrievalHit)</code>', '点击检索命中项'],
  ['<code>graphNodeSelect</code>', '<code>(node: YdChatGraphNode)</code>', '点击图谱节点（透传自 <code>YdChatGraph</code>）'],
]

const activity = [
  ['<code>messageId</code>', '<code>string</code>', '所属消息 id（<strong>必须是 string</strong>，后端 Long 序列化为字符串）；仅作数据携带'],
  ['<code>activityType</code>', '<code>\'wiki-progress\' | \'wiki-retrieval\' | \'wiki-graph\' | string</code>', '活动类型（必填）；无值的活动会被过滤不渲染'],
  ['<code>phase</code>', '<code>string</code>', '当前阶段，显示在标题旁的灰色小标签'],
  ['<code>status</code>', '<code>\'running\' | \'complete\' | \'error\' | \'cancelled\' | string</code>', '状态，决定图标与颜色（详见下方归一化表）'],
  ['<code>title</code>', '<code>string</code>', '步骤标题；缺省时按类型回退（检索知识库 / 分析关联图谱）或用 phase'],
  ['<code>content</code>', '<code>string</code>', '描述文字，优先于 query 显示'],
  ['<code>query</code>', '<code>string</code>', '查询语句；content 为空且非检索类活动时显示'],
  ['<code>hits</code>', '<code>YdChatRetrievalHit[]</code>', '检索命中列表，仅 <code>wiki-retrieval</code> 渲染为可点击卡片'],
  ['<code>graph</code>', '<code>YdChatGraph</code>', '关联图谱数据，仅 <code>wiki-graph</code> 渲染为 ECharts 力导向图'],
]
</script>

# YdChatProcess 处理过程

回答上方的过程时间线：把一次 AI 回复背后的活动（进度、知识库检索命中、关联图谱分析）渲染为带状态图标的步骤流。检索命中默认最多显示 3 条、可展开；`wiki-graph` 活动首次出现即自动展开为 ECharts 力导向图。

源码：`yudream-frontend/packages/components/src/ai/YdChatProcess.vue`

## 基础用法

<Demo title="过程时间线" description="真实组件；可点击本地检索命中" :source="srcBasic">
  <ClientOnly><InteractiveRemainingAiDemos demo="process" /></ClientOnly>
</Demo>

## 图谱活动

<Demo title="wiki-graph 自动展开" description="真实过程组件与自动展开的本地图谱" :source="srcGraph">
  <ClientOnly><InteractiveRemainingAiDemos demo="process-graph" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatProcess Props" :data="props" />

### Emits

<ApiTable title="YdChatProcess Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### YdChatActivity

`activities` 数组元素的类型，声明在 `yudream-frontend/packages/components/src/ai/useYdChatStream.ts` 的 `export interface YdChatActivity`：

<ApiTable title="YdChatActivity 字段" :data="activity" :columns="['字段', '类型', '说明']" />

## 状态归一化与图标

后端返回的状态字符串先 trim、转小写并把下划线替换为中划线，再归一化为四种展示态：

| 归一化状态 | 命中值示例 | 图标 | 颜色 |
| --- | --- | --- | --- |
| `complete` | complete / success / finished / done | `i-ri:checkbox-circle-line` | 成功色 |
| `error` | error / failed / failure | `i-ri:error-warning-line` | 危险色 |
| `cancelled` | cancelled / canceled / stopped | `i-ri:stop-circle-line` | 中性灰 |
| `running`（其余一切） | running 等 | `i-ri:loader-4-line`（旋转动画） | 主色 |

类型专属图标会覆盖通用状态图标：`wiki-retrieval` 用 `i-ri:search-line`，`wiki-graph` 用 `i-ri:mind-map`。

```mermaid
flowchart LR
  A[activities] --> B[过滤 activityType 为空项]
  B --> C[逐项渲染步骤行<br/>status 归一化 → 图标/颜色]
  C --> D{activityType?}
  D -- wiki-retrieval 且 hits --> E[命中卡片<br/>超 3 条可展开/收起]
  D -- wiki-graph 且 graph --> F[折叠按钮<br/>节点数·边数]
  F --> G[YdChatGraph 力导向图]
  E --> H[retrievalSelect]
  G --> I[graphNodeSelect]
```

## 注意事项

- **`messageId` 一律使用 string**。后端消息 id 是雪花 Long，JSON 中序列化为字符串；禁止 `Number(id)` 转换。
- 展开状态按「活动索引」记录在组件内部：检索命中超过 3 条才出现展开按钮；图谱活动通过深度 watch 在节点首次到达时自动置为展开。
- 命中卡片的 `key` 由 `nodeId || path || title` 加 `excerpt` 组合而成；`hit.nodeId` 若来自后端雪花 ID，同样保持 string。
- 分数显示规则：`score < 1` 时保留 2 位小数（如 `0.92`），否则取整——组件假定分数是 0~1 的相似度。
- 组件自身不带外边距容器约束，通常直接放在 `YdBubble` 的 `process` 插槽或 `YdChatMessageList` 内部使用。

> 相关组件：`YdChatGraph`、`YdChatMessageList`。真实使用参考 `yudream-frontend/apps/core-arco-design-vue/src/views/platform/chat/index.vue`。
