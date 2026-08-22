<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
import { FaDescriptions } from '@yudream/components'

const items = [
  { key: 'id', label: '用户 ID', value: '1932574829104' },
  { key: 'name', label: '用户名', value: 'husky' },
  { key: 'role', label: '角色', value: '管理员' },
  { key: 'status', label: '状态', span: 2, value: '启用' },
]
<\/script>

<template>
  <FaDescriptions :items="items" :column="3" border />
</template>`

const srcSlot = `<script setup>
import { FaDescriptions } from '@yudream/components'

const items = [
  { key: 'avatar', label: '头像', value: '' },
  { key: 'status', label: '状态', value: true },
]
<\/script>

<template>
  <FaDescriptions :items="items" :column="1">
    <template #value-status="{ value }">
      <FaTag>{{ value ? '启用' : '停用' }}</FaTag>
    </template>
    <!-- 按 item.key 生成的具名插槽：label-xxx / value-xxx -->
  </FaDescriptions>
</template>`

const props = [
  ['<code>items</code>', '<code>DescriptionItem[]</code>', '<code>[]</code>', '描述项数组，字段：<code>key</code> / <code>label</code> / <code>value</code> / <code>span</code> / <code>class</code> / <code>labelClass</code> / <code>valueClass</code>，并允许通过泛型扩展额外字段'],
  ['<code>column</code>', '<code>number</code>', '<code>3</code>', '每行列数；非正数或非法值会被归一化为 1'],
  ['<code>direction</code>', "<code>'horizontal' | 'vertical'</code>", "<code>'horizontal'</code>", '排列方向：水平为「标签-值」成对同行，垂直为标签行 + 值行上下交替'],
  ['<code>border</code>', '<code>boolean</code>', '<code>false</code>', '是否显示边框（含圆角与单元格分隔线）'],
  ['<code>labelWidth</code>', '<code>string | number</code>', '—', '标签列宽度，仅 <code>direction="horizontal"</code> 生效；数字按 px 处理'],
  ['<code>size</code>', "<code>'sm' | 'default' | 'lg'</code>", "<code>'default'</code>", '单元格内边距与字号'],
  ['<code>emptyText</code>', '<code>string</code>', '<code>-</code>', '值为 <code>null</code> / <code>undefined</code> / 空字符串时的占位文案'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
  ['<code>labelClass</code> / <code>valueClass</code>', '<code>string</code>', '—', '作用于所有标签 / 值单元格的 class，可被单项覆盖'],
]

const slotProps = [
  ['<code>item</code>', '<code>TItem</code>', '当前描述项'],
  ['<code>index</code>', '<code>number</code>', '在 <code>items</code> 中的下标'],
  ['<code>label</code>', '<code>string</code>', '当前标签文本'],
  ['<code>value</code>', '<code>DescriptionValue</code>', '原始值（未经 emptyText 占位处理）'],
]
</script>

# FaDescriptions 描述列表

以表格形式成组展示只读明细字段。纯 Vue + Tailwind 实现，内部按 `column` 与 `span` 自动分行布局。

依据源码：`yudream-frontend/packages/components/src/basic/descriptions/index.vue`

## 基础用法

<Demo title="基础描述列表" description="column 控制列数，span 让某一项跨多列，border 显示边框" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="descriptions" /></ClientOnly>
</Demo>

## 自定义内容插槽

当描述项设置了 `key` 时，可通过 `label-{key}` / `value-{key}` 具名插槽自定义该单元格渲染；插槽作用域见下方 Slot Props。未匹配到插槽时回退到默认文本渲染（空值显示 `emptyText`）。

<Demo title="具名插槽" description="label-{key} / value-{key} 按项定制渲染" :source="srcSlot">
  <div>
    <table style="width: 100%; border-collapse: collapse;">
      <tbody>
        <tr><th style="text-align: left; padding: 8px 12px; background: var(--vp-c-bg-soft); font-weight: 500; white-space: nowrap;">头像</th><td style="padding: 8px 12px;"><span style="display: inline-block; width: 28px; height: 28px; border-radius: 50%; background: var(--vp-c-divider);"></span></td></tr>
        <tr><th style="text-align: left; padding: 8px 12px; background: var(--vp-c-bg-soft); font-weight: 500; white-space: nowrap;">状态</th><td style="padding: 8px 12px;"><span class="demo-tag">启用</span></td></tr>
      </tbody>
    </table>
  </div>
</Demo>

## API

### Props

<ApiTable title="FaDescriptions Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | — |
| <code>label-\{key\}</code> | 定制某一项的标签单元格 |
| <code>value-\{key\}</code> | 定制某一项的值单元格 |

具名插槽作用域（`DescriptionSlotProps`）：

<ApiTable title="Slot Props" :data="slotProps" />

### Emits

无 emits，纯展示组件。
