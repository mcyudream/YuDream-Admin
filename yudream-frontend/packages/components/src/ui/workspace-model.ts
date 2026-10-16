/**
 * 工作区模型：多标签与外壳面板的纯状态计算。
 *
 * 只表达「标签集合 → 下一个激活标签」「方向键步进」这两件可测的事，
 * DOM 滚动、焦点与分栏尺寸由组件负责，便于单测覆盖关闭/切换的边界。
 */

/** 工作区标签：一个 key 对应一个可打开的目标（如 Wiki 页面），dirty 只影响展示不改标题。 */
export interface YdWorkspaceTab {
  key: string
  label: string
  /** 图标名（Morphicons 优先），有图标时显示在标题左侧 */
  icon?: string
  /** 未保存标记：标签显示脏点，不使用颜色表示未读 */
  dirty?: boolean
  /** 是否可关闭，默认 true；false 时无关闭按钮且忽略中键与 Delete */
  closable?: boolean
  /** 悬停提示，默认取 label */
  title?: string
}

/** 活动栏条目：只做视图切换，不承载业务动作。 */
export interface YdWorkspaceRailItem {
  value: string
  label: string
  icon: string
}

/** 分栏尺寸配置：百分比或像素由调用方决定，未传时交给分栏容器默认值。 */
export interface YdWorkspacePanelSize {
  defaultSize?: number
  minSize?: number
  maxSize?: number
  sizeUnit?: '%' | 'px'
}

/** 状态栏条目：start 左对齐、end 右对齐；tone 复用共享状态色语义。 */
export interface YdWorkspaceStatusItem {
  text: string
  icon?: string
  title?: string
  align?: 'start' | 'end'
  tone?: 'default' | 'muted' | 'success' | 'warning' | 'danger'
}

/**
 * 关闭标签后应激活的标签：关闭的不是当前标签时保持现状；
 * 关闭当前标签时优先右邻，其次左邻；全部关闭返回空串（由调用方展示空态）。
 */
export function nextWorkspaceTabKey(tabs: readonly { key: string }[], activeKey: string, closingKey: string): string {
  if (activeKey !== closingKey) {
    return activeKey
  }
  const index = tabs.findIndex(tab => tab.key === closingKey)
  if (index < 0) {
    return activeKey
  }
  const rest = tabs.filter(tab => tab.key !== closingKey)
  if (rest.length === 0) {
    return ''
  }
  return (rest[index] ?? rest[rest.length - 1] ?? rest[0]!).key
}

/** 方向键步进：不环绕，越界停在边界；当前无激活标签时落在第一个。 */
export function stepWorkspaceTabKey(tabs: readonly { key: string }[], activeKey: string, step: number): string {
  if (tabs.length === 0) {
    return ''
  }
  const index = tabs.findIndex(tab => tab.key === activeKey)
  if (index < 0) {
    return tabs[0]!.key
  }
  const next = Math.min(Math.max(index + step, 0), tabs.length - 1)
  return (tabs[next] ?? tabs[tabs.length - 1]!).key
}
