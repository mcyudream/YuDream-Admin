<script setup lang="ts">
import { ref } from 'vue'
import { YdResponsiveEnhancedTable } from '@yudream/components'
import type { ActionMenuItem, EnhancedTableProps, StatusColumn, UserColumn } from '@yudream/components'

interface User {
  id: string
  name: string
  email: string
  avatar: string
  role: string
  status: 'active' | 'inactive' | 'pending'
  department: string
  joinDate: string
}

const data = ref<User[]>([
  {
    id: '1',
    name: '张三',
    email: 'zhangsan@example.com',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhang',
    role: '产品经理',
    status: 'active',
    department: '产品部',
    joinDate: '2023-01-15',
  },
  {
    id: '2',
    name: '李四',
    email: 'lisi@example.com',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Li',
    role: '前端工程师',
    status: 'active',
    department: '研发部',
    joinDate: '2023-03-20',
  },
  {
    id: '3',
    name: '王五',
    email: 'wangwu@example.com',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Wang',
    role: '后端工程师',
    status: 'inactive',
    department: '研发部',
    joinDate: '2022-11-10',
  },
  {
    id: '4',
    name: '赵六',
    email: 'zhaoliu@example.com',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Zhao',
    role: 'UI 设计师',
    status: 'pending',
    department: '设计部',
    joinDate: '2024-01-05',
  },
  {
    id: '5',
    name: '孙七',
    email: 'sunqi@example.com',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Sun',
    role: '测试工程师',
    status: 'active',
    department: '质量部',
    joinDate: '2023-07-18',
  },
])

const userColumn: UserColumn<User> = {
  nameKey: 'name',
  emailKey: 'email',
  avatarKey: 'avatar',
  header: '用户',
  width: 240,
}

const statusColumn: StatusColumn<User> = {
  statusKey: 'status',
  header: '状态',
  statusMap: {
    active: { label: '在职', variant: 'default' },
    inactive: { label: '离职', variant: 'secondary' },
    pending: { label: '待入职', variant: 'destructive' },
  },
  width: 100,
}

const columns: EnhancedTableProps<User>['columns'] = [
  {
    accessorKey: 'role',
    header: '职位',
    width: 150,
  },
  {
    accessorKey: 'department',
    header: '部门',
    width: 120,
  },
  {
    accessorKey: 'joinDate',
    header: '入职日期',
    width: 120,
  },
]

const actions: ActionMenuItem<User>[] = [
  {
    label: '查看详情',
    icon: 'i-heroicons-eye',
    onClick: (row: User) => {
      console.log('查看详情', row)
      alert(`查看 ${row.name} 的详情`)
    },
  },
  {
    label: '编辑',
    icon: 'i-heroicons-pencil',
    onClick: (row: User) => {
      console.log('编辑', row)
      alert(`编辑 ${row.name}`)
    },
  },
  {
    label: '删除',
    icon: 'i-heroicons-trash',
    danger: true,
    disabled: (row: User) => row.status === 'pending',
    onClick: (row: User) => {
      console.log('删除', row)
      if (confirm(`确定删除 ${row.name} 吗?`)) {
        data.value = data.value.filter(item => item.id !== row.id)
      }
    },
  },
]

function onSelectionChange(rows: User[]) {
  console.log('选中的行:', rows)
}
</script>

<template>
  <div class="p-6">
    <div class="mb-6">
      <h1 class="text-2xl font-bold mb-2">响应式增强表格</h1>
      <p class="text-muted-foreground">
        在桌面端显示为增强表格,在移动端自动切换为优化的卡片列表。调整浏览器窗口宽度查看效果。
      </p>
    </div>

    <YdResponsiveEnhancedTable
      :data="data"
      :user-column="userColumn"
      :status-column="statusColumn"
      :columns="columns"
      :actions="actions"
      :selectable="true"
      :multiple="true"
      mobile-breakpoint="(max-width: 1024px)"
      @selection-change="onSelectionChange"
    />
  </div>
</template>
