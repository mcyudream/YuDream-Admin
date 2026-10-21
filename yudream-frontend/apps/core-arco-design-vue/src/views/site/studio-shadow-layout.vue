<script setup lang="ts">
// 公开站统一页面渲染：站点页面与承载宿主公开页的主题页都在这里渲染到 Shadow DOM（同一视口滚动、同一入场过渡）。
// 有默认插槽时为承载模式：data-yd-outlet 节点内放 <slot>，宿主页面（知识库、插件市场、插件公开页、登录）作为插槽内容投影，
// 与菜单页同属一个主题。chrome 为 true 时连同页头/页脚一起渲染（无持久外壳时）；否则页头页脚由应用级持久外壳提供。
// 文档不执行脚本，行为由站点运行时提供。
import type { SiteDocument, StudioPage } from '@/types/site-studio'
import { onBeforeUnmount, onMounted, ref, useSlots, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { compileStudioPage, STUDIO_BASE_CSS } from '@/utils/site-studio-html'
import { resolveStudioHref } from '@/utils/site-studio-links'
import { attachStudioRuntime } from '@/utils/site-studio-runtime'

defineOptions({ name: 'StudioShadowLayout' })

const props = withDefaults(defineProps<{ document: SiteDocument, page: StudioPage, pageTitle?: string, chrome?: boolean, params?: Record<string, string> }>(), { chrome: false })
const slots = useSlots()

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
  const hosting = Boolean(slots.default)
  const output = compileStudioPage(props.document, props.page.id, { includeChrome: props.chrome })
  // 来自已过校验的站点文档编译结果（与公开站 srcdoc 同源），不含脚本与事件属性
  shadow.innerHTML = `<style>${STUDIO_BASE_CSS}\n${output.css}\n[data-yd-outlet]{display:block}</style>${output.html}`
  if (hosting) {
    const outlet = shadow.querySelector('[data-yd-outlet]') ?? shadow.querySelector('[data-studio-region="page"]')
    outlet?.replaceChildren(window.document.createElement('slot'))
  }
  const query = Object.fromEntries(Object.entries(route.query).flatMap(([key, value]) => typeof value === 'string' ? [[key, value]] : []))
  detach = attachStudioRuntime({ document: props.document, page: props.page, frameWindow: window, basePath: '/site', root: shadow, currentPath: route.path, query: { ...query, ...props.params }, hostMode: hosting, pageTitle: props.pageTitle })
  // 统一入场过渡（站点页与宿主页一致）；系统减少动态效果时跳过
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    host.value.animate([{ opacity: 0, transform: 'translateY(10px)' }, { opacity: 1, transform: 'none' }], { duration: 320, easing: 'cubic-bezier(.22,.61,.36,1)' })
  }
}

onMounted(() => {
  render()
  shadow?.addEventListener('click', onClick)
})
watch(() => [props.document, props.page, route.fullPath, props.pageTitle, props.chrome] as const, render)
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
