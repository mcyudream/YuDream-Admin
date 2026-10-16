<script setup lang="ts" generic="TData extends RowData = RowData">
import type { RowData, RowSelectionState, Table as TanStackTable } from '@tanstack/vue-table'
import type { TableColumn } from '../basic/table/index.vue'
import type { BadgeVariant, EnhancedTableProps } from './YdEnhancedTable.types'
import { computed, ref } from 'vue'
import FaAvatar from '../basic/avatar/index.vue'
import { Badge } from '../basic/badge/badge'
import FaButton from '../basic/button/index.vue'
import FaDropdown from '../basic/dropdown/index.vue'
import FaIcon from '../basic/icon/index.vue'
import FaTable from '../basic/table/index.vue'

const props = withDefaults(defineProps<EnhancedTableProps<TData>>(), {
  columnVisibility: true,
  resizable: true,
  showActions: true,
  actionsHeader: '操作',
  actionsWidth: 80,
  sortable: true,
})

const emit = defineEmits<{ selectionChange: [rows: TData[], selection: RowSelectionState] }>()

/** 桌面表格实例，供响应式版本读取引擎行与可见单元格 */
const tableRef = ref<{ table: TanStackTable<TData> }>()

defineExpose({
  get table() {
    return tableRef.value?.table
  },
})

/** 构建完整的列配置 */
const fullColumns = computed<TableColumn<TData, any>[]>(() => {
  const cols: TableColumn<TData, any>[] = []

  // 用户信息列（默认固定在左侧，fixed === false 时取消固定）
  if (props.userColumn) {
    cols.push({
      id: 'user-info',
      header: props.userColumn.header,
      accessorKey: props.userColumn.nameKey as string,
      width: props.userColumn.width || 280,
      cellClass: 'py-3',
      fixed: props.userColumn.fixed ?? 'left',
    })
  }

  // 普通列
  if (props.columns) {
    cols.push(...props.columns)
  }

  // 状态列
  if (props.statusColumn) {
    cols.push({
      id: 'status',
      header: props.statusColumn.header,
      accessorKey: props.statusColumn.statusKey as string,
      width: props.statusColumn.width || 120,
    })
  }

  // 操作列
  if (props.showActions && props.actions && props.actions.length > 0) {
    cols.push({
      id: 'actions',
      header: props.actionsHeader,
      width: props.actionsWidth,
      fixed: 'right',
      enableSorting: false,
      enableHiding: false,
      cell: () => '', // 通过插槽渲染
    })
  }

  return cols
})

/** 获取头像 URL */
function getAvatarUrl(row: TData): string | undefined {
  if (!props.userColumn?.avatarKey) {
    return undefined
  }
  const value = (row as Record<string, any>)[props.userColumn.avatarKey as string]
  return value != null ? String(value) : undefined
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
function getStatusConfig(row: TData): { label: string, variant: BadgeVariant } | undefined {
  if (!props.statusColumn) {
    return undefined
  }
  const value = String((row as Record<string, any>)[props.statusColumn.statusKey as string] || '')
  return props.statusColumn.statusMap[value]
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
  // Dropdown 需要二维数组,每个子数组是一组菜单项
  return [items]
}

/** 菜单项全部被行级可见性过滤时不渲染触发按钮 */
function hasActionItems(groups: DropdownMenuItem[][]) {
  return groups.some(group => group.length > 0)
}
</script>

<template>
  <FaTable
    ref="tableRef"
    v-bind="$props"
    :columns="fullColumns"
    class="yd-enhanced-table"
    @selection-change="(rows, selection) => emit('selectionChange', rows, selection)"
  >
    <!-- 用户信息单元格 -->
    <template v-if="userColumn" #cell-user-info="{ row }">
      <div class="flex gap-3 items-center">
        <FaAvatar
          v-if="userColumn.avatarKey"
          :src="getAvatarUrl(row.original) || ''"
          :fallback="avatarFallback(row.original)"
          :alt="getUserName(row.original)"
          class="shrink-0"
        />
        <div class="flex flex-col gap-1 min-w-0">
          <span class="text-sm text-foreground font-medium truncate">
            {{ getUserName(row.original) }}
          </span>
          <span v-if="userColumn.emailKey" class="text-xs text-muted-foreground truncate">
            {{ getUserEmail(row.original) }}
          </span>
        </div>
      </div>
    </template>

    <!-- 状态单元格 -->
    <template v-if="statusColumn" #cell-status="{ row }">
      <Badge
        v-if="getStatusConfig(row.original)"
        :variant="getStatusConfig(row.original)!.variant"
        class="font-medium"
      >
        {{ getStatusConfig(row.original)!.label }}
      </Badge>
    </template>

    <!-- 操作单元格 -->
    <template v-if="showActions && actions && actions.length > 0" #cell-actions="{ row }">
      <div v-if="hasActionItems(buildActionItems(row.original))" class="yd-enhanced-table__actions">
        <FaDropdown :items="buildActionItems(row.original)" align="end">
          <FaButton
            variant="ghost"
            size="sm"
            class="yd-enhanced-table__actions-trigger"
            aria-label="操作菜单"
          >
            <FaIcon name="morph:ellipsis" class="size-4" />
          </FaButton>
        </FaDropdown>
      </div>
    </template>

    <!-- 转发其他插槽 -->
    <template v-for="(_, name) in $slots" :key="name" #[name]="slotProps">
      <slot :name="name" v-bind="slotProps || {}" />
    </template>
  </FaTable>
</template>

<style scoped>
.yd-enhanced-table {
  /** 表格现代化样式增强 */
  --table-border-color: var(--color-border-2);
  --table-hover-bg: var(--color-fill-1);
  --table-header-bg: var(--color-fill-1);
}

.yd-enhanced-table :deep(.yd-enhanced-table__actions) {
  display: flex;
  justify-content: flex-end;
}

.yd-enhanced-table :deep(.yd-enhanced-table__actions-trigger) {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: var(--color-text-2);
  border-radius: 6px;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.yd-enhanced-table :deep(.yd-enhanced-table__actions-trigger:hover),
.yd-enhanced-table :deep(.yd-enhanced-table__actions-trigger:focus-visible) {
  color: var(--color-text-1);
  background-color: var(--color-fill-2);
}

.yd-enhanced-table :deep(.yd-enhanced-table__actions-trigger svg) {
  width: 16px;
  height: 16px;
  stroke-width: 2.2;
}

.yd-enhanced-table :deep(thead) {
  background-color: var(--table-header-bg);
}

.yd-enhanced-table :deep(tbody tr) {
  transition: background-color 0.15s ease;
}

.yd-enhanced-table :deep(tbody tr:hover) {
  background-color: var(--table-hover-bg);
}

.yd-enhanced-table :deep(th),
.yd-enhanced-table :deep(td) {
  border-color: var(--table-border-color);
}

/** 单元格最小高度保证图文舒适间距 */
.yd-enhanced-table :deep(td) {
  min-height: 64px;
  padding: 12px 16px;
}

.yd-enhanced-table :deep(th) {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-2);
  text-transform: uppercase;
  letter-spacing: 0.025em;
}
</style>
