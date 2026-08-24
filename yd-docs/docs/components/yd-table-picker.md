<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveFormTablePicker from '../.vitepress/theme/components/demos/InteractiveFormTablePicker.vue'

const srcBasic = `<script setup>
import { YdTablePicker } from '@yudream/components'
import { ref } from 'vue'

const keys = ref([])

const columns = [
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'owner', header: '负责人', width: 100 },
  { accessorKey: 'updatedAt', header: '更新时间', width: 120 },
]

// 动态取数：打开弹窗、翻页、搜索时调用
async function fetcher({ page, size, keyword }) {
  const { data } = await api.get('/plugins', { params: { page, size, keyword } })
  return { list: data.list, total: data.total }
}
<\/script>

<template>
  <YdTablePicker
    v-model="keys"
    :columns="columns"
    :fetcher="fetcher"
    row-key="id"
    label-key="name"
    title="选择插件"
  />
</template>`

const srcFilters = `<YdTablePicker v-model="keys" :columns="columns" :fetcher="fetcher" row-key="id" label-key="name">
  <template #filters="{ reload }">
    <FaSelect v-model="category" :options="categoryOptions" class="w-32" @change="reload" />
  </template>
</YdTablePicker>`

const props = [
  ['<code>v-model</code>', '<code>string[]</code>', '<code>[]</code>', '选中行的 key 列表（一律 string，雪花 ID 禁止转 Number）'],
  ['<code>columns</code>', '<code>TableColumn&lt;T&gt;[]</code>', '—（必填）', '业务列配置，与 <code>FaTable</code> 一致；选择列由组件内部前置'],
  ['<code>fetcher</code>', '<code>(query: YdTablePickerQuery) =&gt; Promise&lt;YdTablePickerResult&lt;T&gt;&gt;</code>', '—（必填）', '动态取数函数；query 含 <code>page / size / keyword</code>，返回 <code>{ list, total }</code>'],
  ['<code>rowKey</code>', '<code>string</code>', "<code>'id'</code>", '行唯一键字段'],
  ['<code>labelKey</code>', '<code>string</code>', "<code>'label'</code>", '表面芯片与已选展示的文本字段'],
  ['<code>multiple</code>', '<code>boolean</code>', '<code>true</code>', '多选；传 <code>false</code> 为单选互斥'],
  ['<code>initialLabels</code>', '<code>Record&lt;string, string&gt;</code>', '—', '编辑场景的回显文本（key → 展示文本），用于尚未加载过的已选项'],
  ['<code>title / placeholder / searchPlaceholder</code>', '<code>string</code>', '—', '弹窗标题、表面占位、搜索框占位'],
  ['<code>pageSize / pageSizes</code>', '<code>number / number[]</code>', '<code>10 / [10, 20, 50]</code>', '每页条数与可选项'],
  ['<code>disabled / clearable</code>', '<code>boolean</code>', '<code>false / true</code>', '禁用与一键清空'],
  ['<code>emptyText</code>', '<code>string</code>', "<code>'暂无数据'</code>", '表格空态文案'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(keys: string[]) =&gt; void</code>', '确认选择、移除芯片或清空时更新'],
  ['<code>change</code>', '<code>(keys: string[]) =&gt; void</code>', '同上，伴随 modelValue 更新触发'],
]

const slots = [
  ['<code>filters</code>', '搜索栏追加的筛选控件，插槽参数 <code>{ reload }</code>；外部筛选值变化后调用 <code>reload()</code> 重新取数'],
  ['<code>cell-* / header-* / footer-*</code>', '透传给内部 <code>FaTable</code> 的列插槽，用于自定义单元格渲染'],
]

const exposes = [
  ['<code>open()</code>', '编程打开选择弹窗'],
  ['<code>reload()</code>', '弹窗打开状态下按当前条件重新取数'],
  ['<code>clear()</code>', '清空全部已选'],
]
</script>

# YdTablePicker 弹出式选择输入框

表面为多元素选择框，点击弹出带 **表格 + 关键字搜索 + 翻页** 的动态数据选择窗，用于替代从后端拉取候选数据的选择器场景。选中态跨页保持，弹窗重开按 `v-model` 回显勾选。

## 基础用法

<Demo title="动态数据选择" description="点击输入框弹出选择窗：表格分页、关键字搜索、跨页多选；已选 2 项为 initialLabels 回显" :source="srcBasic">
  <ClientOnly><InteractiveFormTablePicker /></ClientOnly>
</Demo>

## 扩展筛选条件

`filters` 插槽向搜索栏注入自定义筛选控件；筛选值变化后调用插槽参数 `reload()`（或监听后调用暴露的 `reload()`）触发重新取数：

<Demo title="filters 插槽" :source="srcFilters">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「全部分类」下拉（组件展示页表单区）。</p>
</Demo>

## API

### Props

<ApiTable title="YdTablePicker Props" :data="props" />

### Emits

<ApiTable title="YdTablePicker Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Slots

<ApiTable title="YdTablePicker Slots" :data="slots" :columns="['插槽', '说明']" />

### Exposes

<ApiTable title="YdTablePicker Exposes" :data="exposes" :columns="['方法', '说明']" />

## 注意事项

- **确认制**：弹窗内的勾选在点击「确定」后才写回 <code>v-model</code>；表面芯片上的移除与清空为即时生效。
- **标签缓存**：选中项的展示文本在数据加载时缓存，翻页/重开弹窗不丢失；跨会话或初始值请配合 <code>initialLabels</code>。
- **取数异常**：<code>fetcher</code> 抛错时弹窗显示空态并 toast 提示，不影响已选内容。
- 完整可运行示例见主前端仓 <code>apps/component-showcase</code> 的「表单」区。
