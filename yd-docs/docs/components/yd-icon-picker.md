<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'

const srcBasic = `<script setup>
import { YdIconPicker } from '@yudream/components'
import { ref } from 'vue'

const icon = ref('i-ri:settings-3-line')
<\/script>

<template>
  <YdIconPicker v-model="icon" />
</template>`

const props = [
  ['<code>v-model</code>', '<code>string | undefined</code>', '—', '选中图标名，例如 <code>i-ri:settings-3-line</code>'],
  ['<code>placeholder</code>', '<code>string</code>', "<code>'选择系统图标'</code>", '未选择时的占位文案'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '是否禁用'],
  ['<code>clearable</code>', '<code>boolean</code>', '<code>true</code>', '是否允许清空'],
  ['<code>class</code>', '<code>string</code>', '—', '触发器 CSS 类'],
]

const emits = [
  ['<code>change</code>', '<code>(value: string | undefined) =&gt; void</code>', '选择或清空时触发'],
]
</script>

# YdIconPicker 系统图标选择器

弹出式系统图标选择器，默认提供 Remix Icon 全量清单（约 3200 个）。选中值写入 `i-ri:*` 图标名，可直接交给 `FaIcon` 渲染。网格按页浏览，每页 64 个。

## 基础用法

<Demo title="选择系统图标" description="点击输入框弹出图标网格，支持搜索与常用图标" :source="srcBasic" />

## API

<ApiTable :props="props" :emits="emits" />
