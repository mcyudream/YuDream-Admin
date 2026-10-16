# YdResponsiveEnhancedTable - 响应式增强表格

## 简介

`YdResponsiveEnhancedTable` 是 `YdEnhancedTable` 的响应式版本，自动在桌面端和移动端之间切换显示模式：

- **桌面端**：显示完整的增强表格
- **移动端**：自动切换为优化的卡片列表视图

## 特性

✅ 完全继承 YdEnhancedTable 的所有功能
✅ 响应式断点切换（可自定义）
✅ 移动端卡片优化设计
✅ 用户信息展示（头像 + 邮箱）
✅ 状态标签展示
✅ 操作菜单支持
✅ 选择框支持
✅ 树形数据支持
✅ 自定义卡片插槽

## 基本使用

```vue
<template>
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
</template>

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
}

const data = ref<User[]>([...])

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
}

const columns: EnhancedTableProps<User>['columns'] = [
  { accessorKey: 'role', header: '职位' },
  { accessorKey: 'department', header: '部门' },
]

const actions: ActionMenuItem<User>[] = [
  {
    label: '查看',
    icon: 'i-heroicons-eye',
    onClick: (row: User) => console.log('查看', row),
  },
  {
    label: '编辑',
    icon: 'i-heroicons-pencil',
    onClick: (row: User) => console.log('编辑', row),
  },
  {
    label: '删除',
    icon: 'i-heroicons-trash',
    danger: true,
    onClick: (row: User) => console.log('删除', row),
  },
]
</script>
```

## Props

### 响应式专属

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| mobileBreakpoint | string | `(max-width: 768px)` | 移动端断点媒体查询字符串 |
| cardHiddenColumns | string[] | - | 移动端卡片中隐藏的列，可传列 id 或 accessorKey；被列显示开关隐藏的列在卡片中同样不显示 |

### 继承的 Props

所有 `YdEnhancedTable` 的 props 都被支持：

- `userColumn` - 用户列配置（卡片头部显示头像/标题/副标题）；`fixed` 控制桌面端固定位置，默认 `'left'` 固定在左侧，传 `false` 取消固定，也可传 `'right'`
- `statusColumn` - 状态列配置（卡片底部显示徽章）
- `columns` - 普通列配置
- `actions` - 操作菜单；`label` 可传函数按行动态生成，`visible` 可做行级隐藏，全部隐藏时不渲染触发按钮
- `showActions` - 是否显示操作菜单
- `data` - 表格数据
- `selectable` - 是否可选择（多选 `multiple` 时卡片头部显示勾选框）
- `multiple` - 是否多选
- 以及所有 TanStack Table 的 props...

### 固定用户列

`userColumn.fixed` 复用表格既有的固定列机制（sticky + 边缘阴影），仅作用于桌面表格：

| 取值 | 效果 |
|------|------|
| 不传 / `true` / `'left'` | 用户列固定在左侧（默认） |
| `'right'` | 用户列固定在右侧 |
| `false` | 用户列不固定，随横向滚动 |

操作列（`actions`）保持固定右侧；移动端卡片布局不使用固定列配置。

## 移动端卡片渲染

卡片主体沿用桌面表格的**可见单元格**，不是重新按字段名取值：

- `#cell-{列 id}` 插槽在卡片中同样生效（作用域与桌面一致：`cell`、`column`、`index`、`row`、`table`、`value`）；
- 列配置里的 `cell` 渲染函数通过 FlexRender 复用，标签、徽章、格式化日期等自定义渲染在卡片中保持一致；
- 没有自定义渲染且值为空时显示 `—` 占位；
- 列显示开关（`columnVisibility`）隐藏的列不会出现在卡片中，`cardHiddenColumns` 可进一步按端裁剪。

## 插槽

### 默认插槽（桌面端）

所有 `YdEnhancedTable` 的插槽都被支持。

### 移动端专属插槽

#### card

自定义移动端卡片内容：

```vue
<template>
  <YdResponsiveEnhancedTable :data="data" ...>
    <template #card="{ row, index, depth, selected, toggleSelected, selectDisabled }">
      <FaCard>
        <!-- 自定义卡片内容 -->
        <div>{{ row.name }}</div>
      </FaCard>
    </template>
  </YdResponsiveEnhancedTable>
</template>
```

**参数：**
- `row` - 当前行数据
- `index` - 行索引
- `depth` - 树形层级（树形数据）
- `selected` - 是否选中
- `toggleSelected` - 切换选中状态的函数
- `selectDisabled` - 是否禁用选择

#### card-header

在默认卡片布局中插入自定义头部内容：

```vue
<template #card-header="{ row, index }">
  <div>自定义头部内容</div>
</template>
```

#### card-footer

在默认卡片布局中插入自定义底部内容：

```vue
<template #card-footer="{ row, index }">
  <div>自定义底部内容</div>
</template>
```

## 移动端卡片样式

移动端卡片采用了优化的设计：

### 视觉特性

- **悬浮效果**：鼠标悬停时轻微上移并显示阴影
- **选中状态**：选中的卡片显示主题色边框和背景高亮
- **分段布局**：头部、主体、底部三段式布局，清晰分隔
- **响应式间距**：在小屏幕上自动调整字段布局

### 卡片结构

```
┌─────────────────────────────────────┐
│ 头部 (Header)                        │
│ - 用户头像 + 姓名 + 邮箱              │
│ - 选择框（右侧）                      │
├─────────────────────────────────────┤
│ 主体 (Body)                          │
│ - 字段标签：字段值                    │
│ - 字段标签：字段值                    │
│ - ...                                │
├─────────────────────────────────────┤
│ 底部 (Footer)                        │
│ - 状态标签（左侧）                    │
│ - 操作菜单（右侧）                    │
└─────────────────────────────────────┘
```

## 树形数据支持

移动端自动展开树形数据为扁平列表，并通过缩进和连接线表示层级关系：

```vue
<YdResponsiveEnhancedTable
  :data="treeData"
  :tree="true"
  :get-sub-rows="(row) => row.children"
  ...
/>
```

## 样式定制

### CSS 变量

组件使用了 CSS 变量，可通过全局样式覆盖：

```css
.yd-responsive-enhanced-table {
  --card-gap: 0.75rem;
  --card-padding: 1rem;
  --card-border-radius: 0.5rem;
}
```

### 自定义类名

```vue
<YdResponsiveEnhancedTable
  class="custom-table"
  table-class="custom-desktop-table"
  ...
/>
```

```css
.custom-table .mobile-card-list {
  /* 移动端列表样式 */
}

.custom-table .data-card {
  /* 卡片样式 */
}
```

## 注意事项

1. **断点设置**：`mobileBreakpoint` 使用标准的 CSS 媒体查询语法
2. **性能优化**：大数据集时建议使用分页，避免一次渲染过多卡片
3. **插槽优先级**：提供 `card` 插槽时会完全替换默认卡片布局
4. **响应式测试**：开发时使用浏览器开发者工具的响应式模式测试

## 与 YdEnhancedTable 的区别

| 特性 | YdEnhancedTable | YdResponsiveEnhancedTable |
|------|----------------|---------------------------|
| 桌面端显示 | ✅ 表格 | ✅ 表格 |
| 移动端显示 | ✅ 表格（可能溢出） | ✅ 优化的卡片列表 |
| 移动端按 cell 插槽渲染 | ❌ | ✅ |
| 响应式切换 | ❌ | ✅ |
| 移动端裁剪与自定义卡片 | ❌ | ✅（`cardHiddenColumns` / `#card`） |
| API 兼容性 | - | 100% 向下兼容 |

## 与 YdResponsiveTable 的关系

两者都是「桌面表格 + 移动卡片」：`YdResponsiveTable` 面向任意 TanStack 列配置，卡片必须自绘 `#card`；`YdResponsiveEnhancedTable` 面向图文并茂的增强表格，卡片可按桌面列自动生成，并按需叠加用户列、状态徽章与操作下拉。管理列表需要在卡片上完整还原业务渲染时用前者，人员列表用后者。

## 示例

完整示例见组件预览站的增强表格分组（`apps/component-showcase/src/sections/EnhancedTableSection.vue`）；宿主的数据备份、安全中心等设置页也按此组件接入响应式卡片。
