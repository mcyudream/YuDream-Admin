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
  { id: 'green', color: '#3c8527' },
  { id: 'red', color: '#b02e26' },
  { id: 'blue', color: '#3c44aa' },
  { id: 'gold', color: '#c8a03a' },
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
      <div>
        <p>主题强调色</p>
        <div ref="paletteHost" />
      </div>
      <div>
        <p>音效开关</p>
        <div ref="soundHost" />
      </div>
      <div>
        <p>收藏状态</p>
        <div ref="favoriteHost" />
      </div>
    </div>
    <p class="yd-dom-action-demo__status" role="status">
      {{ lastAction }}
    </p>
  </DemoCard>
</template>

<style>
.yd-dom-action-demo-grid { display: flex; flex-wrap: wrap; gap: 20px; }
.yd-dom-action-demo-grid > div { display: flex; align-items: center; gap: 10px; }
.yd-dom-action-demo-grid p, .yd-dom-action-demo__status { color: var(--color-text-2); font-size: 13px; }
.yd-dom-action-demo { display: inline-flex; min-width: 38px; min-height: 38px; align-items: center; justify-content: center; border: 1px solid var(--color-border-2); border-radius: var(--yd-control-radius); background: var(--color-bg-2); color: var(--color-text-1); cursor: pointer; }
.yd-dom-action-demo:disabled { cursor: not-allowed; opacity: 0.55; }
.yd-dom-action-demo--sound, .yd-dom-action-demo--favorite { font-size: 18px; }
.yd-dom-action-demo--sound[aria-pressed="false"], .yd-dom-action-demo--favorite[aria-pressed="true"] { border-color: var(--color-border-3); background: var(--color-fill-2); }
.yd-dom-action-demo__swatches { display: inline-flex; gap: 3px; }
.yd-dom-action-demo__swatch { width: 7px; height: 13px; border-radius: 2px; }
.yd-dom-action-demo__status { margin-top: 12px; }
</style>
