<script setup lang="ts">
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'reka-ui'
import { computed } from 'vue'
import { YdButton } from '../adapters/index'

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  confirmText?: string
  cancelText?: string
  confirmVariant?: 'default' | 'destructive'
  disabled?: boolean
  loading?: boolean
}>(), { title: '确认此操作？', confirmText: '确认', cancelText: '取消', confirmVariant: 'destructive' })
const emit = defineEmits<{ confirm: [], cancel: [] }>()
const open = defineModel<boolean>('open', { default: false })
const confirmDisabled = computed(() => props.disabled || props.loading)
function confirm() {
  if (confirmDisabled.value) {
    return
  }
  emit('confirm')
  open.value = false
}
function cancel() {
  emit('cancel')
  open.value = false
}
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child :disabled="disabled">
      <slot />
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent class="text-popover-foreground p-4 border border-border rounded-md bg-popover w-72 shadow-md z-2000" :side-offset="6" @open-auto-focus="$event.preventDefault()">
        <div class="space-y-2">
          <strong class="text-sm font-semibold">{{ title }}</strong>
          <p v-if="description" class="text-sm text-muted-foreground m-0">
            {{ description }}
          </p>
        </div>
        <div class="mt-4 flex gap-2 justify-end">
          <YdButton size="sm" variant="outline" :disabled="loading" @click="cancel">
            {{ cancelText }}
          </YdButton>
          <YdButton size="sm" :variant="confirmVariant" :loading="loading" :disabled="confirmDisabled" @click="confirm">
            {{ confirmText }}
          </YdButton>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
