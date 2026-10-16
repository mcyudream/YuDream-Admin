<script setup lang="ts">
import type { YdSortableTreeNode as NodeData, YdSortableTreeDropPosition } from './sortable-tree-model'
import { computed } from 'vue'
import Icon from '../basic/icon/index.vue'

defineOptions({ name: 'YdSortableTreeNode', inheritAttrs: false })

const props = defineProps<{
  node: NodeData
  depth: number
  selectedKey?: string
  focusedKey?: string
  expandedKeys: string[]
  draggable: boolean
  loadingKeys: string[]
  inheritedDisabled?: boolean
  /** 当前拖拽提案的落点节点与位置（整棵树共享同一份提案） */
  dropKey?: string
  dropPosition?: YdSortableTreeDropPosition
  /** 正在被拖拽的节点 key */
  draggingKey?: string
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
const isBranch = computed(() => Boolean(props.node.children?.length) || Boolean(props.node.lazy))
const isExpanded = computed(() => props.expandedKeys.includes(props.node.key))
const isDisabled = computed(() => Boolean(props.inheritedDisabled) || Boolean(props.node.disabled))
const dropHint = computed(() => props.dropKey === props.node.key
  ? props.dropPosition === 0 ? 'into' : props.dropPosition === -1 ? 'before' : 'after'
  : undefined)
</script>

<template>
  <li role="none" class="yd-sortable-tree__item">
    <div
      role="treeitem"
      class="yd-sortable-tree__row"
      :class="{
        'yd-sortable-tree__row--selected': selectedKey === node.key,
        'yd-sortable-tree__row--disabled': isDisabled,
        'yd-sortable-tree__row--dragging': draggingKey === node.key,
        'yd-sortable-tree__row--drop-before': dropHint === 'before',
        'yd-sortable-tree__row--drop-into': dropHint === 'into',
        'yd-sortable-tree__row--drop-after': dropHint === 'after',
      }"
      :data-node-key="node.key"
      :style="{ '--yd-tree-depth': depth - 1 }"
      :aria-level="depth"
      :aria-expanded="isBranch ? isExpanded : undefined"
      :aria-selected="selectedKey === node.key"
      :aria-disabled="isDisabled || undefined"
      :aria-busy="loadingKeys.includes(node.key) || undefined"
      :tabindex="!isDisabled && focusedKey === node.key ? 0 : -1"
      :draggable="draggable && !isDisabled"
      @click="emit('select', node)"
      @focus="emit('focus', node)"
      @dragstart="emit('dragStart', node, $event)"
      @dragover="emit('dragOver', node, $event)"
      @drop="emit('drop', node, $event)"
      @dragend="emit('dragEnd')"
    >
      <span class="yd-sortable-tree__indent" aria-hidden="true" />
      <span class="yd-sortable-tree__chevron">
        <span v-if="loadingKeys.includes(node.key)" class="yd-sortable-tree__spinner" />
        <button
          v-else-if="isBranch"
          type="button"
          class="yd-sortable-tree__toggle"
          :aria-label="isExpanded ? `收起 ${node.label}` : `展开 ${node.label}`"
          :tabindex="-1"
          @click.stop="emit('toggle', node)"
        >
          <Icon name="morph:chevron-right" :class="isExpanded ? 'yd-sortable-tree__toggle--open' : ''" />
        </button>
      </span>
      <span class="yd-sortable-tree__leading" aria-hidden="true">
        <Icon :name="isBranch ? (isExpanded ? 'morph:folder-open' : 'morph:folder') : 'morph:file-text'" />
      </span>
      <span class="yd-sortable-tree__title">
        <slot name="title" :node="node" :depth="depth" :expanded="isExpanded" :selected="selectedKey === node.key" :disabled="isDisabled" :loading="loadingKeys.includes(node.key)">
          {{ node.label }}
        </slot>
      </span>
      <span v-if="draggable && !isDisabled" class="yd-sortable-tree__grip" aria-hidden="true">
        <Icon name="morph:grip-vertical" />
      </span>
    </div>
    <ul v-if="isExpanded && node.children?.length" role="group" class="yd-sortable-tree__group">
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
        :inherited-disabled="isDisabled"
        :drop-key="dropKey"
        :drop-position="dropPosition"
        :dragging-key="draggingKey"
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
