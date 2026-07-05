<template>
  <div class="skin-preview" :class="{ compact }">
    <div v-if="title || controls" class="skin-preview__toolbar">
      <strong>{{ title }}</strong>
      <div v-if="controls" class="skin-preview__actions">
        <button type="button" :class="{ active: rotateEnabled }" title="旋转" @click="rotateEnabled = !rotateEnabled">
          <span class="i-ri:refresh-line" />
        </button>
        <button type="button" title="切换动作" @click="nextAnimation">
          {{ animationLabel }}
        </button>
        <button
          v-for="item in backgrounds"
          :key="item.label"
          type="button"
          class="skin-preview__swatch"
          :class="{ active: backgroundValue === item.value }"
          :style="{ backgroundColor: item.color }"
          :title="item.label"
          @click="backgroundValue = item.value"
        />
      </div>
    </div>
    <div v-if="hasPreview" class="skin-preview__stage">
      <SkinView3d
        ref="viewerRef"
        :width="width"
        :height="height"
        :skin-url="skin || undefined"
        :cape-url="cape || undefined"
        :skin-options="skinOptions"
        :cape-options="capeOptions"
        :fov="fov"
        :auto-rotate="false"
        :animation="animation"
        :enable-rotate="controls"
        :enable-zoom="controls"
        :enable-pan="false"
        :global-light="1.7"
        :camera-light="1.35"
        :zoom="zoom"
        :background="{ type: 'color', value: backgroundValue }"
      />
    </div>
    <div v-else class="skin-preview__empty">
      <span class="i-ri:t-shirt-2-line" />
      <strong>还没有外观</strong>
      <small>选择皮肤后会在这里预览</small>
    </div>
    <div v-if="$slots.default" class="skin-preview__footer">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { SkinViewer } from 'skinview3d'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { SkinView3d, type CapeOptions, type SkinOptions } from 'vue-skinview3d'
import { IdleAnimation, RunningAnimation, WalkingAnimation } from 'vue-skinview3d/animations'

const props = withDefaults(defineProps<{
  title?: string
  skin?: string
  cape?: string
  slim?: boolean
  compact?: boolean
  autoRotate?: boolean
  controls?: boolean
  view?: 'front' | 'back' | 'left' | 'right'
}>(), {
  title: '',
  skin: '',
  cape: '',
  slim: false,
  compact: false,
  autoRotate: true,
  controls: false,
  view: 'front',
})

interface PreviewBackground {
  label: string
  color: string
  value: number
}

interface RgbColor {
  r: number
  g: number
  b: number
}

const fallbackBackgrounds: PreviewBackground[] = [
  { label: '表面', color: '#f7f7f8', value: 0xf7f7f8 },
  { label: '柔和', color: '#fbfbfc', value: 0xfbfbfc },
  { label: '主题', color: '#ececef', value: 0xececef },
]
const backgrounds = ref<PreviewBackground[]>(fallbackBackgrounds)
const backgroundValue = ref(fallbackBackgrounds[0].value)
const rotateEnabled = ref(props.autoRotate)
const animationIndex = ref(0)
const viewerRef = ref<InstanceType<typeof SkinView3d> | null>(null)
let themeObserver: MutationObserver | null = null
let rotateFrame: number | null = null
let lastRotateTime = 0
const animations = [
  { label: '待机', create: () => new IdleAnimation() },
  { label: '行走', create: () => new WalkingAnimation() },
  { label: '奔跑', create: () => new RunningAnimation() },
]
const width = computed(() => props.compact ? 220 : 320)
const height = computed(() => props.compact ? 270 : 390)
const fov = computed(() => props.compact ? 48 : 46)
const zoom = computed(() => props.compact ? 0.86 : 0.84)
const rotateSpeed = computed(() => props.compact ? 0.55 : 0.42)
const verticalOffset = computed(() => props.compact ? 1.6 : 2.6)
const skinOptions = computed<SkinOptions>(() => ({
  model: props.slim ? 'slim' : 'auto-detect',
}))
const capeOptions = computed<CapeOptions>(() => ({
  backEquipment: 'cape',
}))
const animationLabel = computed(() => animations[animationIndex.value].label)
const animation = computed(() => animations[animationIndex.value].create())
const hasPreview = computed(() => !!props.skin || !!props.cape)
const viewRotation = computed(() => {
  if (props.view === 'back') {
    return 0
  }
  if (props.view === 'left') {
    return -Math.PI / 2
  }
  if (props.view === 'right') {
    return Math.PI / 2
  }
  return Math.PI
})

onMounted(() => {
  syncThemeBackgrounds()
  themeObserver = new MutationObserver(syncThemeBackgrounds)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
  if (document.body) {
    themeObserver.observe(document.body, { attributes: true, attributeFilter: ['class', 'style'] })
  }
  void applyView()
  syncRotationLoop()
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  themeObserver = null
  stopRotationLoop()
})

watch([viewRotation, rotateEnabled, () => props.skin, () => props.cape], () => {
  void applyView()
  syncRotationLoop()
}, { flush: 'post' })

watch(() => props.autoRotate, (value) => {
  rotateEnabled.value = value
})

async function applyView() {
  await nextTick()
  const viewer = resolveViewer()
  if (!viewer) {
    return
  }
  configureViewerFrame(viewer)
  viewer.controls.target.set(0, 4, 0)
  viewer.controls.update()
  if (!rotateEnabled.value) {
    viewer.playerWrapper.rotation.y = viewRotation.value
  }
  viewer.render()
}

function syncRotationLoop() {
  if (rotateEnabled.value && hasPreview.value) {
    startRotationLoop()
    return
  }
  stopRotationLoop()
}

function startRotationLoop() {
  if (rotateFrame !== null) {
    return
  }
  lastRotateTime = window.performance.now()
  const tick = (time: number) => {
    rotateFrame = null
    if (!rotateEnabled.value || !hasPreview.value) {
      return
    }
    const viewer = resolveViewer()
    if (viewer) {
      const delta = Math.min(time - lastRotateTime, 80) / 1000
      configureViewerFrame(viewer)
      viewer.playerWrapper.rotation.y += delta * rotateSpeed.value
      viewer.controls.target.set(0, 4, 0)
      viewer.controls.update()
      viewer.render()
    }
    lastRotateTime = time
    rotateFrame = window.requestAnimationFrame(tick)
  }
  rotateFrame = window.requestAnimationFrame(tick)
}

function stopRotationLoop() {
  if (rotateFrame === null) {
    return
  }
  window.cancelAnimationFrame(rotateFrame)
  rotateFrame = null
}

function configureViewerFrame(viewer: SkinViewer) {
  viewer.playerWrapper.position.y = verticalOffset.value
}

function resolveViewer(): SkinViewer | null {
  const exposed = viewerRef.value?.viewer as unknown
  if (!exposed) {
    return null
  }
  if (typeof exposed === 'object' && 'value' in exposed) {
    return (exposed as { value?: SkinViewer | null }).value || null
  }
  return exposed as SkinViewer
}

function syncThemeBackgrounds() {
  const styles = getComputedStyle(document.documentElement)
  const surface = readThemeColor(styles, '--color-bg-1', { r: 247, g: 247, b: 248 })
  const panel = readThemeColor(styles, '--color-bg-2', surface)
  const primary = readThemeColor(styles, '--primary-6', { r: 22, g: 93, b: 255 })
  const currentIndex = backgrounds.value.findIndex(item => item.value === backgroundValue.value)
  const brightenRatio = colorLuminance(surface) < 96 ? 0.1 : 0.72
  const base = mixColor(surface, { r: 255, g: 255, b: 255 }, brightenRatio)
  const nextBackgrounds = [
    toPreviewBackground('表面', base),
    toPreviewBackground('柔和', mixColor(base, panel, 0.35)),
    toPreviewBackground('主题', mixColor(base, primary, 0.04)),
  ]
  backgrounds.value = nextBackgrounds
  if (currentIndex >= 0) {
    backgroundValue.value = nextBackgrounds[Math.min(currentIndex, nextBackgrounds.length - 1)].value
  }
}

function colorLuminance(color: RgbColor): number {
  return color.r * 0.299 + color.g * 0.587 + color.b * 0.114
}

function readThemeColor(styles: CSSStyleDeclaration, name: string, fallback: RgbColor): RgbColor {
  return parseCssColor(styles.getPropertyValue(name)) || fallback
}

function parseCssColor(value: string): RgbColor | null {
  const text = value.trim()
  const hex = text.match(/^#([\da-f]{3}|[\da-f]{6})$/i)
  if (hex) {
    const full = hex[1].length === 3
      ? hex[1].split('').map(item => item + item).join('')
      : hex[1]
    return {
      r: Number.parseInt(full.slice(0, 2), 16),
      g: Number.parseInt(full.slice(2, 4), 16),
      b: Number.parseInt(full.slice(4, 6), 16),
    }
  }
  const rgb = text.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)/i) || text.match(/^(\d+),\s*(\d+),\s*(\d+)$/)
  if (!rgb) {
    return null
  }
  return {
    r: Number(rgb[1]),
    g: Number(rgb[2]),
    b: Number(rgb[3]),
  }
}

function mixColor(base: RgbColor, overlay: RgbColor, ratio: number): RgbColor {
  return {
    r: Math.round(base.r * (1 - ratio) + overlay.r * ratio),
    g: Math.round(base.g * (1 - ratio) + overlay.g * ratio),
    b: Math.round(base.b * (1 - ratio) + overlay.b * ratio),
  }
}

function toPreviewBackground(label: string, color: RgbColor): PreviewBackground {
  return {
    label,
    color: `rgb(${color.r}, ${color.g}, ${color.b})`,
    value: (color.r << 16) + (color.g << 8) + color.b,
  }
}

function nextAnimation() {
  animationIndex.value = (animationIndex.value + 1) % animations.length
}
</script>
