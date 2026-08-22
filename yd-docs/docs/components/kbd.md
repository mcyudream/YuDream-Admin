<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<template>
  <div class="flex flex-col gap-2">
    <FaKbd>Ctrl</FaKbd>
    <FaKbd>Alt</FaKbd>
    <FaKbd>Shift</FaKbd>
  </div>
</template>`

const props = [
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]
</script>

# FaKbd 键盘按键

以键盘按键样式展示单个按键或快捷键字符，常用于快捷键提示、帮助文档。渲染为原生 `<kbd>` 元素。

> 源码位置：`yudream-frontend/packages/components/src/basic/kbd/index.vue`

## 基础用法

默认插槽传入按键文字即可。

<Demo title="单个按键" description="muted 背景 + 次要文字色的按键样式" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="kbd" /></ClientOnly>
</Demo>

## 组合快捷键

多个按键用 `FaKbdGroup` 包裹排列，参见 [FaKbdGroup](./kbd-group)。

## 示例讲解

```vue
<FaKbd>Ctrl</FaKbd>
```

- 组件为纯样式封装：`h-5 min-w-5` 的行内 flex 容器，muted 背景配次要文字色，`select-none` 防止误选中。
- 内部 SVG 图标自动缩放为 `size-3`，可直接放图标表达方向键等特殊按键。
- 在 `FaTooltip` 内使用时会自动切换为反色样式（适配 tooltip 深色背景）。

## API

### Props

<ApiTable title="FaKbd Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 按键文字或图标 |

### Emits

无自定义 emits。
