import { useMediaQuery } from '@vueuse/core'

/**
 * 响应式断点组合式函数：视口宽度小于等于 breakpoint 时返回 true（视为移动端）。
 */
export function useIsMobile(breakpoint = '(max-width: 768px)') {
  return useMediaQuery(breakpoint)
}
