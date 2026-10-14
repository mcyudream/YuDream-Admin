<script setup lang="ts">
import type { CheckboxRootProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { computed, useId, watch } from 'vue'
import { cn } from '#utils'
import { Label } from '../label/label'
import { Checkbox } from './checkbox'

defineOptions({
  name: 'BuiltInCheckbox',
})

const props = defineProps<{
  id?: string
  disabled?: boolean
  class?: HTMLAttributes['class']
  itemClass?: HTMLAttributes['class']
  labelClass?: HTMLAttributes['class']
  ariaLabel?: string
  ariaLabelledby?: string
  ariaDescribedby?: string
  ariaInvalid?: boolean | 'true' | 'false' | 'grammar' | 'spelling'
}>()

const emit = defineEmits<{
  change: [value: CheckboxRootProps['modelValue'] | undefined]
}>()

const value = defineModel<CheckboxRootProps['modelValue']>()
const generatedId = useId()
const checkboxId = computed(() => props.id || generatedId)

watch(value, (newValue) => {
  emit('change', newValue)
})

function handleContainerClick() {
  if (props.disabled) {
    return
  }

  value.value = value.value !== true
}
</script>

<template>
  <div
    :data-state="value === true ? 'checked' : value === 'indeterminate' ? 'indeterminate' : 'unchecked'"
    :data-disabled="props.disabled ? '' : undefined"
    :class="
      cn(
        'flex-center-start min-w-0 min-h-9 gap-2 rounded-md border border-transparent px-2 py-1.5 transition-colors cursor-pointer hover:bg-accent/50 data-[state=checked]:border-border data-[state=checked]:bg-accent data-[state=checked]:text-accent-foreground data-[state=indeterminate]:border-border data-[state=indeterminate]:bg-accent data-[state=indeterminate]:text-accent-foreground data-[disabled]:cursor-not-allowed data-[disabled]:hover:bg-transparent',
        props.class,
      )
    "
    @click.self="handleContainerClick"
  >
    <Checkbox :id="checkboxId" v-model="value" :disabled="disabled" :class="props.itemClass" :aria-label="props.ariaLabel" :aria-labelledby="props.ariaLabelledby" :aria-describedby="props.ariaDescribedby" :aria-invalid="props.ariaInvalid" />
    <Label
      :for="checkboxId"
      :class="
        cn(
          'min-w-0 flex-1 text-sm cursor-pointer empty:hidden',
          props.disabled && 'cursor-not-allowed opacity-60',
          props.labelClass,
        )
      "
    >
      <slot />
    </Label>
  </div>
</template>
