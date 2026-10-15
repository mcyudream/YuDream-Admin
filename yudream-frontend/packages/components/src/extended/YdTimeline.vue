<script setup lang="ts" generic="T extends { datetime?: string | number | Date, id?: string, title?: string, description?: string, icon?: string, side?: 'start' | 'end' }">
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

const props = withDefaults(defineProps<{
  data: T[]
  merge?: boolean
  locale?: string
  direction?: 'vertical' | 'horizontal'
  compact?: boolean
}>(), { direction: 'vertical', compact: false })
function day(item: T) {
  if (item.datetime === undefined) {
    return ''
  }
  const date = new Date(item.datetime)
  return Number.isNaN(date.getTime()) ? String(item.datetime) : date.toLocaleDateString(props.locale || 'zh-CN')
}
</script>

<template>
  <ol class="yd-timeline" :class="[`yd-timeline-${direction}`, { 'yd-timeline-compact': compact }]">
    <li v-for="(item, index) in data" :key="item.id ?? index" :class="{ 'yd-timeline-item-end': item.side === 'end' }">
      <time v-if="item.datetime !== undefined" class="yd-muted">{{ merge && index > 0 && day(data[index - 1]) === day(item) ? '' : day(item) }}</time>
      <div class="yd-timeline-entry">
        <span class="yd-timeline-marker"><slot name="marker" :item="item" :index="index"><FaIcon v-if="item.icon" :name="item.icon" /></slot></span>
        <div class="yd-timeline-content">
          <slot :item="item" :index="index">
            <strong>{{ item.title }}</strong><p class="yd-muted">
              {{ item.description }}
            </p>
          </slot>
        </div>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.yd-timeline { display: grid; padding: 0; margin: 0; list-style: none; }
.yd-timeline-vertical li { display: grid; grid-template-columns: 7rem minmax(0, 1fr); gap: var(--yd-content-gap); }
time { padding-top: 3px; font-size: 12px; text-align: end; }
.yd-timeline-entry { position: relative; min-width: 0; padding: 0 0 24px 24px; border-inline-start: 1px solid oklch(var(--border)); }
.yd-timeline-marker { position: absolute; inset-inline-start: -5px; top: 5px; display: grid; place-items: center; width: 9px; height: 9px; color: oklch(var(--primary)); background: oklch(var(--background)); border-radius: 50%; box-shadow: 0 0 0 1px oklch(var(--border)); }
.yd-timeline-marker :deep(svg) { width: 14px; height: 14px; }
.yd-timeline-content { min-width: 0; }
p { margin: 8px 0 0; font-size: 0.875rem; }
.yd-timeline-vertical li:last-child .yd-timeline-entry { border-color: transparent; }
.yd-timeline-horizontal { grid-auto-flow: column; grid-auto-columns: minmax(180px, 1fr); overflow-x: auto; }
.yd-timeline-horizontal li { display: grid; grid-template-rows: auto 1fr; min-width: 0; }
.yd-timeline-horizontal .yd-timeline-entry { min-height: 100%; padding: 24px 16px 0; border-top: 1px solid oklch(var(--border)); border-inline-start: 0; }
.yd-timeline-horizontal .yd-timeline-marker { top: -5px; inset-inline-start: 16px; }
.yd-timeline-compact li { grid-template-columns: minmax(0, 1fr); }
.yd-timeline-compact time { text-align: start; }
.yd-timeline-item-end .yd-timeline-content { text-align: end; }
@media (width <= 768px) { .yd-timeline-vertical li { grid-template-columns: minmax(0, 1fr); } time { text-align: start; } }
</style>
