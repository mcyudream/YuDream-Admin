import type { Ref } from 'vue'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { resolveWikiPageRoot } from './use-wiki-site-tokens'

/** SiteChrome 页头选择器：只读取共享 chrome，不修改它。 */
const CHROME_HEADER_SELECTOR = '[data-yb-chrome="header"]'

/**
 * 公开 Wiki 的 sticky 偏移必须取真实 chrome 高度，不猜固定值：
 * - 默认主题的 SiteChrome 页头是 sticky/fixed，吸顶后会盖住 `top: 0` 的侧栏；
 * - 悬浮导航主题（如 neco）用 `--neco-page-top` 声明让位高度，由页面 `padding-top` 消费。
 * 这里把「当前真实存在的 sticky/fixed 页头底边」测量成 `--wiki-chrome-offset`，
 * CSS 侧再与 `--neco-page-top` 相加组合，两边都不重复计算同一段高度。
 */
export function useWikiChromeOffset(root: Ref<HTMLElement | null>, rootSelector: string) {
  const chromeOffset = ref(0)
  let header: HTMLElement | null = null
  let headerObserver: ResizeObserver | undefined
  let frame = 0
  let lateTimer: ReturnType<typeof setTimeout> | undefined
  const chromeTimers: Array<ReturnType<typeof setTimeout>> = []
  const chromeObservers: MutationObserver[] = []

  /** 主题就绪后 SiteChrome 会重建页面元素，优先找当前挂载中的页面根 */
  function resolveTarget(): HTMLElement | null {
    return resolveWikiPageRoot(root.value, rootSelector)
  }

  function measure() {
    frame = 0
    const target = resolveTarget()
    const nextHeader = document.querySelector<HTMLElement>(CHROME_HEADER_SELECTOR)
    if (nextHeader !== header) {
      headerObserver?.disconnect()
      headerObserver = undefined
      header = nextHeader
      if (header && typeof ResizeObserver !== 'undefined') {
        // 页头随主题/断点改变高度时同步偏移
        headerObserver = new ResizeObserver(schedule)
        headerObserver.observe(header)
      }
    }
    let offset = 0
    if (header) {
      const style = window.getComputedStyle(header)
      if (style.position === 'sticky' || style.position === 'fixed') {
        const top = Number.parseFloat(style.top)
        offset = Math.max(0, header.getBoundingClientRect().height + (Number.isFinite(top) ? top : 0))
      }
    }
    chromeOffset.value = Math.round(offset)
    target?.style.setProperty('--wiki-chrome-offset', `${chromeOffset.value}px`)
  }

  function schedule() {
    if (frame) {
      return
    }
    frame = window.requestAnimationFrame(measure)
  }

  onMounted(() => {
    measure()
    window.addEventListener('resize', schedule)
    // 主题 chrome 可能在挂载后才就位，补迟到测量并观察 chrome 子树变化
    lateTimer = setTimeout(schedule, 600)
    const observe = (target: Node, options: MutationObserverInit) => {
      const observer = new MutationObserver(schedule)
      observer.observe(target, options)
      chromeObservers.push(observer)
    }
    observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
    const chrome = document.querySelector('.site-chrome')
    if (chrome) {
      observe(chrome, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] })
    }
    chromeTimers.push(setTimeout(schedule, 2500))
  })

  onBeforeUnmount(() => {
    if (frame) {
      window.cancelAnimationFrame(frame)
    }
    if (lateTimer) {
      clearTimeout(lateTimer)
    }
    chromeTimers.forEach(timer => clearTimeout(timer))
    chromeObservers.forEach(observer => observer.disconnect())
    headerObserver?.disconnect()
    window.removeEventListener('resize', schedule)
  })

  // SiteChrome 在主题就绪后会重建页面元素，承载元素变化要重新写入偏移
  watch(root, schedule)

  return { chromeOffset, remeasure: schedule }
}
