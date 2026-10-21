<script setup lang="ts">
// 宿主公开页嵌入站点主题页面：把承载该路由的站点页面（含页头页脚、背景、物品栏等）渲染在 Shadow DOM 中，
// 页面里的 data-yd-outlet 节点内放 <slot>，宿主页面（知识库、插件市场、插件公开页）作为插槽内容投影到该位置，
// 因此这些页面与站点菜单页同属一个主题。文档不执行脚本，行为由站点运行时提供。
import type { SiteDocument, StudioPage } from '@/types/site-studio'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { compileStudioPage, STUDIO_BASE_CSS } from '@/utils/site-studio-html'
import { resolveStudioHref } from '@/utils/site-studio-links'
import { attachStudioRuntime } from '@/utils/site-studio-runtime'

defineOptions({ name: 'StudioShadowLayout' })

const props = defineProps<{ document: SiteDocument, page: StudioPage, pageTitle?: string }>()

const host = ref<HTMLElement | null>(null)
const route = useRoute()
const router = useRouter()
let shadow: ShadowRoot | null = null
let detach: (() => void) | undefined

function onClick(event: Event) {
  const mouse = event as MouseEvent
  const anchor = (mouse.target as HTMLElement | null)?.closest?.('a')
  // 插槽内的宿主内容由宿主自身处理；这里只接管站点文档节点里的链接
  if (!anchor || mouse.defaultPrevented || mouse.button !== 0 || !anchor.closest('[data-studio-document]')) {
    return
  }
  const url = resolveStudioHref(anchor.getAttribute('href') || '', '/site', anchor.hasAttribute('data-yd-host-link'))
  event.preventDefault()
  if (!url) {
    return
  }
  if (url.startsWith('#')) {
    shadow?.getElementById(decodeURIComponent(url.slice(1)))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  if (/^https?:/i.test(url) || mouse.metaKey || mouse.ctrlKey || anchor.target === '_blank') {
    window.open(/^https?:/i.test(url) ? url : router.resolve(url).href, '_blank', 'noopener,noreferrer')
    return
  }
  void router.push(url)
}

function render() {
  if (!host.value) {
    return
  }
  shadow ??= host.value.attachShadow({ mode: 'open' })
  detach?.()
  const output = compileStudioPage(props.document, props.page.id, { includeChrome: true })
  // 来自已过校验的站点文档编译结果（与公开站 srcdoc 同源），不含脚本与事件属性
  shadow.innerHTML = `<style>${STUDIO_BASE_CSS}\n${output.css}\n[data-yd-outlet]{display:block}</style>${output.html}`
  const outlet = shadow.querySelector('[data-yd-outlet]') ?? shadow.querySelector('[data-studio-region="page"]')
  outlet?.replaceChildren(window.document.createElement('slot'))
  const query = Object.fromEntries(Object.entries(route.query).flatMap(([key, value]) => typeof value === 'string' ? [[key, value]] : []))
  detach = attachStudioRuntime({ document: props.document, page: props.page, frameWindow: window, basePath: '/site', root: shadow, currentPath: route.path, query, hostMode: true, pageTitle: props.pageTitle })
}

onMounted(() => {
  render()
  shadow?.addEventListener('click', onClick)
})
watch(() => [props.document, props.page, route.path, props.pageTitle] as const, render)
onBeforeUnmount(() => {
  detach?.()
  shadow?.removeEventListener('click', onClick)
})
</script>

<template>
  <div ref="host" class="studio-shadow-layout">
    <slot />
  </div>
</template>
