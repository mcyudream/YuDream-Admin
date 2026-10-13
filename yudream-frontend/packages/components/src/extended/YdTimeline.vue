<script setup lang="ts" generic="T extends { datetime: string | number | Date, id?: string, title?: string, description?: string }">
import './extended.css'

const props = defineProps<{
  data: T[]
  merge?: boolean
  locale?: string
}>()
function day(item: T) {
  const date = new Date(item.datetime)
  return Number.isNaN(date.getTime()) ? String(item.datetime) : date.toLocaleDateString(props.locale || 'zh-CN')
}
</script>

<template>
  <ol class="yd-timeline">
    <li v-for="(item, index) in data" :key="item.id ?? index">
      <time class="yd-muted">{{ merge && index > 0 && day(data[index - 1]) === day(item) ? '' : day(item) }}</time>
      <div class="yd-timeline-entry">
        <slot :item="item" :index="index">
          <strong>{{ item.title }}</strong><p class="yd-muted">
            {{ item.description }}
          </p>
        </slot>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.yd-timeline {
  padding: 0;
  margin: 0;
  list-style: none;
}

li {
  display: grid;
  grid-template-columns: 7rem minmax(0, 1fr);
  gap: var(--yd-content-gap);
}

time {
  padding-top: 3px;
  font-size: 12px;
  text-align: end;
}

.yd-timeline-entry {
  position: relative;
  padding: 0 0 24px 24px;
  border-inline-start: 1px solid oklch(var(--border));
}

.yd-timeline-entry::before {
  position: absolute;
  inset-inline-start: -5px;
  top: 5px;
  width: 9px;
  height: 9px;
  content: "";
  background: oklch(var(--primary));
  border-radius: 50%;
  box-shadow: 0 0 0 4px oklch(var(--background));
}

p {
  margin: 8px 0 0;
  font-size: 0.875rem;
}

li:last-child .yd-timeline-entry {
  border-color: transparent;
}
</style>
