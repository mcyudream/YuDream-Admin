<script setup lang="ts" generic="TData extends RowData = RowData">
import type { RowData, RowSelectionState, Table as TanStackTable } from '@tanstack/vue-table'
import type { RadioGroupOption } from '../radio-group/index.vue'
import type { TableColumn, TableProps } from '../table/index.vue'
import { useMediaQuery } from '@vueuse/core'
import { computed, ref, useAttrs, useSlots, watch } from 'vue'
import { YdCheckbox, YdRadioGroup } from '../../adapters/index'
import FaCard from '../card/index.vue'
import FaTable from '../table/index.vue'
import { createYdResponsiveCardSelection } from './card-selection'

const props = withDefaults(defineProps<TableProps<TData> & {
  /** 移动端断点，视口宽度小于等于该值时切换为卡片列表 */
  mobileBreakpoint?: string
}>(), {
  mobileBreakpoint: '(max-width: 768px)',
  // 以下 prop 类型包含 Boolean，未传值时 Vue 会将其断言为 false。
  // 这里显式声明 undefined 默认值，保证未传值时经 v-bind 透传给 FaTable 后仍走 FaTable 自身的默认值，
  // 尤其是 expanded：若为 false 会让 FaTable 误判为受控展开状态，导致树形表格无法展开、点击无效。
  border: undefined,
  bodyClass: undefined,
  class: undefined,
  columnVisibility: undefined,
  defaultExpanded: undefined,
  enableMultiSort: undefined,
  enableSortingRemoval: undefined,
  expanded: undefined,
  headerClass: undefined,
  headerRowClass: undefined,
  manualExpanding: undefined,
  multiple: undefined,
  rowClass: undefined,
  selectable: undefined,
  selectionColumnClass: undefined,
  sortable: undefined,
  sortDescFirst: undefined,
  stripe: undefined,
  tableClass: undefined,
  tableRootClass: undefined,
  tree: undefined,
})

const emit = defineEmits<{ selectionChange: [rows: TData[], selection: RowSelectionState] }>()
const slots = useSlots()
const isMobile = useMediaQuery(() => props.mobileBreakpoint || '(max-width: 768px)')
const rowSelection = defineModel<RowSelectionState>('rowSelection', { default: () => ({}) })
const tableRef = ref<{ table: TanStackTable<TData> }>()

function sameSelection(left: RowSelectionState, right: RowSelectionState) {
  const leftKeys = Object.keys(left).filter(key => left[key])
  const rightKeys = Object.keys(right).filter(key => right[key])
  return leftKeys.length === rightKeys.length && leftKeys.every(key => right[key])
}
function syncSelection(selection: RowSelectionState) {
  const table = tableRef.value?.table
  if (table && !sameSelection(table.getState().rowSelection, selection)) {
    table.setRowSelection({ ...selection })
  }
}
watch(() => rowSelection.value, syncSelection, { deep: true, immediate: true })
watch(() => tableRef.value?.table, () => syncSelection(rowSelection.value), { flush: 'post' })
function onSelectionChange(rows: TData[], selection: RowSelectionState) {
  if (!sameSelection(rowSelection.value, selection)) {
    rowSelection.value = { ...selection }
  }
  emit('selectionChange', rows, selection)
}
const engineRows = computed(() => new Map(tableRef.value?.table.getCoreRowModel().flatRows.map(row => [row.original, row]) ?? []))
function cardSelection(row: TData) {
  const engineRow = engineRows.value.get(row)
  return {
    ...createYdResponsiveCardSelection(engineRow),
    table: tableRef.value?.table,
  }
}
function hasSelectionColumnIn(columns: TableColumn<TData>[]): boolean {
  return columns.some(column => column.type === 'selection' || (Array.isArray(column.columns) && hasSelectionColumnIn(column.columns)))
}
const hasSelectionColumn = computed(() => props.selectable && hasSelectionColumnIn(props.columns))
defineExpose({
  get table() {
    return tableRef.value?.table
  },
})

// 通用表格事件转发；selectionChange 由模型桥同步后单独发出。
const attrs = useAttrs()
const tableListeners = computed(() => {
  const result: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(attrs)) {
    if (key.startsWith('on')) {
      result[key] = value
    }
  }
  return result
})

/** 转发给桌面 FaTable 的插槽（排除移动端专用的 card 插槽） */
const tableSlots = computed(() => {
  const result: Record<string, any> = {}
  for (const [name, slot] of Object.entries(slots)) {
    if (name !== 'card') {
      result[name] = slot
    }
  }
  return result
})

/** 转发给 FaTable 的 props（排除响应式专属的 mobileBreakpoint） */
const tableProps = computed<TableProps<TData>>(() => {
  const { mobileBreakpoint: _mobileBreakpoint, ...rest } = props
  return rest
})
const cardColumns = computed(() => props.columns.filter(column => 'accessorKey' in column && column.accessorKey))

function childrenOf(node: TData): TData[] {
  if (props.getSubRows) {
    return props.getSubRows(node, 0) ?? []
  }
  const children = (node as Record<string, unknown>).children
  return Array.isArray(children) ? (children as TData[]) : []
}

/** 树型数据在移动端展开为带层级的扁平列表，保证子级也能展示 */
function flattenTree(nodes: TData[], depth = 0): Array<{ node: TData, depth: number }> {
  const result: Array<{ node: TData, depth: number }> = []
  for (const node of nodes) {
    result.push({ node, depth })
    const children = childrenOf(node)
    if (children.length > 0) {
      result.push(...flattenTree(children, depth + 1))
    }
  }
  return result
}

const cardRows = computed(() => props.tree
  ? flattenTree(props.data)
  : props.data.map(node => ({ node, depth: 0 })))

interface ResponsiveCardRadioOption extends RadioGroupOption {
  row: TData
  index: number
  depth: number
}

const singleSelectionOptions = computed<ResponsiveCardRadioOption[]>(() => cardRows.value.map(({ node, depth }, index) => {
  const selection = cardSelection(node)
  return {
    id: `responsive-card-${selection.selectionId || index}`,
    label: `选择第 ${index + 1} 行`,
    value: selection.selectionId || String(index),
    disabled: selection.selectDisabled,
    row: node,
    index,
    depth,
  }
}))

const singleSelectedId = computed(() => Object.keys(rowSelection.value).find(id => rowSelection.value[id]))

function hasCustomCardSlot() {
  return Boolean(slots.card)
}

function selectSingleCard(value: unknown) {
  if (value == null) {
    return
  }
  const row = tableRef.value?.table.getRow(String(value))
  if (row?.getCanSelect()) {
    row.toggleSelected(true)
  }
}

function columnLabel(column: TableColumn<TData, any>): string {
  if ('header' in column && typeof column.header === 'string') {
    return column.header
  }
  if ('accessorKey' in column && column.accessorKey) {
    return String(column.accessorKey)
  }
  return ''
}

function rowValue(row: TData, column: TableColumn<TData, any>): unknown {
  if ('accessorKey' in column && column.accessorKey) {
    return (row as Record<string, unknown>)[column.accessorKey as string]
  }
  return undefined
}

/** 默认卡片值为空时显示占位符，避免留白难以辨识 */
function displayValue(value: unknown): string {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

function columnKey(column: TableColumn<TData, any>): string {
  if ('accessorKey' in column && column.accessorKey) {
    return String(column.accessorKey)
  }
  if ('id' in column && column.id) {
    return String(column.id)
  }
  return ''
}
</script>

<template>
  <div>
    <FaTable v-show="!isMobile" ref="tableRef" v-bind="{ ...tableProps, ...tableListeners }" @selection-change="onSelectionChange">
      <template v-for="(_, name) in tableSlots" :key="name" #[name]="scope">
        <slot :name="name" v-bind="scope || {}" />
      </template>
    </FaTable>

    <div v-if="isMobile" class="flex flex-col gap-3">
      <slot name="toolbar" :table="tableRef?.table" />
      <FaCard v-if="cardRows.length === 0" class="w-full">
        <div class="text-sm text-secondary-foreground/50 py-10 flex items-center justify-center">
          {{ emptyText || '暂无数据' }}
        </div>
      </FaCard>
      <YdRadioGroup
        v-if="cardRows.length > 0 && hasSelectionColumn && !multiple && !hasCustomCardSlot()"
        :model-value="singleSelectedId"
        :options="singleSelectionOptions"
        class="responsive-table-card-radio-list"
        option-class="responsive-table-card-radio-option"
        item-class="responsive-table-card-radio-control"
        @update:model-value="selectSingleCard"
      >
        <template #option="{ option }">
          <div class="w-full" :style="{ paddingLeft: `${Math.min(option.depth, 6) * 16}px` }">
            <div v-if="option.depth > 0" class="tree-card-connector" />
            <FaCard class="w-full">
              <div class="flex flex-col gap-2">
                <div v-for="column in cardColumns" :key="columnKey(column)" class="text-sm flex gap-3 items-start justify-between">
                  <span class="text-secondary-foreground/60 shrink-0">{{ columnLabel(column) }}</span>
                  <span class="font-medium text-right break-all">{{ displayValue(rowValue(option.row, column)) }}</span>
                </div>
              </div>
            </FaCard>
          </div>
        </template>
      </YdRadioGroup>
      <template v-else>
        <div
          v-for="(row, index) in cardRows"
          :key="index"
          class="w-full"
          :style="{ paddingLeft: `${Math.min(row.depth, 6) * 16}px` }"
        >
          <div v-if="row.depth > 0" class="tree-card-connector" />
          <slot name="card" :row="row.node" :index="index" :depth="row.depth" v-bind="cardSelection(row.node)">
            <FaCard class="w-full">
              <div class="flex flex-col gap-2">
                <div v-if="hasSelectionColumn" class="flex justify-end">
                  <YdCheckbox
                    v-if="multiple"
                    :model-value="cardSelection(row.node).selected"
                    :disabled="cardSelection(row.node).selectDisabled"
                    :aria-label="`选择第 ${index + 1} 行`"
                    @click.stop
                    @update:model-value="cardSelection(row.node).toggleSelected($event === true)"
                  />
                </div>
                <div v-for="column in cardColumns" :key="columnKey(column)" class="text-sm flex gap-3 items-start justify-between">
                  <span class="text-secondary-foreground/60 shrink-0">{{ columnLabel(column) }}</span>
                  <span class="font-medium text-right break-all">{{ displayValue(rowValue(row.node, column)) }}</span>
                </div>
              </div>
            </FaCard>
          </slot>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.tree-card-connector {
  margin-bottom: 4px;
  height: 1px;
  background: var(--color-border-2);
}
</style>
