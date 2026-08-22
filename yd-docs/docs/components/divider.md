<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<template>
  <div>
    <div class="text-sm text-muted-foreground">上方内容</div>
    <FaDivider />
    <div class="text-sm text-muted-foreground">下方内容</div>
    <FaDivider>分割文字</FaDivider>
  </div>
</template>`

const srcPosition = `<template>
  <FaDivider>center</FaDivider>
  <FaDivider position="start">start</FaDivider>
  <FaDivider position="end">end</FaDivider>
</template>`

const props = [
  ['<code>position</code>', "<code>'start' | 'end'</code>", '—', '文字对齐位置；不传时文字居中，两侧线条等宽'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]
</script>

# FaDivider 分割线

区隔内容的分割线，支持纯线条与带文字两种形态，文字位置可控制。

> 源码位置：`yudream-admim` 仓库 `yudream-frontend/packages/components/src/basic/divider/index.vue`

## 基础用法

不提供默认插槽时渲染纯分割线；提供插槽内容时渲染为「线 — 文字 — 线」形态。

<Demo title="基础用法" description="纯分割线与带文字分割线" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="divider" /></ClientOnly>
</Demo>

## 文字位置

通过 `position` 控制文字靠左（`start`）或靠右（`end`），此时对应一侧的线条收缩为最短占位。

<Demo title="文字位置" description="center（默认）/ start / end" :source="srcPosition">
  <div>
    <div style="display: flex; align-items: center; gap: 16px; font-size: 13px; font-weight: 500;">
      <span style="height: 1px; flex: 1; background: var(--vp-c-divider);"></span>
      center
      <span style="height: 1px; flex: 1; background: var(--vp-c-divider);"></span>
    </div>
    <div style="display: flex; align-items: center; gap: 16px; font-size: 13px; font-weight: 500; margin-top: 12px;">
      <span style="height: 1px; flex: 0; min-width: 16px; background: var(--vp-c-divider);"></span>
      start
      <span style="height: 1px; flex: 1; background: var(--vp-c-divider);"></span>
    </div>
    <div style="display: flex; align-items: center; gap: 16px; font-size: 13px; font-weight: 500; margin-top: 12px;">
      <span style="height: 1px; flex: 1; background: var(--vp-c-divider);"></span>
      end
      <span style="height: 1px; flex: 0; min-width: 16px; background: var(--vp-c-divider);"></span>
    </div>
  </div>
</Demo>

## 示例讲解

```vue
<FaDivider position="start">start</FaDivider>
```

- 组件是一个 `flex` 容器，通过 `before` / `after` 伪元素绘制左右两段线条（`bg-border` 主题色）。
- `position="start"` 时 `before` 伪元素 `flex-basis: 0`，线条收缩，文字整体靠左；`end` 同理靠右。
- 仅在有插槽内容时添加 `gap-4`，纯分割线不产生额外间距；组件自带 `my-4` 上下外边距。

## API

### Props

<ApiTable title="FaDivider Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 分割文字；不提供时渲染为纯线条 |

### Emits

无自定义 emits。
