<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { useTemplateRef } from 'vue'

defineOptions({ name: 'YdAction', inheritAttrs: false })
const props = withDefaults(defineProps<{
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  class?: HTMLAttributes['class']
}>(), { type: 'button' })
const buttonRef = useTemplateRef<HTMLButtonElement>('buttonRef')
defineExpose({ focus: () => buttonRef.value?.focus(), blur: () => buttonRef.value?.blur(), element: buttonRef })
</script>

<template>
  <button v-bind="$attrs" ref="buttonRef" :type="props.type" :disabled="props.disabled" class="yd-action" :class="[props.class]">
    <slot />
  </button>
</template>

<style src="./ui.css"></style>
