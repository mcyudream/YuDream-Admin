<script setup lang="ts">
import type { YdSortableTreeNode as NodeData } from './sortable-tree-model'

defineOptions({ name: 'YdSortableTreeNode', inheritAttrs: false })

defineProps<{
  node: NodeData
  depth: number
  selectedKey?: string
  focusedKey?: string
  expandedKeys: string[]
  draggable: boolean
  loadingKeys: string[]
  inheritedDisabled?: boolean
}>()
const emit = defineEmits<{
  select: [node: NodeData]
  toggle: [node: NodeData]
  dragStart: [node: NodeData, event: DragEvent]
  dragOver: [node: NodeData, event: DragEvent]
  drop: [node: NodeData, event: DragEvent]
  dragEnd: []
  focus: [node: NodeData]
}>()
defineSlots<{
  title?: (props: { node: NodeData, depth: number, expanded: boolean, selected: boolean, disabled: boolean, loading: boolean }) => unknown
}>()
function relayDragStart(node: NodeData, event: DragEvent) {
  emit('dragStart', node, event)
}
function relayDragOver(node: NodeData, event: DragEvent) {
  emit('dragOver', node, event)
}
function relayDrop(node: NodeData, event: DragEvent) {
  emit('drop', node, event)
}
</script>

<template>
  <li role="none" class="yd-sortable-tree__item">
    <div
      role="treeitem"
      class="yd-sortable-tree__row"
      :class="{ 'yd-sortable-tree__row--selected': selectedKey === node.key, 'yd-sortable-tree__row--disabled': inheritedDisabled || node.disabled }"
      :data-node-key="node.key"
      :aria-level="depth"
      :aria-expanded="node.children?.length || node.lazy ? expandedKeys.includes(node.key) : undefined"
      :aria-selected="selectedKey === node.key"
      :aria-disabled="inheritedDisabled || node.disabled || undefined"
      :aria-busy="loadingKeys.includes(node.key) || undefined"
      :tabindex="!inheritedDisabled && !node.disabled && focusedKey === node.key ? 0 : -1"
      :draggable="draggable && !inheritedDisabled && !node.disabled"
      @click="emit('select', node)"
      @focus="emit('focus', node)"
      @dragstart="emit('dragStart', node, $event)"
      @dragover="emit('dragOver', node, $event)"
      @drop="emit('drop', node, $event)"
      @dragend="emit('dragEnd')"
    >
      <span class="yd-sortable-tree__indent" :style="{ '--yd-tree-depth': depth - 1 }" aria-hidden="true" />
      <span class="yd-sortable-tree__chevron" :class="{ 'yd-sortable-tree__chevron--open': expandedKeys.includes(node.key) }" aria-hidden="true">
        <span v-if="loadingKeys.includes(node.key)" class="yd-sortable-tree__spinner" />
        <span v-else-if="node.children?.length || node.lazy" @click.stop="emit('toggle', node)">›</span>
      </span>
      <span class="yd-sortable-tree__title">
        <slot name="title" :node="node" :depth="depth" :expanded="expandedKeys.includes(node.key)" :selected="selectedKey === node.key" :disabled="inheritedDisabled || Boolean(node.disabled)" :loading="loadingKeys.includes(node.key)">
          {{ node.label }}
        </slot>
      </span>
    </div>
    <ul v-if="expandedKeys.includes(node.key) && node.children?.length" role="group" class="yd-sortable-tree__group">
      <YdSortableTreeNode
        v-for="child in node.children"
        :key="child.key"
        :node="child"
        :depth="depth + 1"
        :selected-key="selectedKey"
        :focused-key="focusedKey"
        :expanded-keys="expandedKeys"
        :draggable="draggable"
        :loading-keys="loadingKeys"
        :inherited-disabled="inheritedDisabled || node.disabled"
        @select="emit('select', $event)"
        @toggle="emit('toggle', $event)"
        @drag-start="relayDragStart"
        @drag-over="relayDragOver"
        @drop="relayDrop"
        @drag-end="emit('dragEnd')"
        @focus="emit('focus', $event)"
      >
        <template #title="slotProps">
          <slot name="title" v-bind="slotProps" />
        </template>
      </YdSortableTreeNode>
    </ul>
  </li>
</template>
