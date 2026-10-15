<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import YdIcon from '../basic/icon/index.vue'
import './extended.css'

export interface YdFabAction {
  id: string
  label: string
  icon?: string
  ariaLabel?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<{
  actions: YdFabAction[]
  triggerIcon?: string
  triggerLabel?: string
  layout?: 'vertical' | 'flower'
  position?: 'fixed' | 'absolute'
  closeOnSelect?: boolean
}>(), {
  triggerIcon: 'morph:plus',
  triggerLabel: '打开快捷操作',
  layout: 'vertical',
  position: 'fixed',
  closeOnSelect: true,
})
const emit = defineEmits<{
  select: [action: YdFabAction]
  open: []
  close: []
}>()
const open = defineModel<boolean>('open', { default: false })
const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
const actionsId = useId()
const flowerOffsets: Record<number, { x: number, y: number }[]> = {
  1: [{ x: -82, y: -82 }],
  2: [{ x: -116, y: -32 }, { x: -32, y: -116 }],
  3: [{ x: -116, y: -24 }, { x: -92, y: -92 }, { x: -24, y: -116 }],
  4: [{ x: -116, y: -16 }, { x: -108, y: -72 }, { x: -72, y: -108 }, { x: -16, y: -116 }],
}

function setOpen(value: boolean) {
  if (open.value === value) {
    return
  }
  open.value = value
  if (value) {
    emit('open')
  }
  else {
    emit('close')
  }
}

function toggle() {
  const wasOpen = open.value
  setOpen(!wasOpen)
  if (!wasOpen) {
    void nextTick(() => root.value?.querySelector<HTMLButtonElement>('.yd-fab-action:not(:disabled)')?.focus())
  }
}

function select(action: YdFabAction) {
  if (action.disabled) {
    return
  }
  emit('select', action)
  if (props.closeOnSelect) {
    setOpen(false)
    trigger.value?.focus()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    event.stopPropagation()
    setOpen(false)
    trigger.value?.focus()
  }
}

function handlePointerdown(event: PointerEvent) {
  if (open.value && !root.value?.contains(event.target as Node)) {
    setOpen(false)
  }
}

function handleFocusout() {
  requestAnimationFrame(() => {
    if (open.value && !root.value?.contains(document.activeElement)) {
      setOpen(false)
    }
  })
}

function actionStyle(index: number) {
  if (props.layout !== 'flower' || props.actions.length > 4) {
    return undefined
  }
  const offset = flowerOffsets[props.actions.length]?.[index]
  return offset ? { transform: `translate(${offset.x}px, ${offset.y}px)` } : undefined
}

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerdown)
  document.addEventListener('keydown', handleKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerdown)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div
    ref="root"
    class="yd-fab"
    :class="[`yd-fab-${position}`, { 'yd-fab-open': open, 'yd-fab-flower': layout === 'flower' && actions.length <= 4 }]"
    @focusout="handleFocusout"
  >
    <div v-if="open" :id="actionsId" class="yd-fab-actions" :class="{ 'yd-fab-actions-flower': layout === 'flower' && actions.length <= 4 }">
      <div v-for="(action, index) in actions" :key="action.id" class="yd-fab-action-wrap" :style="actionStyle(index)">
        <span v-if="action.label && layout !== 'flower'" class="yd-fab-label">{{ action.label }}</span>
        <button
          type="button"
          class="yd-fab-action yd-focus"
          :aria-label="action.ariaLabel || action.label"
          :title="layout === 'flower' ? action.label : undefined"
          :disabled="action.disabled"
          @click="select(action)"
        >
          <slot name="action" :action="action" :index="index">
            <YdIcon v-if="action.icon" :name="action.icon" />
            <span v-else>{{ action.label }}</span>
          </slot>
        </button>
      </div>
    </div>
    <button
      ref="trigger"
      type="button"
      class="yd-fab-trigger yd-focus"
      :aria-label="open ? '关闭快捷操作' : triggerLabel"
      :aria-expanded="open"
      :aria-controls="actionsId"
      @click="toggle"
    >
      <slot v-if="open" name="close" :open="open">
        <YdIcon name="morph:x" />
      </slot>
      <slot v-else name="trigger" :open="open">
        <YdIcon :name="triggerIcon" />
      </slot>
    </button>
    <div v-if="open && $slots['main-action']" class="yd-fab-main-action">
      <slot name="main-action" :close="() => setOpen(false)" />
    </div>
  </div>
</template>

<style scoped>
.yd-fab { position: relative; z-index: 30; display: inline-flex; width: 56px; height: 56px; align-items: center; justify-content: center; }
.yd-fab-fixed { position: fixed; inset-inline-end: 24px; bottom: 24px; }
.yd-fab-absolute { position: absolute; inset-inline-end: 24px; bottom: 24px; }
.yd-fab-trigger,.yd-fab-action { position: relative; z-index: 2; display: grid; width: 56px; height: 56px; place-items: center; padding: 0; color: oklch(var(--primary-foreground)); background: oklch(var(--primary)); border: 0; border-radius: 50%; box-shadow: 0 4px 14px oklch(var(--foreground) / 18%); cursor: pointer; transition: transform 180ms ease, background-color 180ms ease, box-shadow 180ms ease; }
.yd-fab-trigger :deep(svg) { width: 24px; height: 24px; }
.yd-fab-trigger:hover,.yd-fab-action:hover:not(:disabled) { box-shadow: 0 6px 18px oklch(var(--foreground) / 24%); }
.yd-fab-main-action { position: absolute; inset-inline-end: 68px; bottom: 0; display: flex; height: 56px; align-items: center; }
.yd-fab-main-action :deep(button) { white-space: nowrap; }
.yd-fab-actions { position: absolute; inset-inline-end: 0; bottom: 68px; display: flex; flex-direction: column-reverse; gap: 12px; align-items: flex-end; }
.yd-fab-action-wrap { position: relative; display: flex; align-items: center; gap: 10px; transition: transform 180ms ease; }
.yd-fab-action { width: 48px; height: 48px; color: oklch(var(--foreground)); background: oklch(var(--background)); border: 1px solid oklch(var(--border)); }
.yd-fab-action :deep(svg) { width: 20px; height: 20px; }
.yd-fab-action:disabled { opacity: 0.5; cursor: not-allowed; }
.yd-fab-label { padding: 6px 10px; color: oklch(var(--foreground)); background: oklch(var(--background)); border: 1px solid oklch(var(--border)); border-radius: var(--yd-control-radius); box-shadow: 0 2px 8px oklch(var(--foreground) / 12%); font-size: 0.8125rem; white-space: nowrap; }
.yd-fab-actions-flower { inset: auto 0 0 auto; width: 56px; height: 56px; }
.yd-fab-actions-flower .yd-fab-action-wrap { position: absolute; inset: 0; justify-content: center; }
.yd-fab-actions-flower .yd-fab-action { width: 48px; height: 48px; }
@media (prefers-reduced-motion: reduce) { .yd-fab-trigger,.yd-fab-action,.yd-fab-action-wrap { transition-duration: 0.01ms; } }
</style>
