<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '#utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
</script>

<template>
  <div
    data-slot="card-content"
    :class="cn('min-w-0 break-words px-6', props.class)"
  >
    <slot />
  </div>
</template>
