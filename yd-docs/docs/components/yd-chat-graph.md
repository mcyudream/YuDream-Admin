<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingAiDemos from '../.vitepress/theme/components/demos/InteractiveRemainingAiDemos.vue'

const srcBasic = `<script setup>
import { YdChatGraphView } from '@yudream/components'
import { ref } from 'vue'

const graph = ref({
  query: '部署流程',
  nodes: [
    { id: 'n1', title: '部署流程', role: 'query', type: 'query' },
    { id: 'n2', title: 'Docker Compose', type: 'concept', score: 0.9, role: 'focus' },
    { id: 'n3', title: 'Nginx 配置', type: 'page', score: 0.6 },
    { id: 'n4', title: '健康检查', type: 'page', score: 0.5 },
  ],
  edges: [
    { source: 'n1', target: 'n2', weight: 0.9 },
    { source: 'n1', target: 'n3', weight: 0.6 },
    { source: 'n2', target: 'n4', weight: 0.4, signal: '引用' },
  ],
})

function onNodeSelect(node) {
  // 跳转到知识库节点详情；query / source 角色不会触发该事件
}
<\/script>

<template>
  <YdChatGraphView :graph="graph" @node-select="onNodeSelect" />
</template>`

const srcCompact = `<script setup>
import { YdChatGraphView } from '@yudream/components'

// 空窗口助手（约 420px 宽）使用紧凑模式：高度 220px、标签隐藏、力参数收窄
<\/script>

<template>
  <YdChatGraphView :graph="graph" compact />
</template>`

const props = [
  ['<code>graph</code>', '<code>YdChatGraph</code>', '—（必填）', '图谱数据，来自 <code>useYdChatStream</code> 推送的 <code>wiki-graph</code> 活动负载'],
  ['<code>compact</code>', '<code>boolean</code>', '<code>false</code>', '紧凑模式：画布高 220px（默认 300px）、隐藏节点标签、缩小符号与斥力/边长'],
]

const emits = [
  ['<code>nodeSelect</code>', '<code>(node: YdChatGraphNode)</code>', '点击图谱节点；<code>role</code> 为 <code>query</code> 或 <code>source</code> 的节点不抛出'],
]

const graphNode = [
  ['<code>id</code>', '<code>string</code>', '节点唯一 id（<strong>必须是 string</strong>，若为后端雪花 Long 则 JSON 中已是字符串）；ECharts data 的 <code>id</code> 直接使用它做点击匹配'],
  ['<code>title</code>', '<code>string</code>', '节点标题，用作 ECharts <code>name</code> 与标签文本（必填）'],
  ['<code>type</code>', '<code>string</code>', '节点类型，仅用于 tooltip 展示'],
  ['<code>role</code>', '<code>string</code>', '角色：<code>query</code>（本次查询）/<code>focus</code>（重点）等；影响颜色、大小与是否可点'],
  ['<code>score</code>', '<code>number</code>', '相关度分数，映射为节点符号大小（按最大分归一化）'],
  ['<code>path</code>', '<code>string</code>', '知识库页面路径，仅作数据携带'],
]

const graphEdge = [
  ['<code>source</code>', '<code>string</code>', '起点节点 id（必填）'],
  ['<code>target</code>', '<code>string</code>', '终点节点 id（必填）'],
  ['<code>weight</code>', '<code>number</code>', '关系权重，映射为连线宽度（按最大权重归一化，0.8~3）'],
  ['<code>signal</code>', '<code>string</code>', '关系说明文字，显示在边 tooltip 第二行'],
]
</script>

# YdChatGraph 关联图谱

基于 ECharts `graph` 力导向布局的知识库关联关系可视化：节点大小按相关度分数缩放、颜色区分 query/focus 角色与普通节点，边宽按权重缩放，支持缩放拖拽（roam + draggable）、hover 邻接高亮与边 tooltip。组件内部自行管理 echarts 实例生命周期与容器尺寸自适应。

源码：`yudream-frontend/packages/components/src/ai/YdChatGraph.vue`

## 基础用法

<Demo title="力导向图" description="真实 ECharts 图谱；可拖拽缩放并点击普通节点" :source="srcBasic">
  <ClientOnly><InteractiveRemainingAiDemos demo="graph" /></ClientOnly>
</Demo>

## 紧凑模式

<Demo title="compact" description="真实紧凑图谱；节点标签隐藏" :source="srcCompact">
  <ClientOnly><InteractiveRemainingAiDemos demo="graph-compact" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatGraph Props" :data="props" />

### Emits

<ApiTable title="YdChatGraph Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### YdChatGraphNode

声明在 `yudream-frontend/packages/components/src/ai/useYdChatStream.ts`：

<ApiTable title="YdChatGraphNode 字段" :data="graphNode" :columns="['字段', '类型', '说明']" />

### YdChatGraphEdge

<ApiTable title="YdChatGraphEdge 字段" :data="graphEdge" :columns="['字段', '类型', '说明']" />

## 渲染与生命周期

```mermaid
flowchart TD
  A[props.graph 变化] --> B{edges 非空?}
  B -- 否 --> C[渲染空态<br/>未返回关联关系<br/>并 dispose 图表]
  B -- 是 --> D[nextTick 后确保容器存在]
  D --> E{已有实例且 DOM 变了?}
  E -- 是 --> F[dispose 旧实例]
  E -- 否 --> G[新建 ResizeObserver]
  F --> G
  G --> H[echarts.init + setOption<br/>notMerge 全量替换]
  H --> I[注册 click：<br/>dataType=node 且 id 非空<br/>→ 找到节点 → 过滤角色 → emit]
  J[容器尺寸变化] --> K[chart.resize]
  L[onBeforeUnmount] --> M[disconnect Observer<br/>+ dispose]
```

- 主题色不是硬编码的：通过往容器里塞临时探针元素再读 `getComputedStyle`，把 CSS 变量（`--primary-6`、`--color-bg-1`、`--color-text-*`）解析成真实色值后传给 ECharts，深浅模式切换随 props 重渲生效。
- tooltip 使用 `renderMode: 'richText'` 并开启 `confine`，避免在气泡内溢出。
- 边 tooltip 显示「起点 - 终点」，有 `signal` 时追加第二行。

## 注意事项

- **节点 / 边的 `id`、`source`、`target` 一律使用 string**。后端知识库节点若是雪花 Long 主键，JSON 中已序列化为字符串；禁止 `Number(id)` 转换——ECharts 点击回调里也是用 `String(params.data.id)` 回来比对 `nodes` 列表。
- **没有边就没有图**：`edges` 为空时不初始化 echarts，只渲染空态文案「未返回关联关系」；`nodes` 单独存在不会画图。
- 组件依赖 `echarts`（宿主包已内置），业务侧无需单独引入或按需注册模块——组件内部 `import * as echarts from 'echarts'` 全量引入。
- 通常不直接在页面使用，而是经 `YdChatProcess` 在 `wiki-graph` 活动中渲染；直接使用时注意给外层一个确定宽度的容器（画布默认高 300px，宽度撑满父容器）。

> 相关组件：`YdChatProcess`。真实使用参考 `yudream-frontend/apps/core-arco-design-vue/src/views/platform/chat/index.vue`。
