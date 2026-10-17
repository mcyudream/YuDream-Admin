/**
 * 关联实体「跳过去定位」的内存移交：卡片点击后先把意图交给目标页，再路由跳转。
 * 应用内跳转会经过动态路由守卫，URL 查询参数在挂载时未必可读，内存移交不依赖它；
 * 深链（刷新/分享）仍可带 focusUser 查询参数，页面按「内存优先、URL 兜底」取值。
 */
export interface EntityFocusTarget {
  /** 目标实体主键（Long 语义，字符串） */
  id: string
  /** 目标页可直接用于过滤的检索词 */
  keyword?: string
}

let pending: EntityFocusTarget | null = null

export function requestEntityFocus(target: EntityFocusTarget) {
  pending = target
}

/** 读取并清空：一次跳转只生效一次，避免返回该页时重复高亮 */
export function consumeEntityFocus() {
  const target = pending
  pending = null
  return target
}
