<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<template>
  <div class="flex gap-8">
    <FaBadge :value="true">
      <FaIcon name="i-ri:notification-3-line" />
    </FaBadge>
    <FaBadge :value="99">
      <FaIcon name="i-ri:notification-3-line" />
    </FaBadge>
    <FaBadge value="噢">
      <FaIcon name="i-ri:notification-3-line" />
    </FaBadge>
    <FaBadge value="9" variant="secondary">
      <FaIcon name="i-ri:notification-3-line" />
    </FaBadge>
    <FaBadge value="9" variant="destructive">
      <FaIcon name="i-ri:notification-3-line" />
    </FaBadge>
  </div>
</template>`

const srcDot = `<template>
  <FaBadge :value="hasNew">
    <FaButton variant="outline" size="icon">
      <FaIcon name="i-ri:notification-3-line" />
    </FaButton>
  </FaBadge>
</template>`

const props = [
  ['<code>value</code>', '<code>string | number | boolean</code>', '—', '徽标内容；<code>boolean</code> 时显示小红点，<code>string</code> 非空、<code>number</code> &gt; 0 时显示，否则隐藏'],
  ['<code>variant</code>', "<code>'default' | 'secondary' | 'destructive'</code>", "<code>'default'</code>", '徽标配色变体（小红点与数字徽标共用）'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
  ['<code>badgeClass</code>', '<code>string</code>', '—', '仅作用于数字/文字徽标元素的 class（如调整位置、配色）'],
]
</script>

# FaBadge 徽标

出现在图标或按钮右上角的数字、文字或小红点标记，用于提示有待处理内容。带淡入淡出过渡动画。

> 源码位置：`yudream-frontend/packages/components/src/basic/badge/index.vue`（内部 `Badge` 原子组件在同目录 `badge/` 下）

## 基础用法

`value` 支持三种类型：`boolean` 显示红点，`number` / `string` 显示内容；默认插槽放置被标记的元素。

<Demo title="徽标类型" description="红点 / 数字 / 文字 / 三种配色变体" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="badge" /></ClientOnly>
</Demo>

## 小红点模式

`value` 传 `true` 显示带 ping 动画的小圆点，适合「有新消息」这类无需数量的场景。

<Demo title="小红点" description="value 为 boolean 时渲染红点" :source="srcDot">
  <div class="demo-row">
    <button class="demo-btn" style="position: relative;">通知<span style="position: absolute; top: 4px; right: 4px; width: 6px; height: 6px; border-radius: 9999px; background: var(--vp-c-brand-1);"></span></button>
  </div>
</Demo>

## 示例讲解

```vue
<FaBadge :value="99">
  <FaIcon name="i-ri:notification-3-line" />
</FaBadge>
```

- `value` 为 `number` 时只有 `> 0` 才显示，传 `0` 会自动隐藏徽标，无需额外 `v-if`。
- 徽标绝对定位在插槽内容右上角（`start-[50%] top-0` 向上偏移 50%），出现/消失由 `Transition` 提供淡入淡出。
- 数字徽标内部复用 `Badge` 原子组件（reka-ui `Primitive` 封装），`badgeClass` 可进一步覆盖其定位与配色。

## API

### Props

<ApiTable title="FaBadge Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 被标记的内容（图标、按钮等），徽标叠加在其右上角 |

### Emits

无自定义 emits。
