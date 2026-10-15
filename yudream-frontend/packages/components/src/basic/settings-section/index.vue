<script setup lang="ts">
import type { HTMLAttributes, VNode } from 'vue'
import { cn } from '#utils'

defineOptions({
  name: 'BuiltInSettingsSection',
})

const props = defineProps<{
  title?: string
  description?: string
  class?: HTMLAttributes['class']
  headerClass?: HTMLAttributes['class']
  contentClass?: HTMLAttributes['class']
  footerClass?: HTMLAttributes['class']
}>()

const slots = defineSlots<{
  title?: () => VNode
  description?: () => VNode
  default?: () => VNode
  footer?: () => VNode
}>()
</script>

<template>
  <section :class="cn('flex flex-col gap-5', props.class)">
    <header :class="cn('flex flex-col gap-1.5 border-b pb-4', props.headerClass)">
      <h3 class="text-base font-semibold leading-6">
        <slot name="title">
          {{ title }}
        </slot>
      </h3>
      <p v-if="description || slots.description" class="text-sm text-muted-foreground">
        <slot name="description">
          {{ description }}
        </slot>
      </p>
    </header>

    <div :class="cn('flex flex-col gap-6', props.contentClass)">
      <slot />
    </div>

    <footer
      v-if="slots.footer"
      :class="cn('flex flex-wrap items-center justify-end gap-2 border-t pt-4', props.footerClass)"
    >
      <slot name="footer" />
    </footer>
  </section>
</template>
