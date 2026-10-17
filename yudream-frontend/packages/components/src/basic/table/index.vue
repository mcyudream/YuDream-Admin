<script lang="ts">
import type {
  Cell,
  CellContext,
  Column,
  ColumnDef,
  ColumnPinningState,
  ColumnSizingState,
  DisplayColumnDef,
  ExpandedState,
  Header,
  HeaderContext,
  Row,
  RowData,
  RowSelectionState,
  SortingState,
  TableOptions,
  Table as TanStackTable,
  Updater,
  VisibilityState,
} from '@tanstack/vue-table'
import type { CSSProperties, HTMLAttributes } from 'vue'
import {
  FlexRender,
  getCoreRowModel,
  getExpandedRowModel,
  getSortedRowModel,
  isFunction,
  useVueTable,
} from '@tanstack/vue-table'
import { computed, inject, ref, watch } from 'vue'
import { cn } from '#utils'
import Button from '../../basic/button/index.vue'
import { Checkbox } from '../../basic/checkbox/checkbox'
import Dropdown from '../../basic/dropdown/index.vue'
import Icon from '../../basic/icon/index.vue'
import { RadioGroup, RadioGroupItem } from '../../basic/radio-group/radio-group'
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableEmpty,
  TableHead,
  TableHeader,
  TableRow,
} from './table'
import { tableActionsTargetKey } from './table-actions-context'
import { valueUpdater } from './table/utils'

const SELECTION_COLUMN_ID = '__fa_table_selection__'
const TABLE_HEADER_ROW_HEIGHT = 40

type ColumnSlotName = `cell-${string}` | `header-${string}` | `footer-${string}`
type TableSlots<TData extends RowData> = {
  caption?: () => any
  empty?: (props: TableEmptySlotProps<TData>) => any
  toolbar?: (props: TableToolbarSlotProps<TData>) => any
} & {
  [key: `cell-${string}`]: ((props: TableCellSlotProps<TData, any>) => any) | undefined
} & {
  [key: `header-${string}`]: ((props: TableHeaderSlotProps<TData, any>) => any) | undefined
} & {
  [key: `footer-${string}`]: ((props: TableFooterSlotProps<TData, any>) => any) | undefined
}
type ColumnClass<TContext> = HTMLAttributes['class'] | ((context: TContext) => HTMLAttributes['class'])
type RowClass<TData extends RowData> = HTMLAttributes['class'] | ((row: Row<TData>) => HTMLAttributes['class'])
type RowKey<TData extends RowData> = keyof TData | string | ((row: TData, index: number) => string | number)

export type TableAlign = 'left' | 'center' | 'right'
export type TableColumnFixed = 'left' | 'right'
export type TableSelectionDisabled<TData extends RowData = RowData> = (row: TData, index: number) => boolean
export type TableSortingState = SortingState
export type TableExpandedState = ExpandedState

interface TableColumnOptions<TData extends RowData = RowData, TValue = unknown> {
  align?: TableAlign
  /** 默认隐藏的非常用列（技术字段、长文本、重复信息）；列显隐菜单里可打开，或一键显示全部字段 */
  defaultHidden?: boolean
  cellClass?: ColumnClass<CellContext<TData, TValue>>
  class?: HTMLAttributes['class']
  fixed?: boolean | TableColumnFixed
  headerClass?: ColumnClass<HeaderContext<TData, TValue>>
  label?: string
  maxWidth?: number | string
  minWidth?: number | string
  title?: string
  width?: number | string
}

export type TableSelectionColumn<TData extends RowData = RowData>
  = Partial<DisplayColumnDef<TData, unknown>>
    & TableColumnOptions<TData, unknown>
    & {
      type: 'selection'
      disabled?: TableSelectionDisabled<TData>
      columns?: never
    }

export type TableColumn<TData extends RowData = RowData, TValue = unknown>
  = | (ColumnDef<TData, TValue> & TableColumnOptions<TData, TValue> & {
    type?: never
    columns?: TableColumn<TData, any>[]
  })
  | TableSelectionColumn<TData>

export interface TableCellSlotProps<TData extends RowData = RowData, TValue = unknown> {
  cell: Cell<TData, TValue>
  column: Column<TData, TValue>
  index: number
  row: Row<TData>
  table: TanStackTable<TData>
  value: TValue
}

export interface TableHeaderSlotProps<TData extends RowData = RowData, TValue = unknown> {
  column: Column<TData, TValue>
  header: Header<TData, TValue>
  table: TanStackTable<TData>
}

export interface TableFooterSlotProps<TData extends RowData = RowData, TValue = unknown> {
  column: Column<TData, TValue>
  header: Header<TData, TValue>
  table: TanStackTable<TData>
}

export interface TableEmptySlotProps<TData extends RowData = RowData> {
  table: TanStackTable<TData>
}

export interface TableToolbarSlotProps<TData extends RowData = RowData> {
  table: TanStackTable<TData>
}

export interface TableProps<TData extends RowData = RowData> {
  border?: boolean
  bodyClass?: HTMLAttributes['class']
  caption?: string
  class?: HTMLAttributes['class']
  columnVisibility?: boolean
  columns: TableColumn<TData, any>[]
  data: TData[]
  defaultExpanded?: ExpandedState
  defaultSorting?: SortingState
  emptyText?: string
  enableMultiSort?: boolean
  enableSortingRemoval?: boolean
  expanded?: ExpandedState
  getRowId?: TableOptions<TData>['getRowId']
  getSubRows?: TableOptions<TData>['getSubRows']
  headerClass?: HTMLAttributes['class']
  headerRowClass?: HTMLAttributes['class']
  indentSize?: number
  manualExpanding?: boolean
  /** 服务端排序场景置 true；默认本地排序 */
  manualSorting?: boolean
  multiple?: boolean
  /** 列宽拖拽启用（配合列宽定义；拖动结果写入列 sizing 状态） */
  resizable?: boolean
  rowClass?: RowClass<TData>
  rowKey?: RowKey<TData>
  selectable?: boolean
  selectionColumnClass?: HTMLAttributes['class']
  sortable?: boolean
  sorting?: SortingState
  sortDescFirst?: boolean
  stripe?: boolean
  tableClass?: HTMLAttributes['class']
  tableRootClass?: HTMLAttributes['class']
  tree?: boolean
}
</script>

<script setup lang="ts" generic="TData extends RowData = RowData">
defineOptions({
  name: 'BuiltInTable',
})

const props = withDefaults(defineProps<TableProps<TData>>(), {
  border: false,
  caption: undefined,
  columnVisibility: false,
  defaultExpanded: undefined,
  enableMultiSort: true,
  enableSortingRemoval: true,
  expanded: undefined,
  emptyText: '暂无数据',
  indentSize: 20,
  manualExpanding: false,
  manualSorting: false,
  multiple: false,
  resizable: false,
  selectable: false,
  sortable: false,
  sortDescFirst: true,
  stripe: false,
  tree: false,
})

const emit = defineEmits<{
  'expandedChange': [expanded: ExpandedState, table: TanStackTable<TData>]
  'rowClick': [row: TData, index: number, event: MouseEvent]
  'selectionChange': [rows: TData[], rowSelection: RowSelectionState]
  'sortingChange': [sorting: SortingState, table: TanStackTable<TData>]
  'update:expanded': [expanded: ExpandedState]
}>()

const slots = defineSlots<TableSlots<TData>>()

const rowSelection = ref<RowSelectionState>({})
const columnVisibilityState = ref<VisibilityState>({})
const columnSizingState = ref<ColumnSizingState>({})
const sortingState = ref<SortingState>(props.sorting ?? props.defaultSorting ?? [])
const expandedState = ref<ExpandedState>(props.expanded ?? props.defaultExpanded ?? {})
const isSortingControlled = computed(() => props.sorting !== undefined)
const isExpandedControlled = computed(() => props.expanded !== undefined)

/** 列显隐控件传送目标：被 YdTablePageLayout 包裹时并入页面操作栏 */
const actionsTargetId = inject(tableActionsTargetKey, undefined)
const columnVisibilityTarget = computed(() => actionsTargetId ? `#${actionsTargetId}` : undefined)
/** 自身工具栏行是否还需要占位（有 toolbar 插槽，或列控件未被传送走） */
const hasToolbar = computed(() => Boolean(slots.toolbar) || (props.columnVisibility && !columnVisibilityTarget.value))
/** 列宽拖动生效时用 table-fixed 精确分配列宽 */
const isResizeLayout = computed(() => props.resizable)

function isSelectionColumnDef(column: TableColumn<TData, any>): column is TableSelectionColumn<TData> {
  return column.type === 'selection'
}

function normalizeFixed(value?: boolean | TableColumnFixed): TableColumnFixed | undefined {
  if (value === true) {
    return 'left'
  }

  if (value === 'left' || value === 'right') {
    return value
  }

  return undefined
}

function parseNumericWidth(width?: number | string) {
  if (typeof width === 'number') {
    return width
  }

  if (typeof width === 'string') {
    const matched = width.trim().match(/^(\d+(?:\.\d+)?)px$/)
    return matched ? Number(matched[1]) : undefined
  }

  return undefined
}

function getColumnId(column: TableColumn<TData, any>): string | undefined {
  if (isSelectionColumnDef(column)) {
    return column.id ? String(column.id) : SELECTION_COLUMN_ID
  }

  if (column.id) {
    return String(column.id)
  }

  if ('accessorKey' in column && column.accessorKey) {
    return String(column.accessorKey).replaceAll('.', '_')
  }

  if (typeof column.header === 'string') {
    return column.header
  }

  return undefined
}

function normalizeColumn(column: TableColumn<TData, any>): TableColumn<TData, any> {
  const normalized = { ...column }

  if (isSelectionColumnDef(normalized)) {
    normalized.id ??= SELECTION_COLUMN_ID
    normalized.header ??= ''
    normalized.cell ??= ''
    normalized.align ??= 'center'
    normalized.enableHiding ??= false
    normalized.enableSorting ??= false
    normalized.size ??= parseNumericWidth(normalized.width) ?? 40
    normalized.minSize ??= parseNumericWidth(normalized.minWidth)
    normalized.maxSize ??= parseNumericWidth(normalized.maxWidth)
    normalized.width ??= normalized.size

    return normalized
  }

  normalized.header ??= normalized.label ?? normalized.title
  // 列默认参与排序（选择列等特殊列在各自分支显式关闭）
  normalized.enableSorting ??= true
  // 拖动起点：优先 width，其次 minWidth（无显式宽度的列按最小宽开始，避免拖动瞬间跳变）
  normalized.size ??= parseNumericWidth(normalized.width) ?? parseNumericWidth(normalized.minWidth)
  normalized.minSize ??= parseNumericWidth(normalized.minWidth)
  normalized.maxSize ??= parseNumericWidth(normalized.maxWidth)

  if (normalized.columns?.length) {
    normalized.columns = normalized.columns.map(item => normalizeColumn(item))
  }

  return normalized
}

const normalizedColumns = computed<TableColumn<TData, any>[]>(() => props.columns.map(column => normalizeColumn(column)))

const resolvedColumns = computed<ColumnDef<TData, any>[]>(() => normalizedColumns.value as ColumnDef<TData, any>[])

function findSelectionColumn(columns: TableColumn<TData, any>[]): TableSelectionColumn<TData> | undefined {
  for (const column of columns) {
    if (isSelectionColumnDef(column)) {
      return column
    }

    if (column.columns?.length) {
      const selectionColumn = findSelectionColumn(column.columns)
      if (selectionColumn) {
        return selectionColumn
      }
    }
  }
}

const selectionColumnDef = computed(() => findSelectionColumn(normalizedColumns.value))

function getColumnPinningState(columns: TableColumn<TData, any>[]) {
  const left = new Set<string>()
  const right = new Set<string>()

  function collect(items: TableColumn<TData, any>[], inheritedFixed?: TableColumnFixed) {
    items.forEach((column) => {
      const fixed = normalizeFixed(column.fixed) ?? inheritedFixed

      if (column.columns?.length) {
        collect(column.columns, fixed)
        return
      }

      const columnId = getColumnId(column)
      if (!columnId || !fixed) {
        return
      }

      if (fixed === 'left') {
        left.add(columnId)
        right.delete(columnId)
      }
      else {
        right.add(columnId)
        left.delete(columnId)
      }
    })
  }

  collect(columns)

  return {
    left: [...left],
    right: [...right],
  } satisfies ColumnPinningState
}

const columnPinning = computed<ColumnPinningState>(() => getColumnPinningState(resolvedColumns.value as TableColumn<TData, any>[]))

function resolveRowId(row: TData, index: number, parent?: Row<TData>): string {
  if (props.getRowId) {
    return props.getRowId(row, index, parent)
  }

  if (typeof props.rowKey === 'function') {
    return String(props.rowKey(row, index))
  }

  if (props.rowKey) {
    const value = (row as Record<string, unknown>)[String(props.rowKey)]
    if (value != null) {
      return String(value)
    }
  }

  return parent ? `${parent.id}.${index}` : String(index)
}

function resolveSubRows(row: TData, index: number): TData[] | undefined {
  if (!props.tree) {
    return undefined
  }

  if (props.getSubRows) {
    return props.getSubRows(row, index)
  }

  const children = (row as Record<string, unknown>).children
  return Array.isArray(children) ? children as TData[] : undefined
}

function isRowSelectionDisabled(row: Row<TData>) {
  return selectionColumnDef.value?.disabled?.(row.original, row.index) ?? false
}

function syncSingleRowSelection() {
  if (props.multiple) {
    return
  }

  const selectedIds = Object.keys(rowSelection.value).filter(id => rowSelection.value[id])
  if (selectedIds.length <= 1) {
    return
  }

  const selectedId = selectedIds.at(-1)
  rowSelection.value = selectedId ? { [selectedId]: true } : {}
}

function handleRowSelectionChange(updaterOrValue: Updater<RowSelectionState>) {
  valueUpdater(updaterOrValue, rowSelection)
  syncSingleRowSelection()
}

function handleColumnVisibilityChange(updaterOrValue: Updater<VisibilityState>) {
  valueUpdater(updaterOrValue, columnVisibilityState)
}

function handleColumnSizingChange(updaterOrValue: Updater<ColumnSizingState>) {
  valueUpdater(updaterOrValue, columnSizingState)
}

function handleSortingChange(updaterOrValue: Updater<SortingState>) {
  const nextSorting = isFunction(updaterOrValue) ? updaterOrValue(sortingState.value) : updaterOrValue

  if (!isSortingControlled.value) {
    sortingState.value = nextSorting
  }

  emit('sortingChange', nextSorting, table)
}

function handleExpandedChange(updaterOrValue: Updater<ExpandedState>) {
  const nextExpanded = isFunction(updaterOrValue) ? updaterOrValue(expandedState.value) : updaterOrValue

  if (!isExpandedControlled.value) {
    expandedState.value = nextExpanded
  }

  emit('update:expanded', nextExpanded)
  emit('expandedChange', nextExpanded, table)
}

const table = useVueTable<TData>({
  data: computed(() => props.data),
  get enableMultiSort() {
    return props.enableMultiSort
  },
  get enableMultiRowSelection() {
    return props.multiple
  },
  get enableSorting() {
    return props.sortable
  },
  get enableSortingRemoval() {
    return props.enableSortingRemoval
  },
  enableRowSelection: row => props.selectable && Boolean(selectionColumnDef.value) && !isRowSelectionDisabled(row),
  get columns() {
    return resolvedColumns.value
  },
  get rowCount() {
    return props.data.length
  },
  getCoreRowModel: getCoreRowModel(),
  getExpandedRowModel: getExpandedRowModel(),
  getSortedRowModel: getSortedRowModel(),
  autoResetExpanded: false,
  getRowId: resolveRowId,
  getSubRows: resolveSubRows,
  get manualExpanding() {
    return props.manualExpanding
  },
  get manualSorting() {
    return props.manualSorting
  },
  get enableColumnResizing() {
    return props.resizable
  },
  columnResizeMode: 'onChange',
  onColumnVisibilityChange: handleColumnVisibilityChange,
  onColumnSizingChange: handleColumnSizingChange,
  onRowSelectionChange: handleRowSelectionChange,
  onExpandedChange: handleExpandedChange,
  onSortingChange: handleSortingChange,
  get sortDescFirst() {
    return props.sortDescFirst
  },
  state: {
    get columnVisibility() {
      return columnVisibilityState.value
    },
    get columnPinning() {
      return columnPinning.value
    },
    get columnSizing() {
      return columnSizingState.value
    },
    get rowSelection() {
      return rowSelection.value
    },
    get expanded() {
      return expandedState.value
    },
    get sorting() {
      return sortingState.value
    },
  },
})

function getTableRows(_data: TData[], _rowSelection: RowSelectionState, _columnVisibility: VisibilityState, _sorting: SortingState) {
  return table.getRowModel().rows
}

const selectedRows = computed(() => table.getSelectedRowModel().rows.map(row => row.original))
const tableRows = computed(() => getTableRows(props.data, rowSelection.value, columnVisibilityState.value, sortingState.value))
const visibleColumnCount = computed(() => {
  void columnVisibilityState.value
  return Math.max(table.getVisibleLeafColumns().length, 1)
})
const isSingleSelectionMode = computed(() => props.selectable && !props.multiple && Boolean(selectionColumnDef.value))
const singleSelectedRowId = computed(() => Object.keys(rowSelection.value).find(id => rowSelection.value[id]))
const tableRootComponent = computed(() => isSingleSelectionMode.value ? RadioGroup : 'div')
const tableRootModelValue = computed(() => isSingleSelectionMode.value ? singleSelectedRowId.value : undefined)
const stripeInteractionClass = computed(() => props.stripe ? '[&:hover:not([data-state=selected])>td]:bg-muted/50' : undefined)
/** 列定义里的 defaultHidden 决定初始可见性；用户勾选后不再被列定义覆盖 */
const defaultHiddenState = computed<VisibilityState>(() => {
  const state: VisibilityState = {}
  for (const column of resolvedColumns.value as TableColumn<TData, any>[]) {
    const id = column.id ?? (column as { accessorKey?: string }).accessorKey
    if (column.defaultHidden && typeof id === 'string' && id) {
      state[id] = false
    }
  }
  return state
})

const hasDefaultHiddenColumns = computed(() => Object.keys(defaultHiddenState.value).length > 0)

let visibilityInitialized = false
watch(defaultHiddenState, (state) => {
  if (visibilityInitialized) {
    return
  }
  visibilityInitialized = true
  columnVisibilityState.value = { ...state }
}, { immediate: true })

function showAllColumns() {
  const next: VisibilityState = {}
  for (const column of columnVisibilityColumns.value) {
    next[column.id] = true
  }
  columnVisibilityState.value = next
}

function showCommonColumns() {
  const next: VisibilityState = {}
  for (const column of columnVisibilityColumns.value) {
    // 默认隐藏列的状态是「显式 false」，其余未记录的列都算可见
    next[column.id] = defaultHiddenState.value[column.id] !== false
  }
  columnVisibilityState.value = next
}

const columnVisibilityColumns = computed(() => {
  void resolvedColumns.value
  void columnVisibilityState.value

  return table.getAllLeafColumns().filter(column => column.getCanHide())
})
const columnVisibilityMenuItems = computed(() => {
  const groups: Array<Array<{ type?: 'checkbox', label: string, checked?: boolean, handle?: (checked?: boolean) => void }>> = []
  if (hasDefaultHiddenColumns.value) {
    groups.push([
      { label: '显示全部字段', handle: () => showAllColumns() },
      { label: '只看常用字段', handle: () => showCommonColumns() },
    ])
  }
  groups.push(columnVisibilityColumns.value.length
    ? columnVisibilityColumns.value.map(column => ({
        type: 'checkbox' as const,
        label: getColumnVisibilityLabel(column),
        checked: column.getIsVisible(),
        handle: (checked?: boolean) => column.toggleVisibility(checked),
      }))
    : [])
  return groups
})

watch(
  () => props.multiple,
  () => {
    syncSingleRowSelection()
  },
  { immediate: true },
)

watch(
  () => props.sorting,
  (sorting) => {
    if (sorting !== undefined) {
      sortingState.value = sorting
    }
  },
  { deep: true },
)

watch(
  () => props.expanded,
  (expanded) => {
    if (expanded !== undefined) {
      expandedState.value = expanded
    }
  },
  { deep: true },
)

watch(
  [rowSelection, () => props.data],
  () => {
    emit('selectionChange', selectedRows.value, rowSelection.value)
  },
  { deep: true },
)

function isSelectionColumn(column: Column<TData, unknown>) {
  return isSelectionColumnDef(column.columnDef as TableColumn<TData, unknown>)
}

function getColumnSlotName(prefix: 'cell' | 'header' | 'footer', columnId: string): ColumnSlotName {
  return `${prefix}-${columnId}`
}

function getHeaderSlotName(header: Header<TData, unknown>) {
  return getColumnSlotName('header', header.column.id)
}

function getCellSlotName(cell: Cell<TData, unknown>) {
  return getColumnSlotName('cell', cell.column.id)
}

function hasColumnSlot(name: ColumnSlotName) {
  return Boolean(slots[name])
}

function getColumnVisibilityLabel(column: Column<TData, unknown>) {
  const columnDef = column.columnDef as TableColumn<TData, unknown>

  if (columnDef.label) {
    return columnDef.label
  }

  if (columnDef.title) {
    return columnDef.title
  }

  if (typeof columnDef.header === 'string' && columnDef.header) {
    return columnDef.header
  }

  return column.id
}

function getHeaderSlotProps(header: Header<TData, unknown>): any {
  return {
    column: header.column,
    header,
    table,
  }
}

function getHeaderAriaSort(header: Header<TData, unknown>) {
  if (!props.sortable || !header.column.getCanSort()) {
    return undefined
  }

  const sorted = header.column.getIsSorted()

  if (sorted === 'asc') {
    return 'ascending'
  }

  if (sorted === 'desc') {
    return 'descending'
  }

  return undefined
}

function getCellSlotProps(cell: Cell<TData, unknown>, row: Row<TData>): any {
  return {
    cell,
    column: cell.column,
    index: row.index,
    row,
    table,
    value: cell.getValue(),
  }
}

function shouldRenderTreeCell(cell: Cell<TData, unknown>, row: Row<TData>) {
  if (!props.tree || isSelectionColumn(cell.column)) {
    return false
  }

  return row.getVisibleCells().find(item => !isSelectionColumn(item.column))?.id === cell.id
}

function getTreeCellIndentStyle(row: Row<TData>): CSSProperties {
  return {
    paddingLeft: `${row.depth * props.indentSize}px`,
  }
}

function getTreeToggleIconName(row: Row<TData>) {
  return row.getIsExpanded() ? 'i-lucide:chevron-down' : 'i-lucide:chevron-right'
}

function handleToggleRowExpanded(row: Row<TData>) {
  row.toggleExpanded()
}

function getAlignClass(align?: TableAlign) {
  if (align === 'center') {
    return 'text-center'
  }

  if (align === 'right') {
    return 'text-right'
  }

  return undefined
}

function resolveSize(size?: number | string) {
  if (size == null || size === '') {
    return undefined
  }

  return typeof size === 'number' ? `${size}px` : size
}

function getColumnStyle(column: Column<TData, unknown>): CSSProperties | undefined {
  const columnDef = column.columnDef as TableColumn<TData, unknown>
  const pin = column.getIsPinned()
  // 拖动过该列后由 TanStack sizing 接管宽度；未拖动保持原 width 定义优先的既有行为
  const isResized = columnSizingState.value[column.id] !== undefined
  const width = isResized
    ? `${column.getSize()}px`
    : resolveSize(columnDef.width ?? (pin ? column.getSize() : columnDef.size && columnDef.size !== 150 ? columnDef.size : undefined))
  const minSize = columnDef.minSize && columnDef.minSize !== 20 ? columnDef.minSize : undefined
  const maxSize = columnDef.maxSize && columnDef.maxSize !== Number.MAX_SAFE_INTEGER ? columnDef.maxSize : undefined
  const minWidth = resolveSize(columnDef.minWidth ?? minSize)
  const maxWidth = resolveSize(columnDef.maxWidth ?? maxSize)
  const style: CSSProperties = {}

  if (width) {
    style.width = width
    style.minWidth = minWidth ?? width
  }
  else if (minWidth) {
    style.minWidth = minWidth
  }

  if (maxWidth) {
    style.maxWidth = maxWidth
  }

  if (pin === 'left') {
    style.left = `${column.getStart('left')}px`
    style.position = 'sticky'
  }
  else if (pin === 'right') {
    style.position = 'sticky'
    style.right = `${column.getAfter('right')}px`
  }

  if (!Object.keys(style).length) {
    return undefined
  }

  return style
}

function resolveColumnClass<TContext>(
  columnClass: ColumnClass<TContext> | undefined,
  context: TContext,
) {
  return typeof columnClass === 'function' ? columnClass(context) : columnClass
}

function getHeaderClass(header: Header<TData, unknown>) {
  const columnDef = header.column.columnDef as TableColumn<TData, unknown>
  return cn(
    'sticky z-2 bg-muted/60 text-muted-foreground after:pointer-events-none after:absolute after:inset-x-0 after:bottom-0 after:h-px after:bg-border after:content-empty',
    getAlignClass(columnDef.align),
    getHeaderBorderClass(header),
    getPinnedColumnClass(header.column, 'header'),
    header.column.getCanSort() && 'cursor-pointer select-none',
    getSortedColumnClass(header.column, 'header'),
    columnDef.class,
    resolveColumnClass(columnDef.headerClass, header.getContext()),
    isSelectionColumn(header.column) && cn('px-3', props.selectionColumnClass),
  )
}

function getHeaderStyle(header: Header<TData, unknown>, headerGroupIndex: number): CSSProperties {
  return {
    ...getColumnStyle(header.column),
    top: `${headerGroupIndex * TABLE_HEADER_ROW_HEIGHT}px`,
  }
}

function getHeaderContentClass(header: Header<TData, unknown>) {
  const columnDef = header.column.columnDef as TableColumn<TData, unknown>

  return cn(
    'inline-flex w-full items-center gap-1.5',
    columnDef.align === 'center' && 'justify-center',
    columnDef.align === 'right' && 'justify-end',
  )
}

function getCellClass(cell: Cell<TData, unknown>) {
  const columnDef = cell.column.columnDef as TableColumn<TData, unknown>
  return cn(
    getAlignClass(columnDef.align),
    getCellBorderClass(cell),
    getPinnedColumnClass(cell.column, 'cell'),
    getSortedColumnClass(cell.column, 'cell'),
    columnDef.class,
    resolveColumnClass(columnDef.cellClass, cell.getContext()),
    isSelectionColumn(cell.column) && cn('px-3', props.selectionColumnClass),
  )
}

function getSortedColumnClass(column: Column<TData, unknown>, area: 'cell' | 'header') {
  if (!props.sortable || !column.getIsSorted()) {
    return undefined
  }

  return area === 'header'
    ? '!bg-primary/20 text-foreground'
    : '!bg-primary/10'
}

function getPinnedColumnClass(column: Column<TData, unknown>, area: 'cell' | 'header') {
  const pin = column.getIsPinned()

  if (!pin) {
    return undefined
  }

  return cn(
    'sticky',
    area === 'header' ? 'z-3' : 'z-1',
    area === 'cell' && 'bg-background transition-colors',
    pin === 'left' && column.getIsLastColumn('left') && 'shadow-[4px_0_6px_-4px_rgb(0_0_0/0.18)]',
    pin === 'right' && column.getIsFirstColumn('right') && 'shadow-[-4px_0_6px_-4px_rgb(0_0_0/0.18)]',
  )
}

function isLastVisibleLeafColumn(column: Column<TData, unknown>) {
  return table.getVisibleLeafColumns().at(-1)?.id === column.id
}

function getNextVisibleLeafColumn(column: Column<TData, unknown>) {
  const columns = table.getVisibleLeafColumns()
  const columnIndex = columns.findIndex(item => item.id === column.id)
  return columnIndex === -1 ? undefined : columns[columnIndex + 1]
}

function getVisiblePinnedColumns(pin: TableColumnFixed) {
  return table.getVisibleLeafColumns().filter(column => column.getIsPinned() === pin)
}

function isFirstVisiblePinnedColumn(column: Column<TData, unknown>, pin: TableColumnFixed) {
  return getVisiblePinnedColumns(pin).at(0)?.id === column.id
}

function isLastVisiblePinnedColumn(column: Column<TData, unknown>, pin: TableColumnFixed) {
  return getVisiblePinnedColumns(pin).at(-1)?.id === column.id
}

function getHeaderBorderClass(header: Header<TData, unknown>) {
  if (!props.border) {
    return undefined
  }

  const leafColumns = header.column.getLeafColumns()
  const lastLeafColumn = leafColumns.at(-1) ?? header.column
  const pin = lastLeafColumn.getIsPinned()

  if (pin === 'left') {
    return cn(
      !isFirstVisiblePinnedColumn(lastLeafColumn, 'left')
      && 'before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:z-1 before:w-px before:bg-border before:content-empty',
      isLastVisiblePinnedColumn(lastLeafColumn, 'left') && !isLastVisibleLeafColumn(lastLeafColumn)
      && 'bg-[linear-gradient(to_left,oklch(var(--border))_1px,transparent_1px)]',
    )
  }

  if (pin === 'right') {
    return 'before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:z-1 before:w-px before:bg-border before:content-empty'
  }

  if (getNextVisibleLeafColumn(lastLeafColumn)?.getIsPinned() === 'right') {
    return undefined
  }

  return isLastVisibleLeafColumn(lastLeafColumn) ? undefined : 'border-r'
}

function getCellBorderClass(cell: Cell<TData, unknown>) {
  if (!props.border) {
    return undefined
  }

  const pin = cell.column.getIsPinned()

  if (pin === 'left') {
    return cn(
      !isFirstVisiblePinnedColumn(cell.column, 'left')
      && 'before:pointer-events-none before:absolute before:inset-y-0 before:left-0 before:z-1 before:w-px before:bg-border before:content-empty',
      isLastVisiblePinnedColumn(cell.column, 'left') && !isLastVisibleLeafColumn(cell.column)
      && 'after:pointer-events-none after:absolute after:inset-y-0 after:right-0 after:z-1 after:w-px after:bg-border after:content-empty',
    )
  }

  if (pin === 'right') {
    return 'after:pointer-events-none after:absolute after:inset-y-0 after:left-0 after:z-1 after:w-px after:bg-border after:content-empty'
  }

  if (getNextVisibleLeafColumn(cell.column)?.getIsPinned() === 'right') {
    return undefined
  }

  return isLastVisibleLeafColumn(cell.column) ? undefined : 'border-r'
}

function getRowClass(row: Row<TData>, rowIndex: number) {
  return cn(
    'bg-background',
    '[&:hover:not([data-state=selected])>td[data-pinned]]:bg-muted [&[data-state=selected]>td]:bg-primary/8 [&[data-state=selected]>td[data-pinned]]:bg-primary/8',
    props.stripe && rowIndex % 2 === 1 && '[&>td]:bg-muted/50',
    props.stripe && rowIndex % 2 === 1 && '[&>td[data-pinned]]:bg-muted',
    stripeInteractionClass.value,
    typeof props.rowClass === 'function' ? props.rowClass(row) : props.rowClass,
  )
}

function getHeaderCheckboxValue() {
  if (table.getIsAllRowsSelected()) {
    return true
  }

  if (table.getIsSomeRowsSelected()) {
    return 'indeterminate'
  }

  return false
}

function shouldRenderSelectionHeader(header: Header<TData, unknown>) {
  return props.selectable && props.multiple && isSelectionColumn(header.column) && !header.column.columnDef.header
}

function getSortIconName(header: Header<TData, unknown>) {
  const sorted = header.column.getIsSorted()

  if (sorted === 'desc') {
    return 'i-lucide:arrow-down'
  }

  if (sorted === 'asc') {
    return 'i-lucide:arrow-up'
  }

  return 'i-lucide:arrow-up-down'
}

function getSortIconClass(header: Header<TData, unknown>) {
  return cn(
    'shrink-0 size-3.5',
    header.column.getIsSorted() ? 'text-primary' : 'text-muted-foreground/80',
  )
}

function shouldRenderSortIndex(header: Header<TData, unknown>) {
  return props.enableMultiSort && header.column.getSortIndex() > -1 && sortingState.value.length > 1
}

function getSortIndexLabel(header: Header<TData, unknown>) {
  return header.column.getSortIndex() + 1
}

function handleHeaderClick(header: Header<TData, unknown>, event: MouseEvent) {
  if (!props.sortable || !header.column.getCanSort()) {
    return
  }

  header.column.getToggleSortingHandler()?.(event)
}

function handleToggleAllRows(value: boolean | 'indeterminate') {
  table.toggleAllRowsSelected(value === true)
}

function handleToggleRow(row: Row<TData>, value: boolean | 'indeterminate') {
  row.toggleSelected(value === true)
}

function handleSingleSelectionChange(value: unknown) {
  if (value == null) {
    return
  }

  rowSelection.value = {
    [String(value)]: true,
  }
}

function handleRowClick(row: Row<TData>, event: MouseEvent) {
  emit('rowClick', row.original, row.index, event)
}

defineExpose({
  table,
})
</script>

<template>
  <div :class="cn('flex flex-col gap-2', props.class)">
    <div v-if="hasToolbar" class="flex flex-wrap gap-2 items-center">
      <div class="flex flex-1 gap-2 min-w-0 items-center">
        <slot name="toolbar" :table="table" />
      </div>
      <!-- 未被布局传送时列显隐留在自身工具栏行（与页面操作栏同排的场景见下方 Teleport 分支） -->
      <Dropdown
        v-if="props.columnVisibility && !columnVisibilityTarget"
        :items="columnVisibilityMenuItems"
        align="end"
      >
        <Button
          variant="outline"
          size="icon"
          :disabled="!columnVisibilityColumns.length"
        >
          <Icon name="i-lucide:columns-3" />
        </Button>
      </Dropdown>
    </div>
    <!-- 列显隐并入 YdTablePageLayout 操作栏末尾（与操作按钮同排且最后）；
         defer：目标容器与表格同轮渲染，需等整棵树插入文档后再解析目标 -->
    <Teleport
      v-if="props.columnVisibility && columnVisibilityTarget"
      :to="columnVisibilityTarget"
      defer
    >
      <Dropdown :items="columnVisibilityMenuItems" align="end">
        <Button
          variant="outline"
          size="icon"
          :disabled="!columnVisibilityColumns.length"
          title="显示字段"
        >
          <Icon name="i-lucide:columns-3" />
        </Button>
      </Dropdown>
    </Teleport>
    <component
      :is="tableRootComponent"
      :class="cn('size-full', props.border && 'border', props.tableRootClass)"
      style="overflow-x: auto"
      :model-value="tableRootModelValue"
      @update:model-value="handleSingleSelectionChange"
    >
      <Table :class="cn(tableClass, isResizeLayout && 'table-fixed', !tableRows.length && 'h-full')">
        <slot name="caption">
          <TableCaption v-if="caption">
            {{ caption }}
          </TableCaption>
        </slot>

        <TableHeader :class="headerClass">
          <TableRow
            v-for="(headerGroup, headerGroupIndex) in table.getHeaderGroups()"
            :key="headerGroup.id"
            :class="cn('border-b-0', headerRowClass)"
          >
            <TableHead
              v-for="header in headerGroup.headers"
              :key="header.id"
              :colspan="header.colSpan"
              :class="getHeaderClass(header)"
              :style="getHeaderStyle(header, headerGroupIndex)"
              :aria-sort="getHeaderAriaSort(header)"
              :data-sortable="props.sortable && header.column.getCanSort() ? '' : undefined"
              @click="handleHeaderClick(header, $event)"
            >
              <template v-if="!header.isPlaceholder">
                <div :class="getHeaderContentClass(header)">
                  <slot
                    v-if="hasColumnSlot(getHeaderSlotName(header))"
                    :name="getHeaderSlotName(header)"
                    v-bind="getHeaderSlotProps(header)"
                  />
                  <Checkbox
                    v-else-if="shouldRenderSelectionHeader(header)"
                    aria-label="选择本页所有可选行"
                    :model-value="getHeaderCheckboxValue()"
                    class="translate-y-[2px]"
                    @click.stop
                    @update:model-value="handleToggleAllRows"
                  />
                  <FlexRender
                    v-else
                    :render="header.column.columnDef.header"
                    :props="header.getContext()"
                  />
                  <template v-if="props.sortable && header.column.getCanSort()">
                    <Icon :name="getSortIconName(header)" :class="getSortIconClass(header)" />
                    <span
                      v-if="shouldRenderSortIndex(header)"
                      class="text-[10px] text-muted-foreground leading-none font-medium rounded-full bg-muted inline-flex size-4 items-center justify-center"
                    >
                      {{ getSortIndexLabel(header) }}
                    </span>
                  </template>
                </div>
              </template>
              <!-- 列宽拖动手柄：置于表头内容之上，拖动结束的点击不应触发排序 -->
              <div
                v-if="props.resizable && header.column.getCanResize()"
                class="w-1.5 cursor-col-resize select-none inset-y-0 right-0 absolute z-10 touch-none hover:bg-primary/40"
                :class="header.column.getIsResizing() && 'bg-primary/60'"
                aria-hidden="true"
                @click.stop
                @mousedown.stop="header.getResizeHandler()?.($event)"
                @touchstart.stop="header.getResizeHandler()?.($event)"
              />
            </TableHead>
          </TableRow>
        </TableHeader>

        <TableBody :class="bodyClass">
          <template v-if="tableRows.length">
            <TableRow
              v-for="(row, rowIndex) in tableRows"
              :key="row.id"
              :class="getRowClass(row, rowIndex)"
              :data-row-key="resolveRowId(row.original, rowIndex, row)"
              :data-state="row.getIsSelected() ? 'selected' : undefined"
              @click="handleRowClick(row, $event)"
            >
              <TableCell
                v-for="cell in row.getVisibleCells()"
                :key="cell.id"
                :class="getCellClass(cell)"
                :style="getColumnStyle(cell.column)"
                :data-pinned="cell.column.getIsPinned() || undefined"
              >
                <div
                  v-if="shouldRenderTreeCell(cell, row)"
                  class="flex gap-1.5 min-w-0 items-center"
                  :style="getTreeCellIndentStyle(row)"
                >
                  <Button
                    v-if="row.getCanExpand()"
                    type="button"
                    variant="ghost"
                    size="icon"
                    class="shrink-0 size-6"
                    @click.stop="handleToggleRowExpanded(row)"
                  >
                    <Icon :name="getTreeToggleIconName(row)" class="size-3.5" />
                  </Button>
                  <span v-else class="shrink-0 size-6" aria-hidden="true" />
                  <div class="flex-1 min-w-0">
                    <slot
                      v-if="hasColumnSlot(getCellSlotName(cell))"
                      :name="getCellSlotName(cell)"
                      v-bind="getCellSlotProps(cell, row)"
                    />
                    <template v-else-if="isSelectionColumn(cell.column) && selectable">
                      <Checkbox
                        v-if="multiple"
                        :aria-label="`选择第 ${row.index + 1} 行`"
                        :model-value="row.getIsSelected()"
                        :disabled="!row.getCanSelect()"
                        class="translate-y-[2px]"
                        @click.stop
                        @update:model-value="value => handleToggleRow(row, value)"
                      />
                      <RadioGroupItem
                        v-else
                        :aria-label="`选择第 ${row.index + 1} 行`"
                        :value="row.id"
                        :disabled="!row.getCanSelect()"
                        class="translate-y-[2px]"
                        @click.stop
                      />
                    </template>
                    <FlexRender
                      v-else
                      :render="cell.column.columnDef.cell"
                      :props="cell.getContext()"
                    />
                  </div>
                </div>
                <template v-else>
                  <slot
                    v-if="hasColumnSlot(getCellSlotName(cell))"
                    :name="getCellSlotName(cell)"
                    v-bind="getCellSlotProps(cell, row)"
                  />
                  <template v-else-if="isSelectionColumn(cell.column) && selectable">
                    <Checkbox
                      v-if="multiple"
                      :aria-label="`选择第 ${row.index + 1} 行`"
                      :model-value="row.getIsSelected()"
                      :disabled="!row.getCanSelect()"
                      class="translate-y-[2px]"
                      @click.stop
                      @update:model-value="value => handleToggleRow(row, value)"
                    />
                    <RadioGroupItem
                      v-else
                      :aria-label="`选择第 ${row.index + 1} 行`"
                      :value="row.id"
                      :disabled="!row.getCanSelect()"
                      class="translate-y-[2px]"
                      @click.stop
                    />
                  </template>
                  <FlexRender
                    v-else
                    :render="cell.column.columnDef.cell"
                    :props="cell.getContext()"
                  />
                </template>
              </TableCell>
            </TableRow>
          </template>
          <TableEmpty v-else :colspan="visibleColumnCount">
            <slot name="empty" :table="table">
              <span class="text-muted-foreground">{{ emptyText }}</span>
            </slot>
          </TableEmpty>
        </TableBody>
      </Table>
    </component>
  </div>
</template>
