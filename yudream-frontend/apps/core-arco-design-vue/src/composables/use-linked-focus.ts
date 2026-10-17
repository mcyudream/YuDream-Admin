import type { Ref } from 'vue'
import { consumeEntityFocus } from '@/components/RelatedEntity/focus'

/** 目标页行主键取值：与表格 rowKey 一致，用于在 DOM 上定位到具体行 */
export interface LinkedFocusOptions<T> {
  /** 列表数据；加载完成后变化即触发定位 */
  rows: Ref<T[]>
  /** 行主键（Long 语义字符串） */
  idOf: (row: T) => string | undefined
  /** 深链兜底的查询参数名，如 focusUser / focusDept / focusRole */
  queryKey: string
  /** 落地检索词写入：把移交/URL 带过来的关键词写进本页搜索模型 */
  applyKeyword?: (keyword: string) => void
  /** 树形列表：把嵌套行摊平用于判断目标是否在当前数据里（DOM 定位仍按 data-row-key） */
  flatten?: (rows: T[]) => T[]
}

/** 定位行标记属性与持续时间：样式由 YD 定位行令牌提供，见 adapters.css */
const HIGHLIGHT_ATTR = 'data-yd-highlight'
const HIGHLIGHT_MS = 5200

/**
 * 关联实体「带检索跳转 + 定位高亮」的落地端，目标页 setup 里调用一次即可。
 *
 * 取值顺序：内存移交（应用内跳转，不依赖动态路由守卫）→ URL 查询参数（刷新/分享深链兜底）。
 * 检索词在 setup 期写入搜索模型，列表首次加载即过滤；目标行出现后自动打定位标记并滚入可视区。
 * 用法：
 * ```ts
 * useLinkedFocus({
 *   rows,
 *   idOf: row => row.id,
 *   queryKey: 'focusRole',
 *   applyKeyword: keyword => { searchQuery.keyword = keyword },
 * })
 * ```
 */
export function useLinkedFocus<T>(options: LinkedFocusOptions<T>) {
  const route = useRoute()
  const linkFocus = consumeEntityFocus()
  const queryValue = route.query[options.queryKey]
  const focusId = linkFocus?.id ?? (typeof queryValue === 'string' ? queryValue : undefined)
  const keyword = linkFocus?.keyword
    ?? (typeof route.query.keyword === 'string' ? route.query.keyword : undefined)

  if (keyword && options.applyKeyword) {
    options.applyKeyword(keyword)
  }

  let focused = false
  watch(options.rows, async () => {
    const current = options.flatten ? options.flatten(options.rows.value) : options.rows.value
    if (!focusId || focused || !current.some(row => String(options.idOf(row) ?? '') === focusId)) {
      return
    }
    focused = true
    // 表格行可能比数据晚一拍落地：短轮询等它出现，避免高亮落空后不再重试
    for (let attempt = 0; attempt < 12; attempt++) {
      await nextTick()
      const el = document.querySelector(`.yd-table-page-layout__table tbody tr[data-row-key="${focusId}"]`)
      if (el) {
        // 用 data-* 而不是 class：行上有 :class 绑定，表格每次重渲染都会把手工类名覆盖掉
        el.setAttribute(HIGHLIGHT_ATTR, '')
        el.scrollIntoView({ block: 'center', behavior: 'smooth' })
        setTimeout(() => el.removeAttribute(HIGHLIGHT_ATTR), HIGHLIGHT_MS)
        return
      }
      await new Promise(resolve => setTimeout(resolve, 120))
    }
    focused = false
  }, { immediate: true })

  return { focusId, keyword }
}
