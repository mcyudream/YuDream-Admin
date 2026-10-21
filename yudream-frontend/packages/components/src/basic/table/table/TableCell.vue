<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '#utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()
// 长 ID/哈希/路径不可断词，table-fixed 下会溢出到相邻列；默认换行，单行列通过 cellClass 传 whitespace-nowrap。
</script>

<template>
  <td
    data-slot="table-cell"
    :class="
      cn(
        'p-2 align-middle whitespace-normal [overflow-wrap:anywhere]',
        props.class,
      )
    "
  >
    <slot />
  </td>
</template>
