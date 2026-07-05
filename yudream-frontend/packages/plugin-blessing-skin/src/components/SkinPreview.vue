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
        :auto-rotate="rotateEnabled"
        :auto-rotate-speed="0.8"
        :animation="animation"
        :enable-rotate="controls"
        :enable-zoom="controls"
        :enable-pan="false"
        :global-light="0.9"
        :camera-light="0.35"
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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

const backgrounds = [
  { label: '浅色', color: '#f4f7fb', value: 0xf4f7fb },
  { label: '深色', color: '#1f2937', value: 0x1f2937 },
  { label: '草地', color: '#dceccd', value: 0xdceccd },
]
const backgroundValue = ref(backgrounds[0].value)
const rotateEnabled = ref(props.autoRotate)
const animationIndex = ref(0)
const viewerRef = ref<InstanceType<typeof SkinView3d> | null>(null)
const animations = [
  { label: '待机', create: () => new IdleAnimation() },
  { label: '行走', create: () => new WalkingAnimation() },
  { label: '奔跑', create: () => new RunningAnimation() },
]
const width = computed(() => props.compact ? 220 : 320)
const height = computed(() => props.compact ? 260 : 360)
const fov = computed(() => props.compact ? 50 : 48)
const zoom = computed(() => props.compact ? 0.88 : 0.92)
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
  void applyView()
})

watch([viewRotation, rotateEnabled, () => props.skin, () => props.cape], () => {
  void applyView()
}, { flush: 'post' })

async function applyView() {
  await nextTick()
  const viewer = resolveViewer()
  if (!viewer || rotateEnabled.value) {
    return
  }
  viewer.playerWrapper.rotation.y = viewRotation.value
  viewer.controls.target.set(0, 8, 0)
  viewer.controls.update()
  viewer.render()
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

function nextAnimation() {
  animationIndex.value = (animationIndex.value + 1) % animations.length
}
</script>
