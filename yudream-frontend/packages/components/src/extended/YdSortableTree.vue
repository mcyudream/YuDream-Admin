<script setup lang="ts">
import type { YdSortableTreeNode as NodeData, YdSortableTreeDropEvent, YdSortableTreeDropPosition, YdSortableTreeDropRejection } from './sortable-tree-model'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { resolveYdSortableTreeDrop } from './sortable-tree-model'
import YdSortableTreeNode from './YdSortableTreeNode.vue'
import './extended.css'

defineOptions({ name: 'YdSortableTree', inheritAttrs: false })

const props = withDefaults(defineProps<{
  data: NodeData[]
  disabled?: boolean
  draggable?: boolean
  keyboardReorder?: boolean
  defaultExpandAll?: boolean
  maxDepth?: number
  canDrop?: (event: YdSortableTreeDropEvent) => boolean
  loadChildren?: (node: NodeData) => Promise<NodeData[]>
  ariaLabel?: string
}>(), {
  disabled: false,
  draggable: true,
  keyboardReorder: true,
  defaultExpandAll: false,
  ariaLabel: '可排序树',
})
const emit = defineEmits<{
  selectionChange: [node: NodeData | null]
  drop: [event: YdSortableTreeDropEvent]
  dropRejected: [reason: YdSortableTreeDropRejection | 'custom']
  load: [payload: { node: NodeData, children: NodeData[] }]
  loadError: [payload: { node: NodeData, error: unknown }]
}>()
const selectedKey = defineModel<string | undefined>('selectedKey', { default: undefined })
const expandedKeys = defineModel<string[]>('expandedKeys', { default: () => [] })
const root = ref<HTMLElement | null>(null)
const focusedKey = ref('')
const draggingKey = ref('')
const loadingKeys = ref<string[]>([])
const loadedChildren = ref(new Map<string, NodeData[]>())
let initializedExpansion = false
let disposed = false

function withLoadedChildren(nodes: NodeData[]): NodeData[] {
  return nodes.map((node) => {
    const children = node.children?.length ? node.children : loadedChildren.value.get(node.key)
    return { ...node, children: children ? withLoadedChildren(children) : node.children }
  })
}
const visibleData = computed(() => withLoadedChildren(props.data))
const navigationKeys = computed(() => keyboardVisibleKeys(visibleData.value, new Set(expandedKeys.value)))
const tabStopKey = computed(() => navigationKeys.value.includes(focusedKey.value)
  ? focusedKey.value
  : selectedKey.value && navigationKeys.value.includes(selectedKey.value) ? selectedKey.value : navigationKeys.value[0] ?? '')

function keyboardVisibleKeys(nodes: NodeData[], expanded: Set<string>, inheritedDisabled = false, output: string[] = []): string[] {
  for (const node of nodes) {
    const disabled = inheritedDisabled || props.disabled || Boolean(node.disabled)
    if (!disabled) {
      output.push(node.key)
    }
    if (expanded.has(node.key)) {
      keyboardVisibleKeys(node.children ?? [], expanded, disabled, output)
    }
  }
  return output
}

function findNode(key: string, nodes = visibleData.value): NodeData | undefined {
  for (const node of nodes) {
    if (node.key === key) {
      return node
    }
    const found = findNode(key, node.children ?? [])
    if (found) {
      return found
    }
  }
}

function getElement(key: string): HTMLElement | undefined {
  return [...(root.value?.querySelectorAll<HTMLElement>('[data-node-key]') ?? [])].find(element => element.dataset.nodeKey === key)
}

function focusNode(key: string) {
  if (!navigationKeys.value.includes(key)) {
    return
  }
  focusedKey.value = key
  requestAnimationFrame(() => getElement(key)?.focus())
}

function selectNode(node: NodeData) {
  if (!navigationKeys.value.includes(node.key)) {
    return
  }
  selectedKey.value = node.key
  focusedKey.value = node.key
  emit('selectionChange', node)
}

async function loadNode(node: NodeData) {
  if (!node.lazy || node.children?.length || loadedChildren.value.has(node.key) || !props.loadChildren || loadingKeys.value.includes(node.key)) {
    return
  }
  loadingKeys.value = [...loadingKeys.value, node.key]
  try {
    const children = await props.loadChildren(node)
    if (disposed || !findNode(node.key)) {
      return
    }
    loadedChildren.value = new Map(loadedChildren.value).set(node.key, children)
    emit('load', { node, children })
  }
  catch (error) {
    if (!disposed) {
      emit('loadError', { node, error })
    }
  }
  finally {
    loadingKeys.value = loadingKeys.value.filter(key => key !== node.key)
  }
}

function toggleNode(node: NodeData) {
  if (!navigationKeys.value.includes(node.key)) {
    return
  }
  const next = new Set(expandedKeys.value)
  if (next.has(node.key)) {
    next.delete(node.key)
  }
  else {
    next.add(node.key)
    void loadNode(node)
  }
  expandedKeys.value = [...next]
}

watch(() => props.data, (nodes) => {
  if (!initializedExpansion && nodes.length) {
    initializedExpansion = true
    if (props.defaultExpandAll && !expandedKeys.value.length) {
      const branches: string[] = []
      const visit = (list: NodeData[]) => list.forEach((node) => {
        if (node.children?.length || node.lazy) {
          branches.push(node.key)
          visit(node.children ?? [])
        }
      })
      visit(visibleData.value)
      expandedKeys.value = branches
      branches.forEach((key) => {
        const node = findNode(key)
        if (node) {
          void loadNode(node)
        }
      })
    }
  }
}, { immediate: true })

function resolveCandidate(dragKey: string, dropKey: string, position: YdSortableTreeDropPosition, trigger: YdSortableTreeDropEvent['trigger'], reportRejection = true): YdSortableTreeDropEvent | undefined {
  if (props.disabled) {
    if (reportRejection) {
      emit('dropRejected', 'disabled-node')
    }
    return undefined
  }
  const result = resolveYdSortableTreeDrop(visibleData.value, dragKey, dropKey, position, { maxDepth: props.maxDepth, trigger })
  if (!result.accepted) {
    if (reportRejection) {
      emit('dropRejected', result.reason)
    }
    return undefined
  }
  if (props.canDrop && !props.canDrop(result.event)) {
    if (reportRejection) {
      emit('dropRejected', 'custom')
    }
    return undefined
  }
  return result.event
}

function commitDrop(event: YdSortableTreeDropEvent) {
  emit('drop', event)
  focusedKey.value = event.dragKey
}

function siblingsFor(key: string): NodeData[] | undefined {
  function walk(nodes: NodeData[]): NodeData[] | undefined {
    if (nodes.some(node => node.key === key)) {
      return nodes
    }
    for (const node of nodes) {
      const result = walk(node.children ?? [])
      if (result) {
        return result
      }
    }
  }
  return walk(visibleData.value)
}

function parentOf(key: string, nodes = visibleData.value, parent: NodeData | null = null): NodeData | null | undefined {
  for (const node of nodes) {
    if (node.key === key) {
      return parent
    }
    const result = parentOf(key, node.children ?? [], node)
    if (result !== undefined) {
      return result
    }
  }
}

function keyboardMove(event: KeyboardEvent, key: string): boolean {
  if (!props.keyboardReorder || props.disabled || !event.altKey) {
    return false
  }
  if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) {
    return false
  }
  const siblings = siblingsFor(key)
  const index = siblings?.findIndex(node => node.key === key) ?? -1
  if (!siblings || index < 0) {
    return false
  }
  let target: NodeData | undefined
  let position: YdSortableTreeDropPosition | undefined
  if (event.key === 'ArrowUp' && index > 0) {
    target = siblings[index - 1]
    position = -1
  }
  else if (event.key === 'ArrowDown' && index < siblings.length - 1) {
    target = siblings[index + 1]
    position = 1
  }
  else if (event.key === 'ArrowRight' && index > 0) {
    target = siblings[index - 1]
    position = 0
  }
  else if (event.key === 'ArrowLeft') {
    const parent = parentOf(key)
    if (parent) {
      target = parent
      position = 1
    }
  }
  if (!target || position === undefined) {
    event.preventDefault()
    return true
  }
  event.preventDefault()
  const candidate = resolveCandidate(key, target.key, position, 'keyboard')
  if (candidate) {
    commitDrop(candidate)
  }
  return true
}

function onKeydown(event: KeyboardEvent) {
  const row = (event.target as HTMLElement).closest<HTMLElement>('[role="treeitem"][data-node-key]')
  const key = row?.dataset.nodeKey
  if (!row || !key) {
    return
  }
  if (!navigationKeys.value.includes(key)) {
    return
  }
  if (keyboardMove(event, key)) {
    return
  }
  const node = findNode(key)
  if (!node) {
    return
  }
  const nodeKeys = keyboardVisibleKeys(visibleData.value, new Set(expandedKeys.value))
  const index = nodeKeys.indexOf(key)
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    const next = nodeKeys[Math.max(0, Math.min(nodeKeys.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1)))]
    if (next) {
      event.preventDefault()
      focusNode(next)
    }
  }
  else if (event.key === 'Home' || event.key === 'End') {
    const next = event.key === 'Home' ? nodeKeys[0] : nodeKeys.at(-1)
    if (next) {
      event.preventDefault()
      focusNode(next)
    }
  }
  else if (event.key === 'ArrowRight' && (node.children?.length || node.lazy)) {
    event.preventDefault()
    if (!expandedKeys.value.includes(key)) {
      toggleNode(node)
    }
    else if (node.children?.[0]) {
      focusNode(node.children[0].key)
    }
  }
  else if (event.key === 'ArrowLeft') {
    if (expandedKeys.value.includes(key)) {
      event.preventDefault()
      toggleNode(node)
    }
    else {
      const parent = parentOf(key)
      if (parent) {
        event.preventDefault()
        focusNode(parent.key)
      }
    }
  }
  else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    selectNode(node)
  }
}

function onDragStart(node: NodeData, event: DragEvent) {
  if (!navigationKeys.value.includes(node.key) || !props.draggable) {
    event.preventDefault()
    return
  }
  draggingKey.value = node.key
  event.dataTransfer?.setData('text/plain', node.key)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function dropPosition(event: DragEvent): YdSortableTreeDropPosition {
  const row = event.currentTarget as HTMLElement
  const rect = row.getBoundingClientRect()
  const ratio = rect.height ? (event.clientY - rect.top) / rect.height : 0.5
  return ratio < 0.25 ? -1 : ratio > 0.75 ? 1 : 0
}

function onDragOver(node: NodeData, event: DragEvent) {
  if (!draggingKey.value) {
    return
  }
  const candidate = resolveCandidate(draggingKey.value, node.key, dropPosition(event), 'pointer', false)
  if (candidate) {
    event.preventDefault()
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move'
    }
  }
}

function onDrop(node: NodeData, event: DragEvent) {
  event.preventDefault()
  const key = draggingKey.value || event.dataTransfer?.getData('text/plain') || ''
  const candidate = key ? resolveCandidate(key, node.key, dropPosition(event), 'pointer') : undefined
  if (candidate) {
    commitDrop(candidate)
  }
  draggingKey.value = ''
}

function clearDragging() {
  draggingKey.value = ''
}

defineExpose({
  focus: focusNode,
  expand: (keys?: string[]) => { expandedKeys.value = keys ?? visibleData.value.filter(node => node.children?.length || node.lazy).map(node => node.key) },
  collapse: (keys?: string[]) => { expandedKeys.value = keys ? expandedKeys.value.filter(key => !keys.includes(key)) : [] },
})

onBeforeUnmount(() => {
  disposed = true
  clearDragging()
})
</script>

<template>
  <div ref="root" class="yd-sortable-tree" :class="{ 'yd-sortable-tree--disabled': disabled }" v-bind="$attrs" @keydown="onKeydown">
    <ul role="tree" :aria-label="ariaLabel" class="yd-sortable-tree__list">
      <YdSortableTreeNode
        v-for="node in visibleData"
        :key="node.key"
        :node="node"
        :depth="1"
        :selected-key="selectedKey"
        :focused-key="tabStopKey"
        :inherited-disabled="disabled"
        :expanded-keys="expandedKeys"
        :draggable="draggable && !disabled"
        :loading-keys="loadingKeys"
        @select="selectNode"
        @toggle="toggleNode"
        @drag-start="onDragStart"
        @drag-over="onDragOver"
        @drop="onDrop"
        @drag-end="clearDragging"
        @focus="focusedKey = $event.key"
      >
        <template #title="slotProps">
          <slot name="title" v-bind="slotProps" />
        </template>
      </YdSortableTreeNode>
      <li v-if="!visibleData.length" class="yd-sortable-tree__empty">
        <slot name="empty">
          没有节点
        </slot>
      </li>
    </ul>
  </div>
</template>

<style>
.yd-sortable-tree { min-width: 0; color: var(--color-text-1); }
.yd-sortable-tree--disabled { opacity: .65; }
.yd-sortable-tree__list,
.yd-sortable-tree__group { margin: 0; padding: 0; list-style: none; }
.yd-sortable-tree__row { display: flex; min-height: 34px; align-items: center; gap: 6px; border-radius: 5px; outline: none; cursor: pointer; user-select: none; }
.yd-sortable-tree__row:hover { background: var(--color-fill-1); }
.yd-sortable-tree__row:focus-visible { outline: 2px solid rgb(var(--primary-6)); outline-offset: -2px; }
.yd-sortable-tree__row--selected { background: var(--color-fill-2); color: rgb(var(--primary-6)); }
.yd-sortable-tree__row--disabled { color: var(--color-text-3); cursor: not-allowed; }
.yd-sortable-tree__row[draggable="true"] { cursor: grab; }
.yd-sortable-tree__row[draggable="true"]:active { cursor: grabbing; }
.yd-sortable-tree__indent { width: calc(var(--yd-tree-depth) * 18px); flex: 0 0 auto; }
.yd-sortable-tree__chevron { display: inline-grid; width: 18px; height: 18px; flex: 0 0 18px; place-items: center; color: var(--color-text-3); }
.yd-sortable-tree__chevron > span:not(.yd-sortable-tree__spinner) { font-size: 18px; transition: transform 120ms ease; }
.yd-sortable-tree__chevron--open > span:not(.yd-sortable-tree__spinner) { transform: rotate(90deg); }
.yd-sortable-tree__title { min-width: 0; flex: 1; }
.yd-sortable-tree__empty { padding: 12px; color: var(--color-text-3); text-align: center; }
.yd-sortable-tree__spinner { width: 12px; height: 12px; border: 2px solid var(--color-border-2); border-top-color: rgb(var(--primary-6)); border-radius: 50%; animation: yd-sortable-tree-spin .8s linear infinite; }
@keyframes yd-sortable-tree-spin { to { transform: rotate(360deg); } }
</style>
