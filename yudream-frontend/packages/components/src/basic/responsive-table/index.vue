<script setup lang="ts" generic="TData extends RowData = RowData">
import type { RowData } from '@tanstack/vue-table'
import { useMediaQuery } from '@vueuse/core'
import { computed, useSlots } from 'vue'
import FaCard from '../card/index.vue'
import type { TableColumn, TableProps } from '../table/index.vue'

const props = withDefaults(defineProps<TableProps<TData> & {
  /** 移动端断点，视口宽度小于等于该值时切换为卡片列表 */
  mobileBreakpoint?: string
}>(), {
  mobileBreakpoint: '(max-width: 768px)',
})

const slots = useSlots()
const isMobile = useMediaQuery(() => props.mobileBreakpoint || '(max-width: 768px)')

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
const tableProps = computed(() => {
  const { mobileBreakpoint: _breakpoint, ...rest } = props
  return rest as TableProps<TData>
})

/** 移动端兜底卡片可展示的列（仅支持有 accessorKey 的列） */
const cardColumns = computed(() => props.columns.filter(column => 'accessorKey' in column && column.accessorKey))

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
    <FaTable v-if="!isMobile" v-bind="tableProps">
      <template v-for="(_, name) in tableSlots" :key="name" #[name]="scope">
        <slot :name="name" v-bind="scope || {}" />
      </template>
    </FaTable>

    <div v-else class="flex flex-col gap-3">
      <slot name="toolbar" />
      <FaCard v-if="data.length === 0" class="w-full">
        <div class="flex items-center justify-center py-10 text-sm text-secondary-foreground/50">
          {{ emptyText || '暂无数据' }}
        </div>
      </FaCard>
      <div v-for="(row, index) in data" :key="index" class="w-full">
        <slot name="card" :row="row" :index="index">
          <FaCard class="w-full">
            <div class="flex flex-col gap-2">
              <div v-for="column in cardColumns" :key="columnKey(column)" class="flex items-start justify-between gap-3 text-sm">
                <span class="shrink-0 text-secondary-foreground/60">{{ columnLabel(column) }}</span>
                <span class="break-all text-right font-medium">{{ rowValue(row, column) }}</span>
              </div>
            </div>
          </FaCard>
        </slot>
      </div>
    </div>
  </div>
</template>
