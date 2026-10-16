import type { Ref } from 'vue'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface WikiTocItem {
  id: string
  text: string
  level: number
}

export interface WikiTocOptions {
  /**
   * 预览重渲染回调（防抖后触发）：正文引用高亮等阅读层装饰复用同一个 MutationObserver，
   * 不再为同一棵子树挂第二个观察者；回调实现必须自带幂等跳过，避免与写装饰互相回写。
   */
  onRendered?: () => void
}

/**
 * 本页目录：只读 Markdown 预览不输出标题锚点 id，这里从渲染后的真实 DOM 生成目录并补 id。
 * 目录项只在正文有两条以上标题时占位；滚动高亮只做阅读辅助，锚点落位交给 CSS `scroll-margin-top`
 * 消费实测的 chrome 偏移，避免在这里再猜一个固定值。
 */
export function useWikiArticleToc(articleRef: Ref<HTMLElement | null>, options: WikiTocOptions = {}) {
  const items = ref<WikiTocItem[]>([])
  const activeId = ref('')
  let targets: HTMLElement[] = []
  let observer: MutationObserver | undefined
  let syncTimer: ReturnType<typeof setTimeout> | undefined
  let scrollFrame = 0
  let attached: HTMLElement | null = null

  function sync() {
    syncTimer = undefined
    const container = attached
    const headings = container ? [...container.querySelectorAll<HTMLElement>('.md-editor-preview :is(h2, h3, h4)')] : []
    const nextItems: WikiTocItem[] = []
    const nextTargets: HTMLElement[] = []
    headings.forEach((heading, index) => {
      const text = (heading.textContent || '').replace(/\s+/g, ' ').trim()
      if (!text) {
        return
      }
      const id = `wiki-section-${index + 1}`
      if (heading.id !== id) {
        heading.id = id
      }
      nextItems.push({ id, text, level: Number(heading.tagName.slice(1)) })
      nextTargets.push(heading)
    })
    const visible = nextItems.length > 1
    items.value = visible ? nextItems : []
    targets = visible ? nextTargets : []
    if (!targets.some(target => target.id === activeId.value)) {
      activeId.value = ''
    }
    options.onRendered?.()
  }

  function schedule() {
    if (syncTimer) {
      clearTimeout(syncTimer)
    }
    syncTimer = setTimeout(sync, 120)
  }

  function attach(element: HTMLElement | null) {
    observer?.disconnect()
    observer = undefined
    attached = element
    if (element) {
      // 预览内容异步渲染，订阅子树变化而不是只依赖一次同步
      observer = new MutationObserver(schedule)
      observer.observe(element, { childList: true, subtree: true })
    }
    schedule()
  }

  function syncActive() {
    scrollFrame = 0
    if (!targets.length) {
      return
    }
    // 高亮锚点取视口上三分之一下沿：纯阅读辅助，不参与任何布局偏移
    const anchor = window.innerHeight / 3
    let current = ''
    for (const target of targets) {
      if (target.getBoundingClientRect().top <= anchor) {
        current = target.id
      }
    }
    activeId.value = current
  }

  function onScroll() {
    if (scrollFrame) {
      return
    }
    scrollFrame = window.requestAnimationFrame(syncActive)
  }

  function scrollTo(item: WikiTocItem, event: MouseEvent) {
    const target = targets.find(candidate => candidate.id === item.id)
    if (!target) {
      return
    }
    event.preventDefault()
    target.scrollIntoView({ behavior: 'smooth', block: 'start' })
    // 保留可分享的锚点地址，但不触发路由滚动接管
    window.history.replaceState(window.history.state, '', `#${item.id}`)
    activeId.value = item.id
  }

  watch(articleRef, element => attach(element || null))

  onMounted(() => {
    window.addEventListener('scroll', onScroll, { passive: true })
  })

  onBeforeUnmount(() => {
    if (syncTimer) {
      clearTimeout(syncTimer)
    }
    if (scrollFrame) {
      window.cancelAnimationFrame(scrollFrame)
    }
    observer?.disconnect()
    window.removeEventListener('scroll', onScroll)
  })

  return { items, activeId, sync, scrollTo }
}
