<script setup lang="ts">
// 公开站新文档渲染容器：srcdoc 静态 HTML + sandbox 隔离（禁脚本/表单/顶层导航），
// 站内链接经同源 contentDocument 点击桥接回宿主路由，外部链接新开标签；
// 高度随内容自动增长，滚动仍由宿主页面承载。
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'

// basePath：站内根相对链接映射到的前缀，公开站为 /site，草稿预览为 /site-preview/{主题}
const props = withDefaults(defineProps<{ srcdoc: string, basePath?: string }>(), { basePath: '/site' })

const frame = ref<HTMLIFrameElement | null>(null)
const router = useRouter()
let resizeObserver: ResizeObserver | undefined
let cleanupDocument: (() => void) | undefined

function resolveUrl(href: string) {
  const value = (href || '').trim()
  if (!value) {
    return ''
  }
  if (/^(?:https?:|mailto:|tel:)/i.test(value) || value.startsWith('//')) {
    return value
  }
  if (value.startsWith('#')) {
    return value
  }
  // 站点文档内的根相对路径一律映射到站点前缀，避免站内跳转落进后台
  const base = props.basePath
  if (value === base || value.startsWith(`${base}/`) || ['/wiki', '/market', '/forms'].some(prefix => value === prefix || value.startsWith(`${prefix}/`))) {
    return value
  }
  if (base !== '/site' && (value === '/site' || value.startsWith('/site/'))) {
    return `${base}${value.slice('/site'.length) || '/'}`
  }
  return `${base}${value.startsWith('/') ? value : `/${value}`}`
}

function handleClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement | null)?.closest?.('a')
  if (!anchor || event.defaultPrevented || event.button !== 0) {
    return
  }
  const raw = anchor.getAttribute('href') || ''
  const url = resolveUrl(raw)
  if (!url) {
    event.preventDefault()
    return
  }
  if (url.startsWith('#')) {
    event.preventDefault()
    const document = frame.value?.contentDocument
    const id = decodeURIComponent(url.slice(1))
    const destination = id ? document?.getElementById(id) : document?.body
    destination?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  if (/^https?:/i.test(url)) {
    event.preventDefault()
    window.open(url, '_blank', 'noopener')
    return
  }
  event.preventDefault()
  if (event.metaKey || event.ctrlKey || event.shiftKey || anchor.target === '_blank') {
    window.open(router.resolve(url).href, '_blank', 'noopener,noreferrer')
    return
  }
  void router.push(url)
}

function bindDocument() {
  cleanupDocument?.()
  cleanupDocument = undefined
  resizeObserver?.disconnect()
  const element = frame.value
  const document = element?.contentDocument
  if (!element || !document) {
    return
  }
  document.addEventListener('click', handleClick)
  cleanupDocument = () => document.removeEventListener('click', handleClick)
  const grow = () => {
    const height = Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight || 0)
    element.style.height = `${Math.max(height, 240)}px`
  }
  grow()
  resizeObserver = new ResizeObserver(grow)
  resizeObserver.observe(document.documentElement)
  if (document.body) {
    resizeObserver.observe(document.body)
  }
}

onBeforeUnmount(() => {
  cleanupDocument?.()
  resizeObserver?.disconnect()
})
</script>

<template>
  <iframe
    ref="frame"
    class="site-studio-frame"
    title="站点内容"
    sandbox="allow-same-origin"
    :srcdoc="srcdoc"
    @load="bindDocument"
  />
</template>

<style scoped>
.site-studio-frame {
  display: block;
  width: 100%;
  min-height: 240px;
  border: 0;
  background: transparent;
}
</style>
