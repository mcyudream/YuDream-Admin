<script setup lang="ts">
import type { TreeNodeData } from '@arco-design/web-vue/es/tree/interface'
import { Tree as ATree } from '@arco-design/web-vue'
import { computed, ref, watch } from 'vue'
import { YdInput } from '../adapters/index'
import './extended.css'

export interface YdTreeNode {
  key: string
  label: string
  children?: YdTreeNode[]
  disabled?: boolean
  [key: string]: unknown
}
const props = withDefaults(defineProps<{
  data: YdTreeNode[]
  checkable?: boolean
  filterable?: boolean
  virtualList?: boolean
  defaultExpandAll?: boolean
  filterPlaceholder?: string
  filterMethod?: (query: string, node: YdTreeNode) => boolean
}>(), { filterPlaceholder: '搜索节点' })
const emit = defineEmits<{
  selectionChange: [node: YdTreeNode | null]
  checkChange: [nodes: YdTreeNode[]]
}>()
const selection = defineModel<string[]>('selectedKeys', { default: () => [] })
const checked = defineModel<string[]>('checkedKeys', { default: () => [] })
const expanded = defineModel<string[]>('expandedKeys', { default: () => [] })
const keyword = ref('')
function flatten(items: YdTreeNode[]): YdTreeNode[] {
  return items.flatMap(node => [node, ...flatten(node.children || [])])
}
const nodes = computed(() => flatten(props.data))
const nodeMap = computed(() => new Map(nodes.value.map(node => [node.key, node])))
const branchKeys = computed(() => nodes.value.filter(node => node.children?.length).map(node => node.key))
let initializedExpansion = false
watch(() => props.data, (items) => {
  if (!initializedExpansion && items.length) {
    initializedExpansion = true
    if (props.defaultExpandAll && !expanded.value.length) {
      expanded.value = branchKeys.value
    }
  }
}, { immediate: true })
function matches(node: YdTreeNode): boolean {
  return (props.filterMethod?.(keyword.value, node) ?? node.label.toLocaleLowerCase().includes(keyword.value.toLocaleLowerCase())) || !!node.children?.some(matches)
}
const treeData = computed(() => {
  function convert(items: YdTreeNode[], inheritedDisabled = false): TreeNodeData[] {
    return items.filter(node => !keyword.value || matches(node)).map((node) => {
      const disabled = inheritedDisabled || !!node.disabled
      return { ...node, title: node.label, disabled, children: node.children ? convert(node.children, disabled) : undefined }
    })
  }
  return convert(props.data)
})
const unavailableKeys = computed(() => {
  const keys = new Set<string>()
  function visit(items: YdTreeNode[], inheritedDisabled = false) {
    for (const node of items) {
      const disabled = inheritedDisabled || !!node.disabled
      if (disabled) {
        keys.add(node.key)
      }
      visit(node.children || [], disabled)
    }
  }
  visit(props.data)
  return keys
})
const expandedView = computed(() => keyword.value ? branchKeys.value : expanded.value)
function enabledKeys(keys: (string | number)[]) {
  return [...new Set(keys.map(String))].filter(key => nodeMap.value.has(key) && !unavailableKeys.value.has(key))
}
function select(keys: (string | number)[]) {
  selection.value = enabledKeys(keys)
  emit('selectionChange', nodeMap.value.get(selection.value[0]) || null)
}
function check(keys: (string | number)[]) {
  checked.value = enabledKeys(keys)
  emit('checkChange', checked.value.map(key => nodeMap.value.get(key)!))
}
defineExpose({
  expand: (keys?: string[]) => { expanded.value = keys?.filter(key => branchKeys.value.includes(key)) || branchKeys.value },
  collapse: (keys?: string[]) => { expanded.value = keys ? expanded.value.filter(key => !keys.includes(key)) : [] },
  checkAll: (on: boolean) => check(on ? nodes.value.map(node => node.key) : []),
  setSelection: (key: string) => {
    if (nodeMap.value.has(key) && !unavailableKeys.value.has(key)) {
      select([key])
    }
  },
  clearSelection: () => select([]),
})
</script>

<template>
  <div class="yd-stack yd-tree">
    <YdInput v-if="filterable" v-model="keyword" :placeholder="filterPlaceholder" clearable aria-label="搜索树节点" />
    <ATree :data="treeData" :checkable="checkable" :selected-keys="selection" :checked-keys="checked" :expanded-keys="expandedView" :virtual-list-props="virtualList ? { height: 320 } : undefined" block-node @select="select" @check="check" @update:expanded-keys="expanded = $event.map(String)">
      <template #title="node">
        <slot :node="nodeMap.get(String(node.key))" :expanded="expandedView.includes(String(node.key))">
          {{ node.title }}
        </slot>
      </template>
    </ATree>
    <div v-if="!treeData.length" class="yd-collection-empty">
      <slot name="empty">
        没有匹配节点
      </slot>
    </div>
  </div>
</template>
