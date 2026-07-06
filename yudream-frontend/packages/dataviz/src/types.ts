/**
 * 支持的图表类型
 */
export type ChartType =
  | 'line'
  | 'bar'
  | 'pie'
  | 'scatter'
  | 'radar'
  | 'graph'
  | 'tree'
  | 'treemap'
  | 'heatmap'
  | 'funnel'
  | 'gauge'
  | 'custom'

/**
 * 通用数据集
 */
export interface ChartDataset {
  /** 数据集唯一标识 */
  id?: string
  /** 数据集名称 */
  label?: string
  /** 维度定义 */
  dimensions?: string[]
  /** 数据行 */
  source?: Array<Record<string, unknown> | unknown[]>
  /** 关系图节点（用于 graph 类型） */
  nodes?: ChartNode[]
  /** 关系图边（用于 graph 类型） */
  links?: ChartLink[]
}

/**
 * 图表系列
 */
export interface ChartSeries {
  /** 系列类型 */
  type: ChartType
  /** 系列名称 */
  name?: string
  /** 系列数据 */
  data?: unknown[]
  /** 关联数据集 ID */
  datasetIndex?: number
  /** 额外配置 */
  [key: string]: unknown
}

/**
 * 关系图节点
 */
export interface ChartNode {
  /** 节点 ID */
  id: string
  /** 节点名称 */
  name: string
  /** 节点值 */
  value?: number | number[]
  /** 节点分类 */
  category?: string | number
  /** 节点样式 */
  itemStyle?: Record<string, unknown>
  /** 扩展属性 */
  [key: string]: unknown
}

/**
 * 关系图边
 */
export interface ChartLink {
  /** 源节点 ID */
  source: string
  /** 目标节点 ID */
  target: string
  /** 边的值 */
  value?: number
  /** 扩展属性 */
  [key: string]: unknown
}

/**
 * 图表主题
 */
export type ChartTheme = string | ChartThemeConfig

/**
 * 图表主题配置
 */
export interface ChartThemeConfig {
  /** 主题名称 */
  name?: string
  /** 主色板 */
  color?: string[]
  /** 主色板（语义化别名） */
  colors?: string[]
  /** 背景色 */
  backgroundColor?: string
  /** 背景色（语义化别名） */
  background?: string
  /** 主文本色 */
  text?: string
  /** 次要文本色 */
  textSecondary?: string
  /** 网格线颜色或样式 */
  grid?: string | Record<string, unknown>
  /** 文本样式 */
  textStyle?: Record<string, unknown>
  /** 标题样式 */
  title?: Record<string, unknown>
  /** 图例样式 */
  legend?: Record<string, unknown>
  /** 坐标轴样式 */
  axis?: Record<string, unknown>
  /** 扩展属性 */
  [key: string]: unknown
}
