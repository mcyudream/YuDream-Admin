# YdSelect 下拉选择

`YdSelect` 使用普通或分组 `options`；单选绑定一个值，`multiple` 绑定数组。选中值保持原类型，Long/Snowflake ID 使用字符串。此页描述共享源码中的修复契约；插件只在 components/SDK 发布验签完成后同步依赖，不能把源码预览视为已发布版本。

## 回显和空值

- 未选择时默认显示“没有选择”，可以用 `placeholder` 提供更具体提示。
- 不默认选择或展示第一个候选项；`0` 和合法的空字符串选项均按真实模型处理。
- 多选在选择框内展示全部已选项的标签，可以在下拉列表再次点选来取消。标签会随内容换行，不用单行文字冒充整个数组。
- 前端搜索只过滤候选项，保留已经选择的项目。多选中暂时不存在的已选值保留原值回显，候选加载后恢复对应标签；对象值使用自身的 `label`，缺省显示“已选项”。单选的非对象值无法匹配候选时显示占位提示，保持原模型等待候选恢复。
- `change` 返回完整模型；清空由调用方将单选设为 `undefined`、多选设为 `[]`。标签不包含嵌套在触发按钮中的删除按钮。

## 可选前端搜索

`allowSearch` 默认 `false`。启用后，在下拉列表顶部输入关键词，按标签和值筛选，大小写不敏感；分组名称命中时显示组内选项。它不发起远程请求，不修改选择数组。需要远程分页资源时使用 `YdTablePicker`。

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { YdSelect } from '@yudream/components'

const selected = ref<string[]>([])
const options = [
  { label: '外观', options: [
    { label: '灵动', value: 'vibrant' },
    { label: '雅致', value: 'calm' },
    { label: '紧凑', value: 'compact' },
  ] },
]
</script>

<template>
  <YdSelect
    v-model="selected"
    :options="options"
    multiple
    allow-search
    search-placeholder="查找外观…"
    empty-text="没有匹配的外观"
    aria-label="选择外观"
    class="w-full"
  />
</template>
```

搜索输入可以使用上下方向键进入结果列表；列表沿用方向键、Enter 和 Esc 的选择与关闭行为。关闭列表或停用搜索时清除关键词；禁用控件时关闭列表。既有 `position`、分组、禁用选项、单选值类型和表单关联保持兼容。

`YdMultiSelect` 是独立的字符串数组搜索选择器，能力与发布边界以其文档为准；不能用它替换基础 `YdSelect` 后丢掉数字、对象值或分组行为。
