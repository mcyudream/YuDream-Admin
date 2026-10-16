<script setup lang="ts">
import type { TreeNodeData } from '@arco-design/web-vue/es/tree/interface'
import type { VNode } from 'vue'
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
  selectable?: boolean
  filterable?: boolean
  virtualList?: boolean
  defaultExpandAll?: boolean
  filterPlaceholder?: string
  filterMethod?: (query: string, node: YdTreeNode) => boolean
}>(), {
  filterPlaceholder: '搜索节点',
  // Vue 会把未传入的布尔 prop 归一成 false，必须显式声明 undefined 默认值，
  // 否则 selectable computed 里的「未传则跟随 !checkable」永远拿不到未传状态
  selectable: undefined,
})
const emit = defineEmits<{
  selectionChange: [node: YdTreeNode | null]
  checkChange: [nodes: YdTreeNode[]]
}>()
const selection = defineModel<string[]>('selectedKeys', { default: () => [] })
const checked = defineModel<string[]>('checkedKeys', { default: () => [] })
const expanded = defineModel<string[]>('expandedKeys', { default: () => [] })
const keyword = ref('')
const treeRef = ref<InstanceType<typeof ATree>>()
function flatten(items: YdTreeNode[]): YdTreeNode[] {
  return items.flatMap(node => [node, ...flatten(node.children || [])])
}
const nodes = computed(() => flatten(props.data))
const nodeMap = computed(() => new Map(nodes.value.map(node => [node.key, node])))
const branchKeys = computed(() => nodes.value.filter(node => node.children?.length).map(node => node.key))
const selectable = computed(() => props.selectable ?? !props.checkable)
const checkStates = computed(() => {
  const checkedKeys = new Set(checked.value)
  const states = new Map<string, 'checked' | 'indeterminate' | 'unchecked'>()

  function visit(node: YdTreeNode): 'checked' | 'indeterminate' | 'unchecked' {
    const childStates = (node.children || []).map(visit)
    const state = checkedKeys.has(node.key)
      ? 'checked'
      : childStates.some(childState => childState !== 'unchecked')
        ? 'indeterminate'
        : 'unchecked'
    states.set(node.key, state)
    return state
  }

  props.data.forEach(visit)
  return states
})
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
  // YdTreeNode 允许业务挂任意扩展字段（icon/menu/nodeType 等）供插槽使用，但 Arco 的
  // TreeNodeData 把 icon/switcherIcon/loadingIcon/dragIcon 当渲染函数直接交给 RenderFunction，
  // 字符串等值会在渲染期抛 "renderFunc is not a function" 并打断整棵子树更新，因此这里白名单透传
  function convert(items: YdTreeNode[], inheritedDisabled = false): TreeNodeData[] {
    return items.filter(node => !keyword.value || matches(node)).map((node) => {
      const disabled = inheritedDisabled || !!node.disabled
      const data: TreeNodeData = { key: node.key, title: node.label, disabled, children: node.children ? convert(node.children, disabled) : undefined }
      for (const flag of ['selectable', 'checkable', 'disableCheckbox', 'draggable', 'isLeaf'] as const) {
        if (typeof node[flag] === 'boolean') {
          data[flag] = node[flag] as boolean
        }
      }
      for (const render of ['icon', 'switcherIcon', 'loadingIcon', 'dragIcon'] as const) {
        if (typeof node[render] === 'function') {
          data[render] = node[render] as () => VNode
        }
      }
      return data
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
  // v-model 绑定时 defineModel 的写入要等父组件回传才生效，写完立即读会拿到旧值，
  // 导致 selectionChange 发出 null（被业务当成取消选中）；用本地结果发事件
  const next = enabledKeys(keys)
  selection.value = next
  emit('selectionChange', nodeMap.value.get(next[0]) || null)
}
function check(keys: (string | number)[]) {
  const next = enabledKeys(keys)
  checked.value = next
  emit('checkChange', next.map(key => nodeMap.value.get(key)!))
}
function nodeCheckState(key: string): 'checked' | 'indeterminate' | 'unchecked' {
  return checkStates.value.get(key) || 'unchecked'
}
function toggleNodeCheck(key: string) {
  if (!props.checkable || unavailableKeys.value.has(key)) {
    return
  }

  treeRef.value?.checkNode(key, nodeCheckState(key) !== 'checked')
}
function handleNodeTitleClick(key: string, event: MouseEvent) {
  if (event.target instanceof Element && event.target.closest('button, a, input, textarea, select, [role="button"], [role="checkbox"], [role="link"], [contenteditable="true"]')) {
    return
  }
  if (props.checkable) {
    toggleNodeCheck(key)
  }

  if (selectable.value && !unavailableKeys.value.has(key)) {
    treeRef.value?.selectNode(key, !selection.value.includes(key))
  }
}
defineExpose({
  expand: (keys?: string[]) => { expanded.value = keys?.filter(key => branchKeys.value.includes(key)) || branchKeys.value },
  collapse: (keys?: string[]) => { expanded.value = keys ? expanded.value.filter(key => !keys.includes(key)) : [] },
  checkAll: (on: boolean) => check(on ? nodes.value.map(node => node.key) : []),
  setSelection: (key: string) => {
    if (selectable.value && nodeMap.value.has(key) && !unavailableKeys.value.has(key)) {
      select([key])
    }
  },
  clearSelection: () => select([]),
})
</script>

<template>
  <div class="yd-stack yd-tree" :class="{ 'yd-tree--checkable': checkable }">
    <YdInput v-if="filterable" v-model="keyword" :placeholder="filterPlaceholder" clearable aria-label="搜索树节点" />
    <ATree ref="treeRef" :data="treeData" :checkable="checkable" :selectable="selectable" :selected-keys="selectable ? selection : []" :checked-keys="checked" :expanded-keys="expandedView" :virtual-list-props="virtualList ? { height: 320 } : undefined" block-node @select="select" @check="check" @update:expanded-keys="expanded = $event.map(String)">
      <template #title="node">
        <span
          v-if="checkable"
          class="yd-tree-check-title"
          :data-state="nodeCheckState(String(node.key))"
          @click.stop="handleNodeTitleClick(String(node.key), $event)"
        >
          <slot :node="nodeMap.get(String(node.key))" :expanded="expandedView.includes(String(node.key))">
            {{ node.title }}
          </slot>
        </span>
        <slot
          v-else
          :node="nodeMap.get(String(node.key))"
          :expanded="expandedView.includes(String(node.key))"
        >
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
