<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import { ref } from 'vue'
import { FaButton, FaTable, FaTag } from '@yudream/components'

type User = { id: string, name: string, status: '启用' | '禁用' }
const users = ref<User[]>([{ id: '1', name: 'Steve', status: '启用' }, { id: '2', name: 'Alex', status: '禁用' }])
const selectedCount = ref(0)
const columns: TableColumn<User>[] = [
  { type: 'selection', width: 44 },
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'status', header: '状态' },
]
function addUser() {
  const id = String(users.value.length + 1)
  // FaTable 会以 data 引用变更重建 TanStack Table；不可原地 push，否则新增行不会刷新。
  users.value = [...users.value, { id, name: `Mock User ${id}`, status: '启用' }]
}
</script>

<template>
  <FaTable :columns="columns" :data="users" selectable multiple sortable stripe border row-key="id" column-visibility @selection-change="rows => selectedCount = rows.length">
    <template #toolbar><FaButton size="sm" @click="addUser">新增本地数据</FaButton><span class="text-sm text-muted-foreground">已选 {{ selectedCount }} 项；点击表头可排序。</span></template>
    <template #cell-status="{ value }"><FaTag :variant="value === '启用' ? 'default' : 'secondary'">{{ value }}</FaTag></template>
  </FaTable>
</template>
