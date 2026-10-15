# YdEnhancedTable 图文表格组件

## 概述

`YdEnhancedTable` 是在 `FaTable` 基础上构建的增强表格组件,专门用于需要图文并茂展示的数据列表场景。它内置了用户信息列、状态徽章和操作下拉菜单的支持,让常见的管理列表更简洁、更美观。

## 何时使用

- 用户列表、成员管理等需要显示头像+姓名+邮箱的场景
- 需要直观展示状态(活跃、停用、待审核等)的数据列表
- 操作按钮较多,希望收纳到下拉菜单以保持界面整洁

## 基础用法

```vue
<script setup lang="ts">
import { YdEnhancedTable } from '@yudream/components'
import type { ActionMenuItem, StatusColumn, UserColumn } from '@yudream/components'

interface User {
  id: string
  username: string
  name: string
  email: string
  phone: string
  status: 'active' | 'suspended' | 'invited'
  role: string
  avatar?: string
}

const users = ref<User[]>([
  {
    id: '1',
    username: 'citlalli.spinka45',
    name: 'Citlalli Spinka',
    email: 'citlalli_marks79@hotmail.com',
    phone: '+17593407614',
    status: 'suspended',
    role: 'Manager',
    avatar: '/avatars/user1.jpg',
  },
  // ... 更多数据
])

// 用户信息列配置
const userColumn: UserColumn<User> = {
  nameKey: 'name',
  emailKey: 'email',
  avatarKey: 'avatar',
  header: 'Name',
  width: 300,
}

// 状态列配置
const statusColumn: StatusColumn<User> = {
  statusKey: 'status',
  header: 'Status',
  statusMap: {
    'active': { label: 'Active', variant: 'success' },
    'suspended': { label: 'Suspended', variant: 'danger' },
    'invited': { label: 'Invited', variant: 'default' },
  },
  width: 120,
}

// 操作菜单
const actions: ActionMenuItem<User>[] = [
  {
    label: '编辑',
    icon: 'morph:edit',
    onClick: (row) => console.log('编辑', row),
  },
  {
    label: '删除',
    icon: 'morph:trash',
    danger: true,
    onClick: (row) => console.log('删除', row),
  },
]

// 其他普通列
const columns = [
  {
    accessorKey: 'phone',
    header: 'Phone Number',
  },
  {
    accessorKey: 'role',
    header: 'Role',
  },
]
</script>

<template>
  <YdEnhancedTable
    :data="users"
    :user-column="userColumn"
    :status-column="statusColumn"
    :columns="columns"
    :actions="actions"
    border
    stripe
  />
</template>
```

## API

### Props

继承 `FaTable` 的所有 props,并扩展以下配置:

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| userColumn | `UserColumn<TData>` | - | 用户信息列配置,包含头像+姓名+邮箱的组合展示 |
| statusColumn | `StatusColumn<TData>` | - | 状态列配置,使用徽章展示状态 |
| columns | `TableColumn<TData, any>[]` | - | 其他普通列,使用 TanStack Table 的列定义 |
| actions | `ActionMenuItem<TData>[]` | - | 操作菜单项列表 |
| showActions | `boolean` | `true` | 是否显示操作列 |
| actionsHeader | `string` | `'操作'` | 操作列标题 |
| actionsWidth | `number \| string` | `80` | 操作列宽度 |

### UserColumn 类型

```typescript
interface UserColumn<TData> {
  /** 用户名字段 */
  nameKey: keyof TData
  /** 用户邮箱字段(可选) */
  emailKey?: keyof TData
  /** 用户头像字段(可选) */
  avatarKey?: keyof TData
  /** 列标题 */
  header: string
  /** 列宽度 */
  width?: number | string
}
```

### StatusColumn 类型

```typescript
interface StatusColumn<TData> {
  /** 状态字段 */
  statusKey: keyof TData
  /** 列标题 */
  header: string
  /** 状态映射:值 -> {label, variant} */
  statusMap: Record<string, { 
    label: string
    variant: 'default' | 'success' | 'warning' | 'danger' 
  }>
  /** 列宽度 */
  width?: number | string
}
```

### ActionMenuItem 类型

```typescript
interface ActionMenuItem<TData> {
  /** 菜单项标签 */
  label: string
  /** 图标(Morphicons/Iconify) */
  icon?: string
  /** 是否危险操作 */
  danger?: boolean
  /** 是否禁用 */
  disabled?: boolean | ((row: TData) => boolean)
  /** 点击回调 */
  onClick: (row: TData) => void
}
```

## 高级用法

### 动态禁用操作

```vue
<script setup lang="ts">
const actions: ActionMenuItem<User>[] = [
  {
    label: '编辑',
    icon: 'morph:edit',
    disabled: (row) => row.status === 'suspended',
    onClick: (row) => editUser(row),
  },
  {
    label: '删除',
    icon: 'morph:trash',
    danger: true,
    disabled: (row) => row.role === 'Admin', // 不能删除管理员
    onClick: (row) => deleteUser(row),
  },
]
</script>
```

### 自定义单元格

`YdEnhancedTable` 继承了 `FaTable` 的插槽系统,你可以覆盖任意列的渲染:

```vue
<template>
  <YdEnhancedTable
    :data="users"
    :user-column="userColumn"
    :columns="columns"
  >
    <!-- 自定义 phone 列 -->
    <template #cell-phone="{ value }">
      <a :href="`tel:${value}`" class="text-primary hover:underline">
        {{ value }}
      </a>
    </template>
  </YdEnhancedTable>
</template>
```

### 响应式移动端

结合 `YdResponsiveTable` 使用,在移动端自动切换为卡片视图:

```vue
<script setup lang="ts">
import { YdResponsiveTable } from '@yudream/components'

// 包装 YdEnhancedTable 到 YdResponsiveTable 的 default 插槽
</script>

<template>
  <YdResponsiveTable
    :data="users"
    :columns="fullColumns"
  >
    <!-- 移动端卡片视图 -->
    <template #card="{ row }">
      <div class="flex items-center gap-3 p-4">
        <FaAvatar :src="row.avatar" :alt="row.name" />
        <div class="flex-1">
          <div class="font-medium">{{ row.name }}</div>
          <div class="text-sm text-muted-foreground">{{ row.email }}</div>
        </div>
        <FaBadge :variant="getStatusVariant(row.status)">
          {{ row.status }}
        </FaBadge>
      </div>
    </template>
  </YdResponsiveTable>
</template>
```

## 视觉规范

### 表格样式

- **表头**: 浅灰背景 (`--color-fill-1`),加粗字体,大写字母,字间距 0.025em
- **行悬停**: 浅色高亮 (`--color-fill-1`),过渡动画 0.15s
- **单元格**: 垂直内边距 12px,水平 16px,最小高度 64px
- **边框**: 统一使用 `--color-border-2`

### 间距与尺寸

- 头像尺寸: `md` (40x40px)
- 用户信息列默认宽度: 280px
- 状态列默认宽度: 120px
- 操作列默认宽度: 80px
- 头像与文字间距: 12px (gap-3)
- 姓名与邮箱间距: 4px (gap-1)

### 主题适配

组件完全使用语义化 CSS 变量,自动适配:
- 三套外观(灵动/雅致/紧凑)
- 明暗主题
- 插件主题变量覆盖

## 注意事项

1. **ID 精度**: 所有 ID 字段(如 `userId`)必须保持字符串类型,禁止转换为 Number
2. **图标优先级**: 优先使用 Morphicons (`morph:*`),缺失时回退到 Iconify
3. **操作确认**: 危险操作(如删除)应在 onClick 中弹出二次确认,不在组件层面处理
4. **权限控制**: 通过 `disabled` 函数实现权限判断,隐藏菜单项应在 `actions` 数组生成时过滤
5. **无障碍**: 操作按钮已设置 `aria-label`,自定义单元格也需保证键盘可访问

## 迁移指南

### 从 Arco Table 迁移

```vue
<!-- 旧代码: Arco Table -->
<a-table :data="users" :columns="arcoColumns">
  <template #operations="{ record }">
    <a-button @click="edit(record)">编辑</a-button>
    <a-button @click="delete(record)">删除</a-button>
  </template>
</a-table>

<!-- 新代码: YdEnhancedTable -->
<YdEnhancedTable
  :data="users"
  :user-column="userColumn"
  :columns="columns"
  :actions="actions"
/>
```

**关键变更**:
- `a-table` → `YdEnhancedTable`
- `operations` 插槽 → `actions` prop
- Arco 列定义 → TanStack 列定义 + 语义化配置
- 操作按钮 → 自动生成下拉菜单

## 完整示例

参见 `apps/component-showcase/src/sections/EnhancedTableSection.vue`

## 相关组件

- `FaTable` - 基础表格组件
- `YdResponsiveTable` - 响应式表格(桌面+移动)
- `YdTablePicker` - 远程数据选择器
- `YdAvatar` - 头像组件
- `YdBadge` - 徽章组件
- `YdDropdown` - 下拉菜单组件
