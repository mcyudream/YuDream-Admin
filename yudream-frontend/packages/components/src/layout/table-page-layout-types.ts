/**
 * 表格页操作按钮配置
 */
export interface YdTablePageAction {
  /** 按钮文本 */
  label: string
  /** 图标名称 */
  icon?: string
  /** 按钮变体 */
  variant?: 'default' | 'outline' | 'destructive' | 'secondary' | 'ghost'
  /** 权限码(用于v-auth指令) */
  permission?: string
  /** 点击回调 */
  onClick: () => void
  /** 是否自动折叠(当操作过多时) */
  autoFold?: boolean
  /** 是否危险操作(红色标识) */
  danger?: boolean
  /** 是否禁用 */
  disabled?: boolean
}
