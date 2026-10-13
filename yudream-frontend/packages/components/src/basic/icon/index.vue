<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { Icon } from '@iconify/vue'
import { UseImage } from '@vueuse/components'
import { MorphIcon } from 'morphicons/vue'
import { computed, defineComponent, h, Transition } from 'vue'
import { cn } from '#utils'
import { parseMorphIconName, resolveMorphIconNode } from './morph-icons'

defineOptions({
  name: 'BuiltInIcon',
})

const props = defineProps<{
  name: string
  transition?: boolean
  class?: HTMLAttributes['class']
}>()

const morphNode = computed(() => {
  const morphName = parseMorphIconName(props.name)
  if (!morphName) {
    return undefined
  }
  const node = resolveMorphIconNode(morphName)
  if (!node && (import.meta as ImportMeta & { env?: { DEV?: boolean } }).env?.DEV) {
    console.warn(`[FaIcon] MorphIcons 未收录 "${props.name}"，已回退 Iconify 通道`)
  }
  return node
})

const outputType = computed(() => {
  if (!props.name) {
    return
  }
  const hasPathFeatures = (str: string) => {
    return /^\.{1,2}\//.test(str) || str.startsWith('/') || str.includes('/')
  }
  if (/^https?:\/\//.test(props.name) || hasPathFeatures(props.name)) {
    return 'img'
  }
  else if (morphNode.value) {
    return 'morph'
  }
  else if (/^i-[^:]+:[^:]+/.test(props.name)) {
    return 'iconify'
  }
  else if (props.name.includes(':')) {
    return 'iconify'
  }
  else {
    return 'svg'
  }
})

const iconKey = computed(() => `${outputType.value ?? 'empty'}-${props.name}`)
const iconName = computed(() => props.name.replace(/^i-([^:]+):/, '$1:'))

const IconWrapper = defineComponent({
  render() {
    const content = this.$slots.default?.()
    return props.transition
      ? h(Transition, { name: 'icon-switch' }, () => h('span', {
          key: iconKey.value,
          class: 'icon-switch-layer',
        }, content))
      : content
  },
})
</script>

<template>
  <i :class="cn('relative size-[1em] flex-inline items-center justify-center fill-current leading-[1em]', props.class)">
    <IconWrapper>
      <span v-if="outputType === 'iconify'" :key="`iconify-${name}`" class="flex shrink-0 size-inherit items-center inset-0 justify-center absolute">
        <Icon :icon="iconName" class="shrink-0 size-inherit!" />
      </span>
      <!-- morph 分支不加内层 key：名称切换时同一个 MorphIcon 实例收到新 icon prop，自动播放弹簧变形动画 -->
      <span v-else-if="outputType === 'morph'" class="flex shrink-0 size-inherit items-center inset-0 justify-center absolute">
        <MorphIcon :icon="morphNode" class="shrink-0 size-inherit!" color="currentColor" reduced-motion="user" />
      </span>
      <svg v-else-if="outputType === 'svg'" :key="`svg-${name}`" class="shrink-0 size-inherit inset-0 absolute" aria-hidden="true">
        <use :xlink:href="`./__spritemap#sprite-${name}`" />
      </svg>
      <span v-else-if="outputType === 'img'" :key="`img-${name}`" class="flex shrink-0 size-inherit items-center inset-0 justify-center absolute">
        <UseImage :src="name" class="shrink-0 size-inherit">
          <template #loading>
            <i class="i-lucide:loader-circle animate-spin size-inherit" />
          </template>
          <template #error>
            <i class="i-lucide:image-off size-inherit" />
          </template>
        </UseImage>
      </span>
    </IconWrapper>
  </i>
</template>

<style scoped>
.icon-switch-layer {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-switch-enter-active,
.icon-switch-leave-active {
  transform-origin: center;
  backface-visibility: hidden;
  transition:
    opacity 0.2s,
    transform 0.2s,
    filter 0.2s;
  will-change: opacity, transform, filter;
}

.icon-switch-enter-from,
.icon-switch-leave-to {
  opacity: 0;
  filter: blur(4px);
}

.icon-switch-enter-from {
  transform: scale(0.6);
}

.icon-switch-leave-to {
  transform: scale(0.6);
}

.icon-switch-enter-to,
.icon-switch-leave-from {
  opacity: 1;
  filter: blur(0);
  transform: scale(1);
}
</style>
