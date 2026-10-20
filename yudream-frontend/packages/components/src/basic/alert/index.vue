<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import type { AlertVariants } from './alert'
import Icon from '../icon/index.vue'
import { Alert, AlertDescription, AlertTitle } from './alert'

defineOptions({
  name: 'BuiltInAlert',
})

const props = defineProps<{
  icon?: string
  title?: string
  description?: string
  variant?: AlertVariants['variant']
  class?: HTMLAttributes['class']
}>()

defineSlots<{
  /** 正文；未提供 #description 时默认插槽同样作为正文渲染 */
  default?: () => any
  description?: () => any
}>()
</script>

<template>
  <Alert
    :variant="variant"
    :class="[
      icon && 'grid-cols-[calc(var(--spacing)*4)_1fr] gap-x-3 [&>[data-slot=alert-icon]]:(size-4 translate-y-0.5 text-current)',
      props.class,
    ]"
  >
    <Icon v-if="icon" data-slot="alert-icon" :name="icon" />
    <AlertTitle v-if="title">
      {{ title }}
    </AlertTitle>
    <AlertDescription v-if="$slots.description || $slots.default || description">
      <slot name="description">
        <slot>
          {{ description }}
        </slot>
      </slot>
    </AlertDescription>
  </Alert>
</template>
