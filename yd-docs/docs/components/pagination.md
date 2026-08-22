<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingBasicPagination from '../.vitepress/theme/components/demos/InteractiveRemainingBasicPagination.vue'

const srcBasic = `<script setup>
import { FaPagination } from '@yudream/components'
import { reactive } from 'vue'

const pagination = reactive({ page: 1, size: 10, total: 0 })

function load() {
  // GET api/xxx?page=\${pagination.page}&size=\${pagination.size}
}

function onPageChange(page) {
  load()
}
function onSizeChange(size) {
  pagination.page = 1
  load()
}
<\/script>

<template>
  <FaPagination
    v-model:page="pagination.page"
    v-model:size="pagination.size"
    :total="pagination.total"
    @page-change="onPageChange"
    @size-change="onSizeChange"
  />
</template>`

const srcLayout = `<script setup>
import { FaPagination } from '@yudream/components'
import { reactive } from 'vue'

const pagination = reactive({ page: 1, size: 20, total: 0 })
<\/script>

<template>
  <FaPagination
    v-model:page="pagination.page"
    v-model:size="pagination.size"
    :total="pagination.total"
    layout="total, sizes, ->, pager, jumper"
    :sizes="[20, 50, 100]"
  />
</template>`

const props = [
  ['<code>v-model:page</code>', '<code>number</code>', '—', '必填；当前页码（从 1 开始）'],
  ['<code>v-model:size</code>', '<code>number</code>', '—', '必填；每页条数'],
  ['<code>total</code>', '<code>number</code>', '—', '必填；数据总条数，用于计算总页数 <code>Math.ceil(total / size)</code>'],
  ['<code>sizes</code>', '<code>number[]</code>', '<code>[10, 20, 30, 40, 50, 100]</code>', '每页条数可选项'],
  ['<code>layout</code>', '<code>string</code>', "<code>'total, sizes, ->, pager, jumper'</code>", '子项及顺序，见下文「layout 布局」'],
  ['<code>textTemplates</code>', '<code>{ total?, sizes?, jumper? }</code>', '中文默认文案', '自定义文案：<code>total(total)</code>、<code>sizes(size)</code>、<code>jumper: { before, after }</code>'],
]

const events = [
  ['<code>pageChange</code>', '(page: number)', '<code>page</code> 模型变化时触发（含跳页、翻页按钮）'],
  ['<code>sizeChange</code>', '(size: number)', '<code>size</code> 模型变化时触发'],
]
</script>

# FaPagination 分页

基于 reka-ui Pagination 子组件二次封装的完整分页栏：页码器 + 总数 + 每页条数选择器 + 跳页输入框，通过 `layout` 字符串自由编排。内部由 `Pagination` / `PaginationContent` / `PaginationItem` / `PaginationEllipsis` / `PaginationFirst` 等子组件组成。

依据源码：`yudream-frontend/packages/components/src/basic/pagination/index.vue` 及同目录 `pagination/` 子目录

## 基础用法

组件是**受控**的：`page` 与 `size` 均为必填的 `v-model`，`total` 由服务端返回后写入。翻页与改每页条数只更新模型并发出事件，请求由业务侧发起（见下文「与服务端分页配合」）。

<Demo title="基础分页" description="v-model:page / v-model:size 受控，layout 控制子项" :source="srcBasic">
  <ClientOnly><InteractiveRemainingBasicPagination /></ClientOnly>
</Demo>

## layout 布局

`layout` 是逗号分隔的字符串，决定显示哪些子项及其排列顺序：

| 片段 | 含义 |
| --- | --- |
| `total` | 总条数文案（默认「共 N 条」） |
| `sizes` | 每页条数 Select |
| `pager` | 页码器（首/上一页/页码/省略号/下一页/末页） |
| `jumper` | 「前往 N 页」跳页输入框 |
| `->` | 弹性占位符（把后面的项推到右侧，移动端隐藏） |

未出现在 `layout` 中的子项不渲染。跳页输入框会过滤非数字字符，回车提交；越界时自动纠正到边界页码。

<Demo title="自定义布局" description="layout='total, sizes, ->, pager, jumper'" :source="srcLayout">
  <ClientOnly><InteractiveRemainingBasicPagination /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaPagination Props" :data="props" />

### Emits

<ApiTable title="FaPagination Emits" :data="events" />

### Slots

无插槽。

## 与服务端分页配合

FaPagination 不做本地切片——`data` 是否分页完全由调用方决定。标准管理页（参考 `apps/core-arco-design-vue/src/views/system/user/index.vue`）的组合方式：

```mermaid
sequenceDiagram
  participant U as 用户
  participant P as FaPagination
  participant V as 页面组件
  participant S as 服务端

  V->>S: GET list?page=1&size=10
  S-->>V: { records, total }
  V->>P: 写入 :total，表格渲染当前页 records
  U->>P: 点击第 2 页 / 跳页
  P->>V: emit pageChange(2)，并同步 v-model:page
  V->>S: GET list?page=2&size=10
  U->>P: 切换每页条数
  P->>V: emit sizeChange(50)
  V->>V: 重置 page = 1 后重新请求
```

要点：

- 请求参数约定为 `page`（从 1 开始）与 `size`，响应为项目统一的 `PageResult<T>` 结构（含 `records` 与 `total`，见 `apps/core-arco-design-vue/src/api/modules/system-client.ts`），将 `total` 回填给 `:total`。
- 监听 `size-change` 时应先把 `page` 重置为 1，避免落在超出范围 的页码上。
- 表格侧使用 `FaTable` / `FaResponsiveTable` 时直接把当前页 `records` 作为 `data` 传入即可，无需开启任何客户端分页 prop。

::: warning 长 ID 必须使用 string
后端 Java `Long` / Snowflake ID 超出 JS `Number` 安全整数范围，在 JSON 响应、TS 模型、表格行 key 与 URL 参数中**一律使用 `string`**，禁止 `Number(id)` 之类的转换；分页接口返回的主键、以及跳转详情页携带的 `id` 查询参数都应保持字符串形态。
:::
