<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '#utils'

defineOptions({
  name: 'BuiltInKbd',
})

const props = withDefaults(defineProps<{
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  class?: HTMLAttributes['class']
}>(), {
  size: 'md',
})

const sizeClasses = {
  xs: 'h-4 min-w-4 px-0.5 text-[10px]',
  sm: 'h-[18px] min-w-[18px] px-1 text-[11px]',
  md: 'h-5 min-w-5 px-1 text-xs',
  lg: 'h-6 min-w-6 px-1.5 text-sm',
  xl: 'h-7 min-w-7 px-2 text-base',
} satisfies Record<NonNullable<typeof props.size>, string>
</script>

<template>
  <kbd
    :class="cn(
      'bg-muted text-muted-foreground pointer-events-none inline-flex w-fit items-center justify-center gap-1 rounded-sm font-sans font-medium select-none',
      sizeClasses[props.size],
      '[&_svg:not([class*=size-])]:size-3',
      '[[data-slot=tooltip-content]_&]:bg-background/20 [[data-slot=tooltip-content]_&]:text-background dark:[[data-slot=tooltip-content]_&]:bg-background/10',
      props.class,
    )"
  >
    <slot />
  </kbd>
</template>
