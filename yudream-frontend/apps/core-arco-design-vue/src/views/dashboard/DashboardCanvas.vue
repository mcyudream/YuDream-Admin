<script setup lang="ts">
import type { GridStack, GridStackWidget } from 'gridstack'
import type { DashboardBreakpoint, DashboardCard, DashboardLayoutItem } from '@/api/modules/system-dashboard'
import { useResizeObserver } from '@vueuse/core'
import { nextTick } from 'vue'
import { breakpointFor, CELL_HEIGHT, cloneItems, COLUMNS } from './layout'
import 'gridstack/dist/gridstack.min.css'

const props = defineProps<{
  items: DashboardLayoutItem[]
  cards: DashboardCard[]
  editing: boolean
}>()
const emit = defineEmits<{
  change: [items: DashboardLayoutItem[]]
  breakpoint: [value: DashboardBreakpoint]
}>()
const canvas = ref<HTMLElement>()
const breakpoint = ref<DashboardBreakpoint>('lg')
const ready = ref(false)
const cardMap = computed(() => new Map(props.cards.map(card => [card.code, card])))
const visible = computed(() => props.items.filter(item => item.visible && cardMap.value.has(item.cardCode)))
let grid: GridStack | undefined
let revision = 0
let applying = false
let disposed = false
const gridModule = import('gridstack')

useResizeObserver(canvas, ([entry]) => {
  if (entry.contentRect.width > 0) {
    breakpoint.value = breakpointFor(entry.contentRect.width)
  }
})
watch(breakpoint, value => emit('breakpoint', value), { immediate: true })
watch([() => props.items, () => props.cards, breakpoint, canvas], reconcile, { flush: 'post', deep: true })
watch(() => props.editing, updateInteraction)

function updateInteraction() {
  grid?.enableMove(props.editing)
  grid?.enableResize(props.editing)
}

function widget(item: DashboardLayoutItem): GridStackWidget {
  const card = cardMap.value.get(item.cardCode)!
  return {
    id: item.cardCode,
    ...item.placements[breakpoint.value],
    minW: breakpoint.value === 'xs' ? 1 : Math.min(card.minW, COLUMNS[breakpoint.value]),
    minH: card.minH,
  }
}

async function reconcile() {
  const current = ++revision
  const { GridStack } = await gridModule
  await nextTick()
  if (disposed || current !== revision || !canvas.value) {
    return
  }
  applying = true
  try {
    if (!grid) {
      grid = GridStack.init({
        auto: false,
        column: COLUMNS[breakpoint.value],
        cellHeight: CELL_HEIGHT,
        margin: 8,
        float: true,
        animate: false,
        disableDrag: !props.editing,
        disableResize: !props.editing,
        draggable: { handle: '.dashboard-card__header', cancel: 'button, input, a' },
        resizable: { handles: 'se' },
      }, canvas.value)
      grid.on('change', () => {
        if (applying || !props.editing || !grid) {
          return
        }
        const next = cloneItems(props.items)
        for (const node of grid.engine.nodes) {
          const item = next.find(item => item.cardCode === node.id)
          if (item) {
            item.placements[breakpoint.value] = { x: node.x || 0, y: node.y || 0, w: node.w || 1, h: node.h || 1 }
          }
        }
        emit('change', next)
      })
    }
    grid.batchUpdate()
    const codes = new Set(visible.value.map(item => item.cardCode))
    for (const node of [...grid.engine.nodes]) {
      if (!codes.has(node.id!)) {
        grid.removeWidget(node.el!, false, false)
      }
    }
    if (grid.getColumn() !== COLUMNS[breakpoint.value]) {
      grid.column(COLUMNS[breakpoint.value], 'none')
    }
    for (const item of visible.value) {
      if (!grid.engine.nodes.some(node => node.id === item.cardCode)) {
        const element = Array.from(canvas.value.children).find(el => el.getAttribute('data-card-code') === item.cardCode)
        if (element) {
          grid.makeWidget(element as HTMLElement, widget(item))
        }
      }
    }
    grid.load(visible.value.map(widget), false)
    grid.batchUpdate(false)
    updateInteraction()
    ready.value = true
  }
  finally {
    applying = false
  }
}

onBeforeUnmount(() => {
  disposed = true
  revision++
  grid?.destroy(false)
  grid = undefined
})
</script>

<template>
  <div ref="canvas" class="grid-stack dashboard-grid" :class="{ editing }" :data-breakpoint="breakpoint">
    <div v-for="(item, index) in visible" :key="item.cardCode" class="grid-stack-item" :data-card-code="item.cardCode">
      <slot :item="item" :card="cardMap.get(item.cardCode)!" :index="index" :ready="ready" />
    </div>
  </div>
</template>

<style scoped>
.dashboard-grid {
  min-height: 1px;
  margin: 16px -8px 0;
}

.editing {
  background-image: radial-gradient(var(--color-border-3) 1px, transparent 1px);
  background-size: 16px 16px;
  border-radius: var(--yd-surface-radius);
}

.editing :deep(.dashboard-card__header) {
  touch-action: none;
  cursor: grab;
}

.editing :deep(.dashboard-card) {
  outline: 1px dashed var(--color-border-3);
  outline-offset: -4px;
}

.grid-stack :deep(.grid-stack-placeholder > .placeholder-content) {
  background: var(--color-fill-3);
  border: 2px dashed var(--color-text-3);
  border-radius: var(--yd-surface-radius);
}

.grid-stack :deep(.ui-resizable-se) {
  right: 10px;
  bottom: 10px;
  width: 28px;
  height: 28px;
}
</style>
