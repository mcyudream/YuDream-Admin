import type { RowData } from '@tanstack/vue-table'
import type { TableColumn, TableProps } from '../basic/table/index.vue'

/** Badge 变体映射 */
export type BadgeVariant = 'default' | 'destructive' | 'secondary'

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
}

/** 状态列配置 */
export interface StatusColumn<TData extends RowData = RowData> {
  /** 状态字段 */
  statusKey: keyof TData
  /** 列标题 */
  header: string
  /** 状态映射:值 -> {label, variant} */
  statusMap: Record<string, { label: string, variant: BadgeVariant }>
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
