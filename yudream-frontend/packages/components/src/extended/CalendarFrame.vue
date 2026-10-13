<script setup lang="ts">
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import FaPopover from '../basic/popover/index.vue'

defineProps<{ picker?: boolean, disabled?: boolean, label: string }>()
const open = defineModel<boolean>('open', { default: false })
</script>

<template>
  <FaPopover v-if="picker" :open="open && !disabled" align="start" @update:open="open = !disabled && $event">
    <FaButton type="button" variant="outline" :disabled="disabled" class="yd-control" aria-haspopup="dialog">
      <FaIcon name="morph:calendar-days" />{{ label }}
    </FaButton>
    <template #panel>
      <slot />
    </template>
  </FaPopover>
  <slot v-else />
</template>
