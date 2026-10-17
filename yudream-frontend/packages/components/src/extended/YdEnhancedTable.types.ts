import type { RowData } from '@tanstack/vue-table'
import type { TableColumn, TableProps } from '../basic/table/index.vue'

/** Badge 变体映射 */
export type BadgeVariant = 'default' | 'destructive' | 'secondary'

/**
 * 状态语义色：按 --yd-status-* 令牌渲染淡底状态胶囊，便于在长列表里一眼扫出异常行。
 * neutral 用弱化文字色，适合"已停用/未分配"这类中性状态。
 */
export type StatusTone = 'info' | 'success' | 'warning' | 'danger' | 'neutral'

/** 状态呈现配置 */
export interface StatusPresentation {
  label: string
  variant: BadgeVariant
  /** 传则改用语义色胶囊（忽略 variant 的底色），不传保持原 Badge 外观 */
  tone?: StatusTone
}

/** 用户信息列配置 */
export interface UserColumn<TData extends RowData = RowData> {
  /** 用户名字段 */
  nameKey: keyof TData
  /** 用户邮箱字段(可选) */
  emailKey?: keyof TData
  /** 用户头像字段(可选) */
  avatarKey?: keyof TData
  /** 列标题 */
  header: string
  /** 列宽度 */
  width?: number | string
  /** 列固定位置；默认固定在左侧，传 false 取消固定，传 'right' 固定在右侧 */
  fixed?: boolean | 'left' | 'right'
}

/** 状态列配置 */
export interface StatusColumn<TData extends RowData = RowData> {
  /** 状态字段 */
  statusKey: keyof TData
  /** 列标题 */
  header: string
  /** 状态映射:值 -> {label, variant, tone?} */
  statusMap: Record<string, StatusPresentation>
  /** 列宽度 */
  width?: number | string
}

/** 操作菜单项 */
export interface ActionMenuItem<TData extends RowData = RowData> {
  /** 菜单项标签，可传函数按行动态计算（如「启用 / 停用」） */
  label: string | ((row: TData) => string)
  /** 图标 */
  icon?: string
  /** 是否危险操作 */
  danger?: boolean
  /** 是否禁用 */
  disabled?: boolean | ((row: TData) => boolean)
  /** 行级可见性；返回 false 时该菜单项不显示 */
  visible?: (row: TData) => boolean
  /** 点击回调 */
  onClick: (row: TData) => void
}

/** 增强表格属性 */
export interface EnhancedTableProps<TData extends RowData = RowData> extends Omit<TableProps<TData>, 'columns'> {
  /** 用户信息列(可选) */
  userColumn?: UserColumn<TData>
  /** 状态列(可选) */
  statusColumn?: StatusColumn<TData>
  /** 普通列 */
  columns?: TableColumn<TData, any>[]
  /** 操作菜单 */
  actions?: ActionMenuItem<TData>[]
  /** 是否显示操作列 */
  showActions?: boolean
  /** 操作列标题 */
  actionsHeader?: string
  /** 操作列宽度 */
  actionsWidth?: number | string
}
