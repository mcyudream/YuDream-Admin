<script setup lang="ts">
import type { CheckboxRootEmits, CheckboxRootProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { Check, Minus } from '@lucide/vue'
import { reactiveOmit } from '@vueuse/core'
import { CheckboxIndicator, CheckboxRoot, useForwardPropsEmits } from 'reka-ui'
import { cn } from '#utils'

const props = defineProps<CheckboxRootProps & { class?: HTMLAttributes['class'] }>()
const emits = defineEmits<CheckboxRootEmits>()

const delegatedProps = reactiveOmit(props, 'class')

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <CheckboxRoot
    v-slot="slotProps"
    data-slot="checkbox"
    v-bind="forwarded"
    :class="
      cn('peer border-input data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground data-[state=checked]:border-primary data-[state=checked]:shadow-sm data-[state=indeterminate]:bg-primary data-[state=indeterminate]:text-primary-foreground data-[state=indeterminate]:border-primary data-[state=indeterminate]:shadow-sm focus-visible:border-ring focus-visible:ring-ring/50 aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40 aria-invalid:border-destructive size-5 shrink-0 rounded-[5px] border-2 shadow-xs transition-all duration-150 outline-none cursor-pointer active:scale-95 focus-visible:ring-[3px] disabled:cursor-not-allowed disabled:opacity-50', props.class)"
  >
    <CheckboxIndicator
      data-slot="checkbox-indicator"
      class="text-current grid transition-none place-content-center"
    >
      <slot v-bind="slotProps">
        <Minus v-if="props.modelValue === 'indeterminate'" class="size-4 stroke-[3]" />
        <Check v-else class="size-4 stroke-[3]" />
      </slot>
    </CheckboxIndicator>
  </CheckboxRoot>
</template>
