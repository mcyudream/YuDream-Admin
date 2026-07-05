<template>
  <div class="skin-texture-preview" :class="{ cape: isCape }">
    <template v-if="isCape">
      <span class="skin-texture-preview__pane">
        <span class="skin-texture-preview__label">正面</span>
        <span class="skin-texture-preview__cape">
          <img :src="textureUrl" alt="">
        </span>
      </span>
      <span class="skin-texture-preview__pane">
        <span class="skin-texture-preview__label">背面</span>
        <span class="skin-texture-preview__cape back">
          <img :src="textureUrl" alt="">
        </span>
      </span>
    </template>
    <template v-else>
      <span class="skin-texture-preview__pane">
        <span class="skin-texture-preview__label">正面</span>
        <SkinView3d
          ref="frontRef"
          :width="104"
          :height="138"
          :skin-url="textureUrl"
          :skin-options="skinOptions"
          :auto-rotate="false"
          :animation="animation"
          :enable-rotate="false"
          :enable-zoom="false"
          :enable-pan="false"
          :global-light="1.65"
          :camera-light="1.25"
          :zoom="0.78"
          :background="{ type: 'color', value: 0xf7f7f8 }"
        />
      </span>
      <span class="skin-texture-preview__pane">
        <span class="skin-texture-preview__label">背面</span>
        <SkinView3d
          ref="backRef"
          :width="104"
          :height="138"
          :skin-url="textureUrl"
          :skin-options="skinOptions"
          :auto-rotate="false"
          :animation="animation"
          :enable-rotate="false"
          :enable-zoom="false"
          :enable-pan="false"
          :global-light="1.65"
          :camera-light="1.25"
          :zoom="0.78"
          :background="{ type: 'color', value: 0xf7f7f8 }"
        />
      </span>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { SkinViewer } from 'skinview3d'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { SkinView3d, type SkinOptions } from 'vue-skinview3d'
import { IdleAnimation } from 'vue-skinview3d/animations'

const props = defineProps<{
  textureUrl: string
  type?: string
  model?: string
}>()

const frontRef = ref<InstanceType<typeof SkinView3d> | null>(null)
const backRef = ref<InstanceType<typeof SkinView3d> | null>(null)
const isCape = computed(() => props.type === 'cape')
const skinOptions = computed<SkinOptions>(() => ({
  model: props.model === 'slim' ? 'slim' : 'auto-detect',
}))
const animation = computed(() => new IdleAnimation())

onMounted(() => {
  void applyView()
})

watch(() => [props.textureUrl, props.model, props.type], () => {
  void applyView()
}, { flush: 'post' })

async function applyView() {
  if (isCape.value) {
    return
  }
  await nextTick()
  rotateViewer(frontRef.value, Math.PI)
  rotateViewer(backRef.value, 0)
}

function rotateViewer(component: InstanceType<typeof SkinView3d> | null, rotation: number) {
  const viewer = resolveViewer(component)
  if (!viewer) {
    return
  }
  viewer.playerWrapper.rotation.y = rotation
  viewer.controls.target.set(0, 8, 0)
  viewer.controls.update()
  viewer.render()
}

function resolveViewer(component: InstanceType<typeof SkinView3d> | null): SkinViewer | null {
  const exposed = component?.viewer as unknown
  if (!exposed) {
    return null
  }
  if (typeof exposed === 'object' && 'value' in exposed) {
    return (exposed as { value?: SkinViewer | null }).value || null
  }
  return exposed as SkinViewer
}
</script>
