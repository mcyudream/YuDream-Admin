<script setup lang="ts">
// 公开站新文档渲染容器：srcdoc 静态 HTML + sandbox 隔离（禁脚本/表单/顶层导航），
// 站内链接经同源 contentDocument 点击桥接回宿主路由，外部链接新开标签。
// 视口模式：iframe 占满容器高度、内部滚动，文档内 position:fixed（固定顶栏/底栏/背景）按访客视口生效。
// 站点功能（音频、粒子、进入提示、当前链接、数据绑定）由宿主运行时在同源文档上实现，文档本身不执行脚本。
import type { SiteDocument, StudioPage } from '@/types/site-studio'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveStudioHref } from '@/utils/site-studio-links'
import { attachStudioRuntime, stopStudioAudio } from '@/utils/site-studio-runtime'
import { useSiteAccountActions } from './use-site-account-actions'

// basePath：站内根相对链接映射到的前缀，公开站为 /site，草稿预览为 /site-preview/{主题}
// height：容器高度，公开站默认占满视口；runtime：当前文档与页面，提供时挂载站点功能
const props = withDefaults(defineProps<{ srcdoc: string, basePath?: string, height?: string, runtime?: { document: SiteDocument, page: StudioPage, params?: Record<string, string> } }>(), { basePath: '/site', height: '100svh' })

const frame = ref<HTMLIFrameElement | null>(null)
const router = useRouter()
const route = useRoute()
const { handleAccountAction } = useSiteAccountActions()
/** 页面查询参数（如详情页 ?id=），供插件数据适配器 {参数} 取值 */
function currentQuery() {
  return Object.fromEntries(Object.entries(route.query).flatMap(([key, value]) => typeof value === 'string' ? [[key, value]] : []))
}
let cleanupDocument: (() => void) | undefined

function handleClick(event: MouseEvent) {
  const anchor = (event.target as HTMLElement | null)?.closest?.('a')
  if (!anchor || event.defaultPrevented || event.button !== 0) {
    return
  }
  const raw = anchor.getAttribute('href') || ''
  // 账号动作链接（/profile、/logout）没有宿主路由，先于路径映射转成弹窗/登出
  if (handleAccountAction(raw)) {
    event.preventDefault()
    return
  }
  const url = resolveStudioHref(raw, props.basePath, anchor.hasAttribute('data-yd-host-link'))
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
  const element = frame.value
  const document = element?.contentDocument
  const frameWindow = element?.contentWindow
  if (!element || !document || !frameWindow) {
    return
  }
  document.addEventListener('click', handleClick)
  const detachRuntime = props.runtime ? attachStudioRuntime({ ...props.runtime, frameWindow, basePath: props.basePath, samples: true, query: { ...currentQuery(), ...props.runtime.params } }) : undefined
  cleanupDocument = () => {
    document.removeEventListener('click', handleClick)
    detachRuntime?.()
  }
}

// 同一页面只换查询参数（如切换详情 ?id=）时 srcdoc 不变：重载框架文档，运行时按新参数取数
watch(() => route.query, (next, previous) => {
  if (JSON.stringify(next) !== JSON.stringify(previous) && frame.value) {
    frame.value.srcdoc = props.srcdoc
  }
})

onBeforeUnmount(() => {
  cleanupDocument?.()
  stopStudioAudio()
})
</script>

<template>
  <iframe
    ref="frame"
    class="site-studio-frame"
    title="站点内容"
    sandbox="allow-same-origin"
    :srcdoc="srcdoc"
    :style="{ height }"
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
