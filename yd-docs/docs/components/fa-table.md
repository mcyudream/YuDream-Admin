<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingFormTable from '../.vitepress/theme/components/demos/InteractiveRemainingFormTable.vue'

const srcBasic = `<script setup>
import { FaButton, FaTable } from '@yudream/components'
import { ref } from 'vue'

interface User { id: string; name: string; status: string }

const list = ref<User[]>([
  { id: '1', name: 'Steve', status: '启用' },
  { id: '2', name: 'Alex', status: '禁用' },
])

const columns = [
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'status', header: '状态' },
  { id: 'actions', header: '操作', cell: ({ row }) => h('span') },
]

function onSelect(rows: User[]) {
  console.log('选中', rows)
}
<\/script>

<template>
  <FaTable
    :columns="columns"
    :data="list"
    selectable
    multiple
    sortable
    stripe
    row-key="id"
    @selection-change="onSelect"
  >
    <template #toolbar>
      <FaButton size="sm">新增</FaButton>
    </template>
    <template #cell-status="{ value }">
      <FaTag>{{ value }}</FaTag>
    </template>
  </FaTable>
</template>`

const props = [
  ['<code>columns</code>', '<code>TableColumn[]</code>', '<code>[]</code>', '列定义（TanStack ColumnDef 扩展）'],
  ['<code>data</code>', '<code>TData[]</code>', '<code>[]</code>', '数据源（泛型组件）'],
  ['<code>selectable</code>', '<code>boolean</code>', '<code>false</code>', '显示选择列'],
  ['<code>multiple</code>', '<code>boolean</code>', '<code>false</code>', '多选（checkbox）；否则单选（radio）'],
  ['<code>sortable</code>', '<code>boolean</code>', '<code>false</code>', '可排序'],
  ['<code>stripe</code>', '<code>boolean</code>', '<code>false</code>', '斑马纹'],
  ['<code>border</code>', '<code>boolean</code>', '<code>false</code>', '边框'],
  ['<code>tree</code>', '<code>boolean</code>', '<code>false</code>', '树形数据'],
  ['<code>rowKey</code>', '<code>string</code>', '—', '行键'],
  ['<code>emptyText</code>', '<code>string</code>', "<code>'暂无数据'</code>", '空文案'],
  ['<code>indentSize</code>', '<code>number</code>', '<code>20</code>', '树形缩进宽度'],
  ['<code>columnVisibility</code>', '<code>Record<string, boolean></code>', '—', '列显隐下拉'],
  ['<code>sortDescFirst</code>', '<code>boolean</code>', '<code>true</code>', '降序优先'],
  ['<code>getRowId</code> / <code>getSubRows</code>', '<code>(row) => …</code>', '—', '自定义行 ID / 子行'],
  ['<code>manualExpanding</code>', '<code>boolean</code>', '<code>false</code>', '手动控制展开'],
]

const emits = [
  ['<code>selectionChange</code>', '<code>(rows, rowSelection) => void</code>', '选择变化'],
  ['<code>sortingChange</code>', '<code>(sorting, table) => void</code>', '排序变化'],
  ['<code>expandedChange</code>', '—', '展开状态变化'],
  ['<code>rowClick</code>', '<code>(row, index, event) => void</code>', '行点击'],
]
</script>

# FaTable 表格

基于 TanStack Table 的泛型表格组件，支持选择、排序、树形、列显隐与完全自定义单元格。

## 基础用法

<Demo title="列表 + 选择 + 工具栏" description="toolbar 插槽放操作按钮，cell-{id} 插槽自定义单元格" :source="srcBasic">
  <ClientOnly><InteractiveRemainingFormTable /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaTable Props" :data="props" />

### Emits

<ApiTable title="FaTable Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Slots

| 插槽 | 参数 | 说明 |
| --- | --- | --- |
| `toolbar` | `{ table }` | 表格上方工具栏 |
| `caption` | — | 表格标题 |
| `empty` | `{ table }` | 自定义空态 |
| `cell-{id}` | `{ cell, column, row, index, value, table }` | 自定义单元格 |
| `header-{id}` | — | 自定义表头单元格 |
| `footer-{id}` | — | 自定义表尾单元格 |

### Expose

| 成员 | 说明 |
| --- | --- |
| `table` | TanStack Table 实例 |

### TableColumn 扩展字段

在 TanStack `ColumnDef` 基础上扩展：`label` / `title` / `align` / `fixed('left'\|'right')` / `width` / `minWidth` / `maxWidth` / `cellClass` / `headerClass` / `class`；选择列写法：`{ type: 'selection', disabled?(row, index), width }`。

::: tip 相关组件
管理页常用组合：`FaSearchBar`（筛选区）+ `FaTable` + `FaPagination`（分页）；移动端自适应用 `FaResponsiveTable`；树形展开配合 `defaultExpanded` / `update:expanded`。
:::
