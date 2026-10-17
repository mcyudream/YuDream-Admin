<script setup lang="ts" generic="TData extends RowData = RowData">
import type { Cell, Row, RowData, RowSelectionState, Table as TanStackTable } from '@tanstack/vue-table'
import type { TableCellSlotProps, TableColumn, TableHeaderSlotProps } from '../basic/table/index.vue'
import type { EnhancedTableProps, StatusPresentation } from './YdEnhancedTable.types'
import { FlexRender } from '@tanstack/vue-table'
import { useMediaQuery } from '@vueuse/core'
import { computed, nextTick, onMounted, ref, shallowRef, useAttrs, watch } from 'vue'
import { YdCheckbox } from '../adapters/index'
import FaAvatar from '../basic/avatar/index.vue'
import FaBadge from '../basic/badge/index.vue'
import FaButton from '../basic/button/index.vue'
import FaCard from '../basic/card/index.vue'
import FaDropdown from '../basic/dropdown/index.vue'
import FaIcon from '../basic/icon/index.vue'
import { createYdResponsiveCardSelection } from '../basic/responsive-table/card-selection'
import YdEnhancedTable from './YdEnhancedTable.vue'

const props = withDefaults(defineProps<EnhancedTableProps<TData> & {
  /** 移动端断点，视口宽度小于等于该值时切换为卡片列表 */
  mobileBreakpoint?: string
  /** 移动端卡片中隐藏的列（列 id 或 accessorKey） */
  cardHiddenColumns?: string[]
}>(), {
  mobileBreakpoint: '(max-width: 768px)',
  cardHiddenColumns: undefined,
  showActions: true,
  actionsHeader: '操作',
  actionsWidth: 80,
  // 以下 prop 类型包含 Boolean，未传值时 Vue 会将其断言为 false
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
  resizable: undefined,
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

const slots = defineSlots<EnhancedTableSlots>()
type EnhancedTableSlots = {
  'toolbar'?: (props: { table: TanStackTable<TData> | undefined }) => any
  'empty'?: (props: { table: TanStackTable<TData> | undefined }) => any
  'card'?: (props: { row: TData, index: number, depth: number, selected: boolean, selectable: boolean, selectDisabled: boolean, selectionId: string, toggleSelected: (value?: boolean) => void, table?: TanStackTable<TData> }) => any
  'card-header'?: (props: { row: TData, index: number }) => any
  'card-footer'?: (props: { row: TData, index: number }) => any
} & {
  [key: `cell-${string}`]: ((props: TableCellSlotProps<TData, any>) => any) | undefined
} & {
  [key: `header-${string}`]: ((props: TableHeaderSlotProps<TData, any>) => any) | undefined
}
const isMobile = useMediaQuery(() => props.mobileBreakpoint || '(max-width: 768px)')
const rowSelection = defineModel<RowSelectionState>('rowSelection', { default: () => ({}) })
const tableRef = ref<{ table: TanStackTable<TData> }>()

/** FaTable 内部选择列 id，移动端卡片由自身控件承载，不再重复渲染 */
const SELECTION_COLUMN_ID = '__fa_table_selection__'
/** YdEnhancedTable 增强列 id */
const USER_COLUMN_ID = 'user-info'
const STATUS_COLUMN_ID = 'status'
const ACTIONS_COLUMN_ID = 'actions'

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

/**
 * 桌面表格实例：卡片字段与选择桥都依赖它。
 * 用 shallowRef 持有实例，保证表格挂载后 engineRows / cardItems 会重算，
 * 否则首帧在子组件 ref 就绪前求值会一直停留在原始值回退分支。
 */
const tableInstance = shallowRef<TanStackTable<TData>>()
watch(() => tableRef.value?.table, (table) => {
  tableInstance.value = table
  syncSelection(rowSelection.value)
}, { flush: 'post' })
onMounted(() => {
  nextTick(() => {
    tableInstance.value = tableRef.value?.table
  })
})

function onSelectionChange(rows: TData[], selection: RowSelectionState) {
  if (!sameSelection(rowSelection.value, selection)) {
    rowSelection.value = { ...selection }
  }
  emit('selectionChange', rows, selection)
}

const engineRows = computed(() => {
  const table = tableInstance.value
  const rows = props.data
  if (!table || rows.length === 0) {
    return new Map<TData, Row<TData>>()
  }
  return new Map(table.getCoreRowModel().flatRows.map(row => [row.original, row]))
})

function cardSelection(row: TData) {
  const engineRow = engineRows.value.get(row)
  return {
    ...createYdResponsiveCardSelection(engineRow),
    table: tableInstance.value,
  }
}

defineExpose({
  get table() {
    return tableRef.value?.table
  },
})

// 通用表格事件转发
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

/** 转发给桌面 YdEnhancedTable 的插槽（排除移动端专用的 card 插槽） */
const tableSlots = computed(() => {
  const result: Record<string, any> = {}
  for (const [name, slot] of Object.entries(slots)) {
    if (name !== 'card' && name !== 'card-header' && name !== 'card-footer') {
      result[name] = slot
    }
  }
  return result
})

/** 转发给 YdEnhancedTable 的 props（排除响应式专属的 mobileBreakpoint / cardHiddenColumns） */
const tableProps = computed<EnhancedTableProps<TData>>(() => {
  const { mobileBreakpoint: _mobileBreakpoint, cardHiddenColumns: _cardHiddenColumns, ...rest } = props
  return rest
})

function childrenOf(node: TData): TData[] {
  if (props.getSubRows) {
    return props.getSubRows(node, 0) ?? []
  }
  const children = (node as Record<string, unknown>).children
  return Array.isArray(children) ? (children as TData[]) : []
}

/** 树型数据在移动端展开为带层级的扁平列表 */
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

/** 获取用户头像 */
function getUserAvatar(row: TData): string {
  if (!props.userColumn?.avatarKey) {
    return ''
  }
  const value = (row as Record<string, any>)[props.userColumn.avatarKey as string]
  return value != null ? String(value) : ''
}

/** 获取用户名 */
function getUserName(row: TData): string {
  if (!props.userColumn) {
    return ''
  }
  return String((row as Record<string, any>)[props.userColumn.nameKey as string] || '')
}

/** 获取用户邮箱 */
function getUserEmail(row: TData): string | undefined {
  if (!props.userColumn?.emailKey) {
    return undefined
  }
  const value = (row as Record<string, any>)[props.userColumn.emailKey as string]
  return value != null ? String(value) : undefined
}

/** 获取状态配置 */
function getStatusConfig(row: TData): StatusPresentation | undefined {
  if (!props.statusColumn) {
    return undefined
  }
  // 布尔状态列必须保留 false（`|| ''` 会把 false 归一成空串，导致 statusMap 里配的 false 永远命不中）
  const raw = (row as Record<string, any>)[props.statusColumn.statusKey as string]
  return props.statusColumn.statusMap[raw === undefined || raw === null ? '' : String(raw)]
}

interface DropdownMenuItem {
  label: string
  icon?: string
  variant?: 'default' | 'destructive'
  disabled?: boolean
  handle: () => void
}

/** 构建操作菜单项 */
/** 无头像时的首字兜底（中文取前 2 字，拉丁取首字母） */
function avatarFallback(row: TData): string {
  const name = getUserName(row).trim()
  if (!name) {
    return '?'
  }
  const isAscii = name.charCodeAt(0) < 128
  return isAscii ? name.slice(0, 1).toUpperCase() : name.slice(0, 2)
}
function buildActionItems(row: TData): DropdownMenuItem[][] {
  if (!props.actions) {
    return []
  }
  const items = props.actions
    .filter(action => action.visible ? action.visible(row) : true)
    .map((action): DropdownMenuItem => ({
      label: typeof action.label === 'function' ? action.label(row) : action.label,
      icon: action.icon,
      variant: action.danger ? 'destructive' : 'default',
      disabled: typeof action.disabled === 'function' ? action.disabled(row) : action.disabled,
      handle: () => action.onClick(row),
    }))
  return [items]
}

/** 菜单项全部被行级可见性过滤时不渲染触发按钮 */
function hasActionItems(groups: DropdownMenuItem[][]) {
  return groups.some(group => group.length > 0)
}

interface CardField {
  /** 单元格唯一键（行内去重） */
  key: string
  /** 列 id，用于匹配 cell-{id} 插槽 */
  id: string
  label: string
  cell?: Cell<TData, any>
  row?: Row<TData>
  value: unknown
  hasCustomCell: boolean
}

function cellSlotName(id: string): `cell-${string}` {
  return `cell-${id}`
}

function cellLabel(cell: Cell<TData, any>): string {
  const def = cell.column.columnDef as { accessorKey?: unknown, header?: unknown }
  if (typeof def.header === 'string') {
    return def.header
  }
  if (typeof def.accessorKey === 'string') {
    return def.accessorKey
  }
  return cell.column.id
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

/**
 * 卡片字段沿用桌面表格的可见单元格：
 * cell-{id} 插槽与列的 cell 渲染函数都会生效，移动端不再只显示原始值。
 */
function buildCardFields(row: TData): CardField[] {
  const hidden = new Set(props.cardHiddenColumns ?? [])
  const engineRow = engineRows.value.get(row)
  if (engineRow) {
    return engineRow.getVisibleCells()
      .filter((cell) => {
        const id = cell.column.id
        if (id === SELECTION_COLUMN_ID) {
          return false
        }
        if (props.userColumn && id === USER_COLUMN_ID) {
          return false
        }
        if (props.statusColumn && id === STATUS_COLUMN_ID) {
          return false
        }
        if (id === ACTIONS_COLUMN_ID) {
          return false
        }
        const accessorKey = (cell.column.columnDef as { accessorKey?: unknown }).accessorKey
        return !hidden.has(id) && !(typeof accessorKey === 'string' && hidden.has(accessorKey))
      })
      .map(cell => ({
        key: cell.id,
        id: cell.column.id,
        label: cellLabel(cell),
        cell,
        row: engineRow,
        value: cell.getValue(),
        hasCustomCell: typeof cell.column.columnDef.cell !== 'undefined',
      }))
  }
  return (props.columns ?? []).map(column => ({
    key: columnKey(column),
    id: columnKey(column),
    label: columnLabel(column),
    value: rowValue(row, column),
    hasCustomCell: false,
  }))
}

/** 统一构建卡片数据，避免模板内重复计算 */
const cardItems = computed(() => (props.tree
  ? flattenTree(props.data)
  : props.data.map(node => ({ node, depth: 0 })))
  .map(({ node, depth }, index) => ({
    node,
    depth,
    index,
    fields: buildCardFields(node),
    selection: cardSelection(node),
    status: getStatusConfig(node),
    actionItems: buildActionItems(node),
  })))

function fieldSlotProps(field: CardField, index: number): TableCellSlotProps<TData, any> {
  return {
    cell: field.cell!,
    column: field.cell!.column,
    index,
    row: field.row!,
    table: tableInstance.value!,
    value: field.value,
  }
}
</script>

<template>
  <div class="yd-responsive-enhanced-table">
    <!-- 桌面端：使用增强表格 -->
    <YdEnhancedTable
      v-show="!isMobile"
      ref="tableRef"
      v-bind="{ ...tableProps, ...tableListeners }"
      @selection-change="onSelectionChange"
    >
      <template v-for="(_, name) in tableSlots" :key="name" #[name]="scope">
        <slot :name="name as keyof EnhancedTableSlots" v-bind="scope || {}" />
      </template>
    </YdEnhancedTable>

    <!-- 移动端：卡片列表 -->
    <div v-if="isMobile" class="mobile-card-list">
      <slot name="toolbar" :table="tableRef?.table" />

      <!-- 空状态 -->
      <FaCard v-if="cardItems.length === 0" class="w-full">
        <div class="py-10 flex flex-col gap-3 items-center justify-center">
          <FaIcon name="morph:inbox" class="text-muted-foreground/30 size-12" />
          <span class="text-sm text-muted-foreground">{{ emptyText || '暂无数据' }}</span>
        </div>
      </FaCard>

      <!-- 数据卡片 -->
      <div
        v-for="item in cardItems"
        :key="item.index"
        class="card-wrapper"
        :style="{ paddingLeft: `${Math.min(item.depth, 6) * 16}px` }"
      >
        <!-- 树形连接线 -->
        <div v-if="item.depth > 0" class="tree-connector" />

        <!-- 自定义卡片插槽 -->
        <slot
          name="card"
          :row="item.node"
          :index="item.index"
          :depth="item.depth"
          v-bind="item.selection"
        >
          <FaCard class="data-card" :class="{ 'is-selected': item.selection.selected }">
            <!-- 卡片头部：用户信息 + 选择框 -->
            <div v-if="userColumn || (selectable && multiple)" class="card-header">
              <div v-if="userColumn" class="user-info">
                <FaAvatar
                  v-if="userColumn.avatarKey"
                  :src="getUserAvatar(item.node)"
                  :fallback="avatarFallback(item.node)"
                  :alt="getUserName(item.node)"
                  class="shrink-0"
                />
                <div class="user-details">
                  <div class="user-name">
                    {{ getUserName(item.node) }}
                  </div>
                  <div v-if="userColumn.emailKey && getUserEmail(item.node)" class="user-email">
                    {{ getUserEmail(item.node) }}
                  </div>
                </div>
              </div>

              <YdCheckbox
                v-if="selectable && multiple"
                :model-value="item.selection.selected"
                :disabled="item.selection.selectDisabled"
                :aria-label="`选择第 ${item.index + 1} 行`"
                class="shrink-0"
                @click.stop
                @update:model-value="item.selection.toggleSelected($event === true)"
              />
            </div>

            <!-- 自定义卡片头部插槽 -->
            <slot name="card-header" :row="item.node" :index="item.index" />

            <!-- 卡片主体：普通列 -->
            <div v-if="item.fields.length > 0" class="card-body">
              <div v-for="field in item.fields" :key="field.key" class="field-row">
                <span class="field-label">{{ field.label }}</span>
                <span class="field-value">
                  <slot v-if="slots[cellSlotName(field.id)]" :name="cellSlotName(field.id)" v-bind="fieldSlotProps(field, item.index)" />
                  <FlexRender
                    v-else-if="field.cell && field.hasCustomCell"
                    :render="field.cell.column.columnDef.cell"
                    :props="field.cell.getContext()"
                  />
                  <template v-else>{{ displayValue(field.value) }}</template>
                </span>
              </div>
            </div>

            <!-- 卡片底部：状态 + 操作（与桌面表格一致地支持语义色胶囊） -->
            <div v-if="item.status || (showActions && hasActionItems(item.actionItems))" class="card-footer">
              <span
                v-if="item.status?.tone"
                class="yd-status-tone shrink-0"
                :data-tone="item.status.tone"
              >
                {{ item.status.label }}
              </span>
              <FaBadge
                v-else-if="item.status"
                :variant="item.status.variant"
                class="shrink-0"
                :value="item.status.label"
              />

              <div class="flex-1" />

              <FaDropdown
                v-if="showActions && hasActionItems(item.actionItems)"
                :items="item.actionItems"
                placement="bottom-end"
              >
                <FaButton variant="ghost" size="sm" class="action-trigger" aria-label="操作菜单">
                  <FaIcon name="morph:ellipsis" class="size-4" />
                </FaButton>
              </FaDropdown>
            </div>

            <!-- 自定义卡片底部插槽 -->
            <slot name="card-footer" :row="item.node" :index="item.index" />
          </FaCard>
        </slot>
      </div>
    </div>
  </div>
</template>

<style scoped>
.yd-responsive-enhanced-table {
  width: 100%;
}

.mobile-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.card-wrapper {
  position: relative;
  width: 100%;
  transition: padding-left 0.2s ease;
}

/* 树形连接线 */
.tree-connector {
  position: absolute;
  top: -6px;
  left: 0;
  width: 16px;
  height: 1px;
  background: var(--color-border-2);
}

.data-card {
  width: 100%;
  cursor: pointer;
  border: 1px solid var(--color-border-2);
  transition: border-color var(--yd-motion-duration-fast, 0.15s) ease, box-shadow var(--yd-motion-duration-fast, 0.15s) ease, transform var(--yd-motion-duration-fast, 0.15s) ease;
}

.data-card:hover {
  border-color: var(--color-border-3);
  box-shadow: var(--yd-surface-shadow, 0 2px 8px rgb(0 0 0 / 0.05));
  transform: translateY(-1px);
}

.data-card.is-selected {
  border-color: oklch(var(--primary));
  box-shadow: 0 0 0 1px color-mix(in oklab, oklch(var(--primary)) 30%, transparent);
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-1);
}

.user-info {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.user-details {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-email {
  overflow: hidden;
  font-size: 13px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 卡片主体 */
.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.field-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  font-size: 14px;
  line-height: 1.5;
}

.field-label {
  flex-shrink: 0;
  min-width: 4rem;
  font-weight: 500;
  color: var(--color-text-3);
}

.field-value {
  flex: 1;
  min-width: 0;
  font-weight: 500;
  color: var(--color-text-1);
  text-align: right;
  word-break: break-word;
}

/* 卡片底部 */
.card-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-1);
}

.action-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 8px;
  color: var(--color-text-2);
}

/* 窄屏：字段上下排布，避免长值被挤成窄列 */
@media (max-width: 640px) {
  .field-row {
    flex-direction: column;
    gap: 4px;
  }

  .field-label {
    min-width: unset;
  }

  .field-value {
    text-align: left;
  }
}
</style>
