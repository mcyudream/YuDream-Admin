<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicSelect from '../.vitepress/theme/components/demos/InteractiveBasicSelect.vue'

const srcBasic = `<script setup>
import { FaSelect } from '@yudream/components'
import { ref } from 'vue'

const status = ref('')

const options = [
  { label: '启用', value: 'enabled' },
  { label: '禁用', value: 'disabled' },
  { label: '归档', value: 'archived' },
]
<\/script>

<template>
  <FaSelect v-model="status" :options="options" placeholder="请选择状态" />
</template>`

const props = [
  ['<code>v-model</code>', '<code>AcceptableValue</code>', '—', '选中值（空字符串特殊处理）'],
  ['<code>options</code>', '<code>({ label, value, disabled? } \\| { label, options: [...] })[]</code>', '<code>[]</code>', '选项列表，支持分组'],
  ['<code>multiple</code>', '<code>boolean</code>', '<code>false</code>', '多选'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用'],
  ['<code>position</code>', "<code>reka-ui SelectContentProps['position']</code>", '—', '下拉定位策略'],
  ['<code>placeholder</code>', '<code>string</code>', '—', '占位文本'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]

const emits = [
  ['<code>change</code>', '<code>(value: AcceptableValue) => void</code>', '选中值变化时触发'],
]
</script>

# FaSelect 选择器

下拉选择，支持选项分组与多选。基于 reka-ui 封装。

## 基础用法

<Demo title="基础选择器" description="options 支持平铺与分组两种形态" :source="srcBasic">
  <ClientOnly><InteractiveBasicSelect /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaSelect Props" :data="props" />

### Emits

<ApiTable title="FaSelect Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />
