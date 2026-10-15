<script setup lang="ts">
import './extended.css'

const emit = defineEmits<{
  minimize: []
  maximize: []
  restore: []
  close: []
}>()
const state = defineModel<'normal' | 'minimized' | 'maximized' | 'closed'>({ default: 'normal' })
function setState(next: 'normal' | 'minimized' | 'maximized' | 'closed') {
  state.value = next
  if (next === 'minimized') {
    emit('minimize')
  }
  else if (next === 'maximized') {
    emit('maximize')
  }
  else if (next === 'normal') {
    emit('restore')
  }
  else {
    emit('close')
  }
}
</script>

<template>
  <section v-if="state !== 'closed'" class="yd-mockup-window yd-surface" :class="{ 'yd-mockup-window-maximized': state === 'maximized', 'yd-mockup-window-minimized': state === 'minimized' }">
    <header class="yd-mockup-window-toolbar">
      <div class="yd-mockup-window-controls">
        <button type="button" aria-label="关闭窗口" @click="setState('closed')">
          <i class="yd-close" />
        </button>
        <button type="button" aria-label="最小化窗口" @click="setState(state === 'minimized' ? 'normal' : 'minimized')">
          <i class="yd-minimize" />
        </button>
        <button type="button" aria-label="最大化或还原窗口" @click="setState(state === 'maximized' ? 'normal' : 'maximized')">
          <i class="yd-maximize" />
        </button>
      </div>
      <slot name="title" />
      <button v-if="state === 'minimized'" type="button" class="yd-mockup-window-restore" @click="setState('normal')">
        还原
      </button>
    </header>
    <div v-if="state !== 'minimized'" class="yd-mockup-window-content">
      <slot />
    </div>
  </section>
  <button v-else type="button" class="yd-mockup-window-reopen" @click="setState('normal')">
    重新打开窗口
  </button>
</template>

<style scoped>
.yd-mockup-window { overflow: hidden; }
.yd-mockup-window-maximized { width: 100%; min-height: 100%; }
.yd-mockup-window-minimized { min-height: 0; }
.yd-mockup-window-toolbar { display: flex; gap: 12px; align-items: center; min-height: 40px; padding: 8px 12px; border-bottom: 1px solid oklch(var(--border)); }
.yd-mockup-window-controls { display: flex; gap: 6px; }
.yd-mockup-window-controls button { display: grid; width: 22px; height: 22px; place-items: center; padding: 0; background: oklch(var(--muted)); border: 0; border-radius: 50%; cursor: pointer; }
.yd-mockup-window-controls button:first-child { background: oklch(var(--destructive)); }
.yd-close { width: 7px; height: 7px; background: oklch(var(--destructive-foreground)); clip-path: polygon(20% 0,50% 30%,80% 0,100% 20%,70% 50%,100% 80%,80% 100%,50% 70%,20% 100%,0 80%,30% 50%,0 20%); }
.yd-minimize { width: 8px; height: 1px; background: oklch(var(--foreground)); }
.yd-maximize { width: 7px; height: 7px; border: 1px solid oklch(var(--foreground)); }
.yd-mockup-window-restore { margin-inline-start: auto; }
.yd-mockup-window-content { min-width: 0; }
.yd-mockup-window-reopen { padding: 8px 12px; color: oklch(var(--foreground)); background: oklch(var(--muted)); border: 1px solid oklch(var(--border)); border-radius: var(--yd-control-radius); cursor: pointer; }
</style>
