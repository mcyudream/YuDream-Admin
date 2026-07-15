<script setup lang="ts">
import type { AgentNodeTemplate } from './types'

const props = defineProps<{
  groups: Array<{ title: string, items: AgentNodeTemplate[] }>
}>()

const emit = defineEmits<{
  add: [template: AgentNodeTemplate]
}>()

const keyword = ref('')
const dragging = ref(false)

const visibleGroups = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) {
    return props.groups
  }
  return props.groups
    .map(group => ({
      ...group,
      items: group.items.filter(item => `${item.label} ${item.description} ${item.kind}`.toLowerCase().includes(query)),
    }))
    .filter(group => group.items.length)
})

function onDragStart(event: DragEvent, template: AgentNodeTemplate) {
  dragging.value = true
  event.dataTransfer?.setData('application/agent-node', JSON.stringify(template))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragEnd() {
  window.setTimeout(() => dragging.value = false, 0)
}

function add(template: AgentNodeTemplate) {
  if (!dragging.value) {
    emit('add', template)
  }
}
</script>

<template>
  <aside class="node-palette">
    <div class="palette-heading">
      <div>
        <strong>节点库</strong>
        <span>拖拽到画布或单击添加</span>
      </div>
      <FaIcon name="i-ri:apps-2-line" />
    </div>

    <div class="palette-search">
      <FaIcon name="i-ri:search-line" />
      <FaInput v-model="keyword" clearable class="w-full" placeholder="搜索节点" aria-label="搜索节点" />
    </div>

    <div class="palette-content">
      <section v-for="group in visibleGroups" :key="group.title" class="palette-group">
        <div class="group-title">
          {{ group.title }}
        </div>
        <button
          v-for="item in group.items"
          :key="item.kind"
          type="button"
          draggable="true"
          class="palette-item"
          @dragstart="onDragStart($event, item)"
          @dragend="onDragEnd"
          @click="add(item)"
        >
          <span class="palette-icon" :style="{ '--item-color': item.color }">
            <FaIcon :name="item.icon" />
          </span>
          <span class="palette-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <FaIcon class="palette-grip" name="i-ri:draggable" />
        </button>
      </section>

      <div v-if="!visibleGroups.length" class="palette-empty">
        <FaIcon name="i-ri:search-eye-line" />
        <span>没有匹配的节点</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.node-palette { display: flex; width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; flex-direction: column; border-right: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.palette-heading { display: flex; min-height: 66px; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--color-border-2); }
.palette-heading > div { display: grid; gap: 3px; }
.palette-heading strong { color: var(--color-text-1); font-size: 14px; }
.palette-heading span { color: var(--color-text-3); font-size: 11px; }
.palette-heading > :last-child { color: rgb(var(--primary-6)); font-size: 20px; }
.palette-search { display: grid; align-items: center; padding: 12px 14px 4px; grid-template-columns: 20px minmax(0, 1fr); }
.palette-search > :first-child { z-index: 1; margin-right: -24px; color: var(--color-text-3); }
.palette-search :deep(input) { padding-left: 30px; }
.palette-content { min-height: 0; padding: 8px 10px 18px; overflow: auto; flex: 1; }
.palette-group + .palette-group { margin-top: 16px; }
.group-title { padding: 0 6px 7px; color: var(--color-text-3); font-size: 11px; font-weight: 600; }
.palette-item { display: grid; width: 100%; min-height: 62px; grid-template-columns: 36px minmax(0, 1fr) 16px; align-items: center; gap: 9px; padding: 9px; border: 1px solid transparent; border-radius: 6px; color: var(--color-text-2); background: transparent; text-align: left; cursor: grab; transition: border-color 0.15s, background 0.15s, transform 0.15s; }
.palette-item:hover { border-color: var(--color-border-2); background: var(--color-fill-2); transform: translateY(-1px); }
.palette-item:active { cursor: grabbing; }
.palette-icon { display: grid; width: 34px; height: 34px; place-items: center; border: 1px solid color-mix(in srgb, var(--item-color), transparent 72%); border-radius: 6px; color: var(--item-color); background: color-mix(in srgb, var(--item-color), transparent 90%); font-size: 17px; }
.palette-copy { display: grid; min-width: 0; gap: 3px; }
.palette-copy strong { color: var(--color-text-1); font-size: 12px; }
.palette-copy small { overflow: hidden; color: var(--color-text-3); font-size: 10px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.palette-grip { color: var(--color-text-4); }
.palette-empty { display: grid; min-height: 160px; place-items: center; align-content: center; gap: 8px; color: var(--color-text-3); font-size: 12px; }
.palette-empty > :first-child { font-size: 24px; }
</style>
