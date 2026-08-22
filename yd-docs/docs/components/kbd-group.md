<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<template>
  <div class="flex flex-col gap-2">
    <FaKbdGroup>
      <FaKbd>⌘</FaKbd>
      <FaKbd>⇧</FaKbd>
      <FaKbd>K</FaKbd>
    </FaKbdGroup>
    <FaKbdGroup>
      <FaKbd>Ctrl</FaKbd>
      <FaKbd>Alt</FaKbd>
      <FaKbd>Delete</FaKbd>
    </FaKbdGroup>
  </div>
</template>`

const props = [
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]
</script>

# FaKbdGroup 按键组合

将多个 `FaKbd` 组合为一组快捷键展示，内部是带 `gap-1` 的行内 flex 容器。

> 源码位置：`yudream-frontend/packages/components/src/basic/kbd-group/index.vue`

## 基础用法

在 `FaKbdGroup` 内并排放置多个 `FaKbd` 即可。

<Demo title="快捷键组合" description="多个 FaKbd 以 4px 间距排列" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="kbd-group" /></ClientOnly>
</Demo>

## 示例讲解

```vue
<FaKbdGroup>
  <FaKbd>Ctrl</FaKbd>
  <FaKbd>Alt</FaKbd>
  <FaKbd>Delete</FaKbd>
</FaKbdGroup>
```

- 组件渲染为 `<kbd data-slot="kbd-group">`，仅负责水平排列与间距，样式细节由子级 `FaKbd` 决定。
- 根元素带 `data-slot="kbd-group"`，可通过该选择器在外部统一定制组内按键样式。

## API

### Props

<ApiTable title="FaKbdGroup Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 若干 `FaKbd` 按键 |

### Emits

无自定义 emits。
