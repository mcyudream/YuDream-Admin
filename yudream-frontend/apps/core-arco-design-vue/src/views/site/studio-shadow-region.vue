<script setup lang="ts">
// 公开站持久外壳的一个区域（页头或页脚）：已发布站点文档的区域编译为静态 HTML 渲染在 Shadow DOM 中（不执行脚本），
// 挂载站点运行时（导航数据、账号、二级菜单、音频、经验条）。只在文档变化时重建；路由切换仅更新当前链接与经验条，
// 因此所有公开页面的导航位置、数据与状态保持一致。链接按站点规则映射到宿主路由。
import type { SiteDocument } from '@/types/site-studio'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveStudioHref } from '@/utils/site-studio-links'
import { attachStudioRuntime } from '@/utils/site-studio-runtime'
import { compileStudioRegion } from '@/utils/site-studio-skin'

defineOptions({ name: 'StudioShadowRegion' })

const props = defineProps<{ document: SiteDocument, region: 'header' | 'footer' }>()

const host = ref<HTMLElement | null>(null)
const route = useRoute()
const router = useRouter()
let shadow: ShadowRoot | null = null
let detach: ((() => void) & { setCurrentPath?: (path: string) => void }) | undefined

function onClick(event: Event) {
  const mouse = event as MouseEvent
  const anchor = (mouse.target as HTMLElement | null)?.closest?.('a')
  if (!anchor || mouse.defaultPrevented || mouse.button !== 0) {
    return
  }
  const url = resolveStudioHref(anchor.getAttribute('href') || '', '/site', anchor.hasAttribute('data-yd-host-link'))
  event.preventDefault()
  if (!url || url.startsWith('#')) {
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
  const output = compileStudioRegion(props.document, props.region)
  // 输出来自已过校验的站点文档编译结果（与公开站 srcdoc 同源），不含脚本与事件属性
  shadow.innerHTML = `<style>${output.css}</style>${output.html}`
  const query = Object.fromEntries(Object.entries(route.query).flatMap(([key, value]) => typeof value === 'string' ? [[key, value]] : []))
  detach = attachStudioRuntime({ document: props.document, frameWindow: window, basePath: '/site', root: shadow, currentPath: route.path, query })
}

onMounted(() => {
  render()
  shadow?.addEventListener('click', onClick)
})
watch(() => props.document, render)
watch(() => route.path, path => detach?.setCurrentPath?.(path))
onBeforeUnmount(() => {
  detach?.()
  shadow?.removeEventListener('click', onClick)
})
</script>

<template>
  <div ref="host" class="studio-shadow-region" :data-region="region" />
</template>
