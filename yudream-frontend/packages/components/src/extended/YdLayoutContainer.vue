<script setup lang="ts">
import { SplitterGroup, SplitterPanel, SplitterResizeHandle } from 'reka-ui'
import { useId } from 'vue'
import './extended.css'

export interface YdLayoutPanel {
  key: string
  defaultSize?: number
  minSize?: number
  maxSize?: number
  collapsible?: boolean
  collapsedSize?: number
  sizeUnit?: '%' | 'px'
}
withDefaults(defineProps<{
  panels: YdLayoutPanel[]
  direction?: 'horizontal' | 'vertical'
  storageKey?: string
  keyboardResizeBy?: number
  disabled?: boolean
}>(), { direction: 'horizontal', keyboardResizeBy: 5, disabled: false })
const emit = defineEmits<{
  layout: [sizes: number[]]
  resize: [key: string, size: number]
  collapse: [key: string]
  expand: [key: string]
  dragging: [active: boolean]
  error: [error: Error]
}>()
const instanceId = useId()
const storage = {
  getItem(name: string) {
    try {
      return typeof localStorage === 'undefined' ? null : localStorage.getItem(name)
    }
    catch (error) {
      emit('error', error instanceof Error ? error : new Error(String(error)))
      return null
    }
  },
  setItem(name: string, value: string) {
    try {
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(name, value)
      }
    }
    catch (error) {
      emit('error', error instanceof Error ? error : new Error(String(error)))
    }
  },
}
</script>

<template>
  <SplitterGroup class="yd-layout yd-surface" :direction="direction" :auto-save-id="storageKey ? `yd-layout:${storageKey}` : undefined" :storage="storage" :keyboard-resize-by="keyboardResizeBy" @layout="emit('layout', $event)">
    <template v-for="(panel, index) in panels" :key="panel.key">
      <SplitterPanel :id="`${storageKey || instanceId}-${panel.key}`" v-slot="state" class="yd-layout-panel" :order="index" :default-size="panel.defaultSize" :min-size="panel.minSize" :max-size="panel.maxSize" :collapsible="panel.collapsible" :collapsed-size="panel.collapsedSize" :size-unit="panel.sizeUnit" @resize="emit('resize', panel.key, $event)" @collapse="emit('collapse', panel.key)" @expand="emit('expand', panel.key)">
        <slot :name="panel.key" :panel="panel" :index="index" v-bind="state">
          <slot name="panel" :panel="panel" :index="index" v-bind="state" />
        </slot>
      </SplitterPanel>
      <SplitterResizeHandle v-if="index < panels.length - 1" class="yd-layout-handle yd-focus" :disabled="disabled" :aria-label="`调整 ${panel.key} 面板大小`" @dragging="emit('dragging', $event)">
        <span />
      </SplitterResizeHandle>
    </template>
  </SplitterGroup>
</template>

<style scoped>
.yd-layout {
  min-height: 160px;
  overflow: hidden;
}

.yd-layout-panel {
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.yd-layout-handle {
  position: relative;
  display: flex;
  flex: 0 0 9px;
  align-items: center;
  justify-content: center;
  background: oklch(var(--muted));
}

.yd-layout-handle > span {
  width: 3px;
  height: 24px;
  background: oklch(var(--muted-foreground) / 40%);
  border-radius: 999px;
  transition: background var(--yd-motion-duration-fast);
}

.yd-layout-handle[data-orientation="vertical"] > span {
  width: 24px;
  height: 3px;
}

.yd-layout-handle[data-state="hover"] > span,
.yd-layout-handle[data-state="drag"] > span,
.yd-layout-handle:focus-visible > span {
  background: oklch(var(--primary));
}

.yd-layout-handle[data-disabled] {
  opacity: 0.5;
}
</style>
