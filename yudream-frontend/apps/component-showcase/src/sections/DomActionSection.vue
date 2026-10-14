<script setup lang="ts">
import type { YdDomActionInstance } from '@yudream/components'
import { useYdDomAction } from '@yudream/components'
import { h, onBeforeUnmount, onMounted, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const paletteHost = ref<HTMLElement>()
const soundHost = ref<HTMLElement>()
const favoriteHost = ref<HTMLElement>()
const soundEnabled = ref(true)
const favorite = ref(false)
const lastAction = ref('尚未点击')
let paletteAction: YdDomActionInstance | undefined
let soundAction: YdDomActionInstance | undefined
let favoriteAction: YdDomActionInstance | undefined
const palette = [
  { id: 'strong', color: 'color-mix(in oklab, oklch(var(--primary)) 82%, oklch(var(--background)))' },
  { id: 'soft', color: 'color-mix(in oklab, oklch(var(--primary)) 62%, oklch(var(--background)))' },
  { id: 'muted', color: 'color-mix(in oklab, oklch(var(--primary)) 42%, oklch(var(--background)))' },
  { id: 'quiet', color: 'color-mix(in oklab, oklch(var(--primary)) 24%, oklch(var(--background)))' },
]

function recordAction(label: string, event: MouseEvent) {
  lastAction.value = `${label}：${(event.currentTarget as HTMLButtonElement).tagName}`
}

function toggleSound(event: MouseEvent) {
  soundEnabled.value = !soundEnabled.value
  soundAction?.update({
    pressed: soundEnabled.value,
    text: soundEnabled.value ? '♪' : '×',
    class: `yd-dom-action-demo yd-dom-action-demo--sound${soundEnabled.value ? '' : ' is-muted'}`,
  })
  recordAction('音效开关', event)
}

function toggleFavorite(event: MouseEvent) {
  favorite.value = !favorite.value
  favoriteAction?.update({
    pressed: favorite.value,
    text: favorite.value ? '★' : '☆',
    title: favorite.value ? '取消收藏' : '收藏',
    ariaLabel: favorite.value ? '取消收藏' : '收藏',
  })
  recordAction('收藏动作', event)
}

onMounted(() => {
  if (paletteHost.value) {
    paletteAction = useYdDomAction(paletteHost.value, {
      ariaLabel: '强调色选择器',
      title: '强调色',
      class: 'yd-dom-action-demo yd-dom-action-demo--palette',
      pressed: false,
      content: () => h('span', { 'class': 'yd-dom-action-demo__swatches', 'aria-hidden': 'true' }, palette.map(item => h('span', {
        key: item.id,
        class: 'yd-dom-action-demo__swatch',
        style: { backgroundColor: item.color },
      }))),
      onClick: (event) => {
        const pressed = paletteAction?.element?.getAttribute('aria-pressed') !== 'true'
        paletteAction?.update({ pressed })
        recordAction('强调色选择器', event)
      },
    })
  }
  if (soundHost.value) {
    soundAction = useYdDomAction(soundHost.value, {
      ariaLabel: '切换像素音效',
      title: '像素音效开关',
      class: 'yd-dom-action-demo yd-dom-action-demo--sound',
      pressed: soundEnabled.value,
      text: '♪',
      onClick: toggleSound,
    })
  }
  if (favoriteHost.value) {
    favoriteAction = useYdDomAction(favoriteHost.value, {
      ariaLabel: '收藏',
      title: '收藏',
      class: 'yd-dom-action-demo yd-dom-action-demo--favorite',
      pressed: favorite.value,
      text: '☆',
      onClick: toggleFavorite,
    })
  }
})

onBeforeUnmount(() => {
  paletteAction?.dispose()
  soundAction?.dispose()
  favoriteAction?.dispose()
})
</script>

<template>
  <DemoCard title="非 Vue DOM 动作" description="在外部引擎/主题运行时节点中由 YD 负责真实 button、无障碍属性、点击事件与 Vue 生命周期。图形插槽仅承载非交互内容。">
    <div class="yd-dom-action-demo-grid">
      <div class="yd-dom-action-demo-item">
        <div class="action-label"><strong>主题强调色</strong><span>外部节点挂载</span></div>
        <div ref="paletteHost" />
      </div>
      <div class="yd-dom-action-demo-item">
        <div class="action-label"><strong>音效开关</strong><span>{{ soundEnabled ? '已开启' : '已静音' }}</span></div>
        <div ref="soundHost" />
      </div>
      <div class="yd-dom-action-demo-item">
        <div class="action-label"><strong>收藏状态</strong><span>{{ favorite ? '已收藏' : '未收藏' }}</span></div>
        <div ref="favoriteHost" />
      </div>
    </div>
    <div class="yd-dom-action-demo-status" role="status" aria-live="polite">
      <div>
        <span class="status-label">最近动作</span>
        <strong>{{ lastAction }}</strong>
      </div>
      <p>动作由 YD 生成真实 button，并在组件卸载时自动销毁。</p>
    </div>
  </DemoCard>
</template>

<style>
.yd-dom-action-demo-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--yd-content-gap); }
.yd-dom-action-demo-item { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 14px; padding: 14px; background: var(--color-fill-1); border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); }
.action-label { display: grid; gap: 4px; min-width: 0; }
.action-label strong { font-size: 13px; color: var(--color-text-1); }
.action-label span { color: var(--color-text-3); font-size: 11px; }
.yd-dom-action-demo { display: inline-flex; min-width: 38px; min-height: 38px; align-items: center; justify-content: center; border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); background: var(--color-bg-2); color: var(--color-text-1); cursor: pointer; }
.yd-dom-action-demo:disabled { cursor: not-allowed; opacity: 0.55; }
.yd-dom-action-demo--sound, .yd-dom-action-demo--favorite { font-size: 18px; }
.yd-dom-action-demo--sound[aria-pressed="false"], .yd-dom-action-demo--favorite[aria-pressed="true"] { border-color: var(--color-border-3); background: var(--color-fill-2); }
.yd-dom-action-demo__swatches { display: inline-flex; gap: 3px; }
.yd-dom-action-demo__swatch { width: 7px; height: 13px; border-radius: 2px; }
.yd-dom-action-demo-status { display: flex; flex-wrap: wrap; gap: 12px 24px; align-items: center; justify-content: space-between; padding: 12px 14px; margin-top: var(--yd-content-gap); background: var(--color-bg-2); border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); }
.yd-dom-action-demo-status > div { display: grid; gap: 3px; }
.status-label { color: var(--color-text-3); font-size: 11px; }
.yd-dom-action-demo-status strong { color: var(--color-text-1); font-size: 13px; }
.yd-dom-action-demo-status p { margin: 0; color: var(--color-text-3); font-size: 12px; }
@media (width <= 760px) { .yd-dom-action-demo-grid { grid-template-columns: 1fr; } }
</style>
