/**
 * echarts-only 侧入口（@yudream/dataviz/echarts）：
 * 不含 d3 相关模块（useD3Graph / GraphChart / SankeyChart / forceSimulation），
 * 供只需要 ECharts 的消费方（如插件远程包）引入，避免 d3 及其类型进入
 * 消费方的 TS 编译程序（以源码发布的契约包没有 @types/d3 可依赖）。
 * 需要力导向图/桑基图时请从根入口 @yudream/dataviz 引入。
 */

// Types（types.ts 为纯接口，不依赖 d3）
export type { ChartDataset, ChartLink, ChartNode, ChartSeries, ChartTheme, ChartThemeConfig, ChartType } from './types'

// Composables（深路径引入，避免 composables/index 连带 useD3Graph）
export { useChartTheme } from './composables/useChartTheme'
export { useECharts } from './composables/useECharts'

// Components（排除 SankeyChart / GraphChart）
export { default as BarChart } from './components/BarChart.vue'
export { default as BaseChart } from './components/BaseChart.vue'
export { default as LineChart } from './components/LineChart.vue'
export { default as PieChart } from './components/PieChart.vue'
export { default as StatTile } from './components/StatTile.vue'

// Utilities（排除 forceSimulation / adaptThemeForD3）
export { adaptThemeForECharts, resolveTheme } from './utils/theme-adapters'
export { buildEChartsOption } from './utils/echarts-option-builder'
export { normalizeDataset } from './utils/transformers'
