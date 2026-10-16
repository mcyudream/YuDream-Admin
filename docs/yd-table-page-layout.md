# YdTablePageLayout 表格页布局组件

## 概述

`YdTablePageLayout` 是一个统一的表格页面布局组件,提供了标准化的页面结构,包括:
- 页面标题区
- 折叠式搜索区
- Tab + 操作栏
- 表格内容区
- 分页区

与 `YdDynamicSearchForm` 和 `useTablePage` 配合使用,可快速搭建符合规范的表格页面。

## 特性

- ✅ **页面标题独立**: 标题区只放标题,操作按钮移至表格上方
- ✅ **搜索区折叠**: 默认折叠,点击展开,减少视觉干扰
- ✅ **注解驱动**: 后端用 `@Searchable` 注解声明搜索字段,前端自动生成筛选表单
- ✅ **操作按钮自动折叠**: 超过阈值自动将不常用操作折叠到"更多"菜单
- ✅ **响应式适配**: 移动端(<768px)自动调整布局

## 快速开始

### 1. 后端:为查询类添加 @Searchable 注解

```java
@EqualsAndHashCode(callSuper = true)
@Data
public class UserPageQuery extends PageQuery {
    @Searchable(
        label = "关键词",
        placeholder = "用户名/昵称/邮箱/手机号",
        showWhenFolded = true,  // 折叠时仍显示
        order = 1,
        width = "240px"
    )
    private String keyword;

    @Searchable(
        label = "部门",
        controlType = ControlType.SELECT,
        optionsSource = "deptOptions",  // 选项数据源标识
        order = 2
    )
    private Long deptId;

    @Searchable(
        label = "状态",
        controlType = ControlType.SELECT,
        order = 3
    )
    private UserStatus status;
}
```

### 2. 前端:使用 useTablePage + YdTablePageLayout

```vue
<script setup lang="ts">
import type { YdTablePageAction, SearchOptionsSource } from '@yudream/components'
import { useTablePage, YdTablePageLayout, YdDynamicSearchForm, YdResponsiveEnhancedTable } from '@yudream/components'
import apiUser from '@/api/modules/system-user'
import apiDept from '@/api/modules/system-dept'

// 定义选项数据源
const optionsSources: SearchOptionsSource[] = [
  {
    id: 'deptOptions',
    loader: async () => {
      const res = await apiDept.list()
      return res.data.map(dept => ({ label: dept.name, value: dept.id }))
    },
  },
]

// 使用 useTablePage hook
const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage({
  queryClass: 'online.yudream.base.application.system.user.query.UserPageQuery',
  loadApi: apiUser.page,
  optionsSources,
})

// 定义操作按钮
const actions: YdTablePageAction[] = [
  {
    label: '导出',
    icon: 'morph:file-spreadsheet',
    variant: 'outline',
    onClick: exportUsers,
    autoFold: true,  // 超过阈值时自动折叠
  },
  {
    label: '导入',
    icon: 'morph:upload',
    variant: 'outline',
    onClick: importUsers,
    autoFold: true,
  },
  {
    label: '新增用户',
    icon: 'morph:user-plus',
    onClick: createUser,
  },
]

function exportUsers() {
  // 导出逻辑
}

function importUsers() {
  // 导入逻辑
}

function createUser() {
  // 新增用户逻辑
}
</script>

<template>
  <YdTablePageLayout
    title="用户管理"
    :actions="actions"
    :actions-threshold="4"
  >
    <!-- 搜索区 -->
    <template #search="{ fold }">
      <YdDynamicSearchForm
        v-model="searchQuery"
        :config="searchConfig"
        :fold="fold"
        :options-sources="optionsSources"
        @search="search"
        @reset="resetSearch"
      />
    </template>

    <!-- 表格区 -->
    <template #table>
      <YdLoadingOverlay :loading="loading">
        <YdResponsiveEnhancedTable
          :data="rows"
          :columns="columns"
          row-key="id"
        />
      </YdLoadingOverlay>
    </template>

    <!-- 分页 -->
    <template #footer>
      <YdPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </template>
  </YdTablePageLayout>
</template>
```

## 表格行为规范（强制默认）

`YdEnhancedTable` / `YdResponsiveEnhancedTable` 默认启用以下能力，表格页统一遵循，不需要逐页开启：

| 能力 | 默认 | 说明 |
|------|------|------|
| 列排序 | 开（`sortable`） | 点击表头在本页数据上排序，第三次点击取消；选择列/操作列自动排除 |
| 列宽拖动 | 开（`resizable`） | 拖动表头右缘调整列宽，宽度写入列 sizing 状态并启用 `table-fixed` 精确分配 |
| 列显隐 | 开（`columnVisibility`） | 控件自动并入 `YdTablePageLayout` 操作栏末尾（与操作按钮同排且最后）；未包裹布局时留在表格自带工具栏行 |
| 固定列 | 约定 | 名称（对人可读的标识）列 `fixed: 'left'`；操作列由增强表格自动 `fixed: 'right'` 且不可隐藏 |

页面只需在列定义中标好 `width` / `minWidth`（列宽拖动需要初始宽度），固定列按语义标注：

```ts
const tableColumns = [
  { id: 'name', header: '角色名称', width: 160, fixed: 'left' },  // 名称列固定左
  { accessorKey: 'code', header: '编码', width: 180 },
  // … 其余数据列
]
```

如需关闭某项（如树形表格不希望排序），在列上显式设置 `enableSorting: false`；整表关闭传 `:sortable="false"` / `:resizable="false"`。

## API

### YdTablePageLayout Props

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `title` | `string` | - | 页面标题 |
| `showSearch` | `boolean` | `true` | 是否显示搜索区 |
| `searchDefaultFolded` | `boolean` | `true` | 搜索区默认是否折叠 |
| `tabs` | `Array<{label: string, value: string}>` | - | Tab选项 |
| `activeTab` | `string` | - | 当前激活的tab |
| `actions` | `YdTablePageAction[]` | - | 操作按钮配置 |
| `actionsThreshold` | `number` | `4` | 操作按钮显示阈值 |

### YdTablePageAction 类型

```ts
interface YdTablePageAction {
  label: string                // 按钮文本
  icon?: string                // 图标名称
  variant?: 'default' | 'outline' | 'destructive' | 'secondary' | 'ghost'
  permission?: string          // 权限码
  onClick: () => void          // 点击回调
  autoFold?: boolean           // 是否自动折叠
  danger?: boolean             // 是否危险操作
  disabled?: boolean           // 是否禁用
}
```

### YdTablePageLayout Slots

| 插槽 | 参数 | 说明 |
|-----|------|------|
| `page-title` | - | 自定义页面标题区域 |
| `search` | `{ fold: boolean }` | 搜索筛选表单内容 |
| `tabs` | - | 自定义tab栏 |
| `actions` | - | 自定义操作按钮区域 |
| `table` | - | 表格内容 |
| `footer` | - | 分页等底部内容 |

### YdTablePageLayout Events

| 事件 | 参数 | 说明 |
|-----|------|------|
| `update:activeTab` | `(value: string)` | Tab切换时触发 |

## YdDynamicSearchForm

动态搜索表单组件,根据后端 `@Searchable` 注解自动生成筛选表单。

### Props

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `modelValue` | `Record<string, any>` | - | 表单数据(v-model) |
| `config` | `SearchConfig` | - | 搜索配置 |
| `fold` | `boolean` | `false` | 是否折叠 |
| `optionsSources` | `SearchOptionsSource[]` | - | 选项数据源映射 |
| `showActions` | `boolean` | `true` | 是否显示操作按钮 |

### Events

| 事件 | 参数 | 说明 |
|-----|------|------|
| `search` | - | 点击筛选按钮时触发 |
| `reset` | - | 点击重置按钮时触发 |

## useTablePage

表格页面逻辑封装 composable。

### 参数

```ts
interface UseTablePageOptions<T, Q extends PageQuery> {
  queryClass: string                         // 查询类全限定名
  loadApi: (query: Q) => Promise<PageResult<T>>  // 加载数据API
  initialQuery?: Partial<Q>                  // 初始查询条件
  initialPage?: number                       // 初始页码,默认1
  initialSize?: number                       // 初始每页数量,默认20
  optionsSources?: SearchOptionsSource[]     // 选项数据源
  autoLoad?: boolean                         // 是否自动加载,默认true
}
```

### 返回值

```ts
{
  loading: Ref<boolean>                      // 加载状态
  rows: Ref<T[]>                             // 表格数据
  pagination: {                              // 分页信息
    page: number
    size: number
    total: number
  }
  searchQuery: Record<string, any>           // 搜索条件
  searchConfig: Ref<SearchConfig | undefined>  // 搜索配置
  optionsSources: SearchOptionsSource[]      // 选项数据源
  load: () => Promise<void>                  // 加载数据
  search: () => void                         // 搜索
  resetSearch: () => void                    // 重置搜索
  onPageChange: (page: number) => void       // 页码变化
  onSizeChange: (size: number) => void       // 页大小变化
  refresh: () => Promise<void>               // 刷新
}
```

## @Searchable 注解

后端注解,用于声明查询字段的元数据。

### 属性

| 属性 | 类型 | 默认值 | 说明 |
|-----|------|--------|------|
| `label` | `String` | 字段名 | 显示标签 |
| `placeholder` | `String` | - | 输入框占位符 |
| `controlType` | `ControlType` | `AUTO` | 控件类型 |
| `order` | `int` | `0` | 显示顺序 |
| `showWhenFolded` | `boolean` | `false` | 是否在折叠时显示 |
| `width` | `String` | `"200px"` | 控件宽度 |
| `group` | `String` | - | 分组名称 |
| `optionsSource` | `String` | - | 选项数据源标识 |
| `hint` | `String` | - | 提示信息 |

### ControlType 枚举

| 值 | 说明 | 前端控件 |
|----|------|---------|
| `AUTO` | 自动推断 | 根据字段类型自动选择 |
| `TEXT` | 文本框 | `YdInput` |
| `SELECT` | 下拉选择 | `YdSelect` |
| `DATE_RANGE` | 日期范围 | `YdRangePicker` 范围选择器 |
| `NUMBER_RANGE` | 数值范围 | `YdNumberRange` 数值范围输入 |
| `CHECKBOX` | 复选框组 | `YdCheckboxGroup` |
| `RADIO` | 单选按钮组 | `YdRadioGroup` |

## 最佳实践

### 1. 合理使用 showWhenFolded

只将最常用的1-2个搜索字段标记为 `showWhenFolded = true`,通常是关键词搜索。

```java
@Searchable(
    label = "关键词",
    placeholder = "用户名/昵称/邮箱",
    showWhenFolded = true,  // ✅ 折叠时仍显示
    order = 1
)
private String keyword;

@Searchable(
    label = "部门",
    controlType = ControlType.SELECT,
    order = 2  // ❌ 不设置 showWhenFolded,折叠时隐藏
)
private Long deptId;
```

### 2. 操作按钮折叠策略

- 常用操作(新增/编辑/删除)不设置 `autoFold`
- 不常用操作(导入/导出/模板下载)设置 `autoFold = true`
- 危险操作设置 `danger = true`,自动加红色标识

```ts
const actions: YdTablePageAction[] = [
  // 常用操作 - 始终显示
  {
    label: '新增用户',
    icon: 'morph:user-plus',
    onClick: createUser,
  },
  // 不常用操作 - 自动折叠
  {
    label: '导出',
    icon: 'morph:download',
    variant: 'outline',
    onClick: exportUsers,
    autoFold: true,  // ✅ 超过阈值时折叠
  },
  // 危险操作 - 红色+折叠
  {
    label: '批量删除',
    icon: 'morph:trash-2',
    onClick: batchDelete,
    danger: true,  // ✅ 红色标识
    autoFold: true,
  },
]
```

### 3. 选项数据源定义

选项数据源支持同步和异步两种方式:

```ts
const optionsSources: SearchOptionsSource[] = [
  // 同步方式
  {
    id: 'statusOptions',
    loader: () => [
      { label: '启用', value: 'ACTIVE' },
      { label: '停用', value: 'DISABLED' },
    ],
  },
  // 异步方式
  {
    id: 'deptOptions',
    loader: async () => {
      const res = await apiDept.list()
      return res.data.map(dept => ({
        label: dept.name,
        value: dept.id,
      }))
    },
  },
]
```

## 迁移指南

### 从旧版表格页迁移

**旧版写法**:
```vue
<YdPageHeader title="用户管理">
  <YdButton @click="exportUsers">导出</YdButton>
  <YdButton @click="createUser">新增</YdButton>
</YdPageHeader>

<YdPageMain>
  <YdSearchBar>
    <div class="grid grid-cols-3 gap-3">
      <YdInput v-model="search.keyword" placeholder="关键词" />
      <YdSelect v-model="search.deptId" :options="deptOptions" />
      <div class="flex gap-2">
        <YdButton variant="outline" @click="resetSearch">重置</YdButton>
        <YdButton @click="loadData">筛选</YdButton>
      </div>
    </div>
  </YdSearchBar>

  <YdResponsiveEnhancedTable :data="rows" :columns="columns" />
</YdPageMain>
```

**新版写法**:
```vue
<YdTablePageLayout
  title="用户管理"
  :actions="actions"
>
  <template #search="{ fold }">
    <YdDynamicSearchForm
      v-model="searchQuery"
      :config="searchConfig"
      :fold="fold"
      @search="search"
      @reset="resetSearch"
    />
  </template>

  <template #table>
    <YdResponsiveEnhancedTable :data="rows" :columns="columns" />
  </template>
</YdTablePageLayout>
```

**优势**:
- ✅ 操作按钮移至表格上方,视觉更清晰
- ✅ 搜索字段由后端注解驱动,前端无需重复定义
- ✅ 搜索区自动折叠,减少视觉干扰
- ✅ 操作按钮自动折叠,适应不同屏幕宽度

## 注意事项

1. **ID字段必须为字符串**: 遵循项目硬规则6,Long类型ID在前端必须用字符串表示
2. **查询类全限定名**: `queryClass` 必须传入完整的包名+类名
3. **选项数据源标识**: 后端 `optionsSource` 与前端 `optionsSources[].id` 必须一致
4. **控件类型推断**: 使用 `ControlType.AUTO` 时,确保字段类型与预期控件匹配

## 相关文档

- [前端开发规范](./dev/frontend.md)
- [YD组件使用规范](./yd-ui-guidelines.md)
- [表格设计规范](./table-design-guidelines.md)
- [@Searchable注解详解](./searchable-annotation.md)
