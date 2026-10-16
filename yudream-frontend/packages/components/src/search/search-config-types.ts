/**
 * 控件类型枚举(与后端ControlType保持一致)
 */
export enum ControlType {
  AUTO = 'AUTO',
  TEXT = 'TEXT',
  SELECT = 'SELECT',
  DATE_RANGE = 'DATE_RANGE',
  NUMBER_RANGE = 'NUMBER_RANGE',
  CHECKBOX = 'CHECKBOX',
  RADIO = 'RADIO',
}

/**
 * 搜索字段配置
 */
export interface SearchFieldConfig {
  /** 字段名称(Java字段名) */
  fieldName: string
  /** 显示标签 */
  label: string
  /** 输入框占位符 */
  placeholder?: string
  /** 控件类型 */
  controlType: ControlType
  /** 显示顺序 */
  order: number
  /** 是否在折叠时显示 */
  showWhenFolded: boolean
  /** 控件宽度 */
  width: string
  /** 分组名称 */
  group?: string
  /** 选项数据源标识 */
  optionsSource?: string
  /** 提示信息 */
  hint?: string
  /** 字段类型(用于前端类型推断) */
  fieldType?: string
}

/**
 * 搜索配置
 */
export interface SearchConfig {
  /** 字段配置列表(已按order排序) */
  fields: SearchFieldConfig[]
  /** 查询类的全限定名 */
  queryClassName?: string
  /** 查询类的简单名称 */
  querySimpleName?: string
}

/**
 * 选项配置
 */
export interface SearchOption {
  label: string
  value: string | number // 移除boolean,因为AcceptableValue不包括false
  disabled?: boolean
}

/**
 * 选项数据源定义
 * 用于SELECT/CHECKBOX/RADIO类型的选项加载
 */
export interface SearchOptionsSource {
  /** 数据源标识(对应optionsSource) */
  id: string
  /** 获取选项列表的函数 */
  loader: () => Promise<SearchOption[]> | SearchOption[]
}
