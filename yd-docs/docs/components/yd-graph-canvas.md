<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveDataGraphCanvas from '../.vitepress/theme/components/demos/InteractiveDataGraphCanvas.vue'

const srcBasic = `<script setup>
import { YdGraphCanvas } from '@yudream/components'
import { ref } from 'vue'

const nodes = [
  { id: 'platform', x: 0, y: 0, label: '平台', size: 16, color: '#2563eb' },
  { id: 'wiki', x: 2, y: 1, label: '知识库', size: 10, color: '#0891b2' },
]
const edges = [
  { source: 'platform', target: 'wiki', type: 'arrow', label: '挂载' },
]

const selectedId = ref(null)
<\/script>

<template>
  <YdGraphCanvas
    :nodes="nodes"
    :edges="edges"
    :selected-id="selectedId"
    style="height: 420px"
    @click-node="({ node, data }) => (selectedId = node)"
    @click-stage="() => (selectedId = null)"
  />
</template>`

const props = [
  ['<code>nodes</code>', '<code>YdGraphNode[]</code>', '—（必填）', '节点数组；字段与 sigma 识别的节点属性一致（<code>x/y/size/color/label/type/image/hidden/forceLabel/zIndex</code>），<code>data</code> 携带业务数据并随事件回传'],
  ['<code>edges</code>', '<code>YdGraphEdge[]</code>', '—（必填）', '边数组（<code>source/target/label/size/color/type</code> 等），内置 <code>line / arrow / doubleArrow</code> 边程序'],
  ['<code>selectedId</code>', '<code>string | null</code>', '<code>null</code>', '受控选中节点 id，选中节点以高亮环 + 强制标签呈现'],
  ['<code>settings</code>', '<code>Partial&lt;Settings&gt;</code>', '—', 'sigma 原生 settings 全量透传（如 <code>labelDensity</code>、<code>enableEdgeEvents</code>、<code>renderEdgeLabels</code>），运行时变更自动生效'],
  ['<code>nodeReducer</code>', '<code>(node, data) =&gt; Partial&lt;NodeDisplayData&gt;</code>', '<code>null</code>', '与 sigma <code>settings.nodeReducer</code> 同签名；组件先应用内置高亮，再叠加自定义结果（可用于聚类显隐过滤）'],
  ['<code>edgeReducer</code>', '<code>(edge, data) =&gt; Partial&lt;EdgeDisplayData&gt;</code>', '<code>null</code>', '同上，作用于边'],
  ['<code>highlightNeighbors</code>', '<code>boolean</code>', '<code>true</code>', '悬停节点时隐藏无关边、弱化非邻居节点并强制显示邻域标签（sigma 官方 events 示例行为）'],
  ['<code>focusRatio</code>', '<code>number</code>', '<code>0.3</code>', '<code>focus()</code> 定位节点时的相机 ratio'],
  ['<code>controls</code>', '<code>boolean</code>', '<code>true</code>', '渲染左下角内置控制条（全屏 / 放大 / 缩小 / 复位），可用 <code>#controls</code> 插槽整体覆盖'],
]

const emits = [
  ['<code>clickNode / doubleClickNode / rightClickNode / downNode / upNode</code>', '<code>(payload: YdGraphNodeEvent) =&gt; void</code>', '节点交互事件，payload 含 <code>node</code>（id）、<code>data</code>（传入的 YdGraphNode）与原始坐标 <code>event</code>'],
  ['<code>enterNode / leaveNode</code>', '<code>(payload: YdGraphNodeEvent) =&gt; void</code>', '节点悬停进出（内置邻域高亮同步触发）'],
  ['<code>clickEdge / doubleClickEdge / rightClickEdge / enterEdge / leaveEdge</code>', '<code>(payload: YdGraphEdgeEvent) =&gt; void</code>', '边交互事件；需在 <code>settings</code> 中开启 <code>enableEdgeEvents</code>'],
  ['<code>clickStage / doubleClickStage / rightClickStage</code>', '<code>(payload: YdGraphStageEvent) =&gt; void</code>', '空白区域交互事件，常用于清空选中'],
]

const exposes = [
  ['<code>reset()</code>', '相机动画复位到全图'],
  ['<code>zoomIn() / zoomOut()</code>', '相机动画缩放'],
  ['<code>focus(id)</code>', '相机动画定位到指定节点（内部使用归一化后的显示坐标）'],
  ['<code>refresh()</code>', '重新处理数据并渲染；外部状态（如聚类显隐集合）变化后需手动调用'],
  ['<code>resize()</code>', '重算容器尺寸（组件已内置 ResizeObserver，一般无需调用）'],
  ['<code>getCamera() / getSigma() / getGraph()</code>', '获取 sigma 相机 / Sigma 实例 / graphology 图实例，用于高级定制'],
]

const slots = [
  ['<code>overlay</code>', '画布覆盖层（绝对定位于容器内），用于放置搜索框、说明面板、聚类筛选等自定义图层'],
  ['<code>controls</code>', '自定义左下角控制条，插槽参数 <code>{ zoomIn, zoomOut, reset, toggleFullscreen, isFullscreen }</code>'],
]
</script>

# YdGraphCanvas 关系图谱画布

基于 [Sigma.js](https://www.sigmajs.org/docs/)（WebGL）+ graphology 的关系图谱画布，对齐官方 demo 的渲染与交互：聚类着色、悬停邻域高亮、悬停信息卡、标签密度控制、内置缩放控制条。

## 基础用法

<Demo title="聚类知识地图" description="悬停查看邻域高亮与信息卡，点击节点选中，右上角搜索可定位节点；左下角为内置控制条" :source="srcBasic">
  <ClientOnly><InteractiveDataGraphCanvas /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdGraphCanvas Props" :data="props" />

### Emits

<ApiTable title="YdGraphCanvas Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Exposes

<ApiTable title="YdGraphCanvas Exposes" :data="exposes" :columns="['方法', '说明']" />

### Slots

<ApiTable title="YdGraphCanvas Slots" :data="slots" :columns="['插槽', '说明']" />

## 注意事项

- **坐标归一化**：sigma 渲染前会把节点坐标归一化到 <code>[0,1]</code> 区间，相机状态同样处于归一化空间。需要编程定位节点时请使用暴露的 <code>focus(id)</code>（内部走 <code>getNodeDisplayData</code> 的显示坐标），不要直接拿节点原始 <code>x/y</code> 驱动 <code>camera.animate</code>。
- **长 ID**：节点 / 边 id 一律使用字符串，禁止 <code>Number(id)</code> 转换雪花 ID。
- **自定义 reducer 的响应式**：<code>nodeReducer</code> 闭包读取的外部状态（如隐藏聚类集合）变化后，需调用 <code>refresh()</code> 触发重算（sigma 只监听图数据与内置交互）。
- **图片节点**：默认节点程序为 <code>image</code>（<code>@sigma/node-image</code>），节点传 <code>image</code> 地址显示圆形图片，缺省退化为彩色圆盘。
- 完整可运行示例见主前端仓 <code>apps/component-showcase</code> 的「数据展示」区。
