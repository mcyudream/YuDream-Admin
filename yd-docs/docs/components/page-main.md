<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
import { FaPageMain } from '@yudream/components'
<\/script>

<template>
  <FaPageMain title="基础信息">
    <div>页面主体内容…</div>
  </FaPageMain>
</template>`

const srcCollapse = `<script setup>
import { FaPageMain } from '@yudream/components'
<\/script>

<template>
  <!-- 初始即折叠，高度为 120px，悬停出现展开按钮 -->
  <FaPageMain title="长文本说明" collaspe height="120px">
    <div>很长的内容…</div>
  </FaPageMain>
</template>`
</script>

# FaPageMain 页面主体

标准后台页面的主体容器：外层圆角卡片（`m-4` 外边距 + 边框），可选标题栏，可选折叠能力。典型页面结构为 `FaPageHeader` + 若干 `FaPageMain`。

依据源码：`yudream-frontend/packages/components/src/basic/page-main/index.vue`

## 基础用法

<Demo title="带标题的主体卡片" description="title 渲染为顶部标题栏，也可用 #title 插槽" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="page-main" /></ClientOnly>
</Demo>

## 折叠

设置 `collaspe` 后主体区域可折叠：折叠时高度为 `height` 指定值并显示底部渐变遮罩；悬停主体区域时出现展开/收起按钮（箭头随状态旋转）。注意 prop 拼写为源码中的 `collaspe`。

<Demo title="可折叠主体" description="collaspe + height 控制折叠态高度" :source="srcCollapse">
  <div style="border: 1px solid var(--vp-c-divider); border-radius: 8px; max-width: 420px; overflow: hidden;">
    <div style="background: var(--vp-c-bg-soft); padding: 10px 14px; font-size: 13px; color: var(--vp-c-text-2); font-size: 13px;">长文本说明</div>
    <div style="position: relative; padding: 14px; height: 72px; overflow: hidden;">很长的内容…<span style="color: var(--vp-c-text-2); font-size: 13px; position: absolute; bottom: 0; left: 0; right: 0; height: 32px; line-height: 32px; text-align: center; background: linear-gradient(transparent, var(--vp-c-bg));">（悬停出现展开按钮 ⌄）</span></div>
  </div>
</Demo>

## 示例讲解

以折叠用法为例：

```vue
<FaPageMain title="长文本说明" collaspe height="120px">
  <div>很长的内容…</div>
</FaPageMain>
```

- 标题栏渲染条件是「`title` 非空 **或** 提供了 `#title` 插槽」，二者任一满足即出现 `bg-muted` 底色的标题条；标题栏存在时主体区自动补 `border-t`。
- 折叠按钮是绝对定位在主体区底部的链接型图标按钮，默认 `opacity-0`，悬停整个卡片（`group-hover`）时淡入；展开态下箭头旋转 180°（`rotate-x-180`）。
- 折叠态由两件事共同呈现：主体容器被裁剪（`overflow-hidden`）且高度锁定为 `height`；底部 48px 高的渐变遮罩通过伪元素渐入（`opacity-0 → opacity-100`）。展开后高度交还内容自适应。
- 根元素为圆角边框卡片并自带 `m-4` 外边距，与布局留白配套；需要通栏时可传 `class="m-0"` 覆盖。

## API

### Props

<ApiTable title="FaPageMain Props" :data="[
  ['<code>title</code>', '<code>string</code>', `<code>''</code>`, '标题文本；与 <code>#title</code> 插槽任一存在即渲染标题栏'],
  ['<code>collaspe</code>', '<code>boolean</code>', '<code>false</code>', '启用折叠（注意拼写为源码中的 collaspe）'],
  ['<code>height</code>', '<code>string</code>', `<code>''</code>`, '折叠态的主体高度，如 <code>120px</code>'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
  ['<code>titleClass</code>', '<code>string</code>', '—', '标题栏 class'],
  ['<code>mainClass</code>', '<code>string</code>', '—', '主体内容区 class'],
]" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `title` | 覆盖标题栏内容 |
| `default` | 主体内容 |

### Emits

无自定义 emits；折叠按钮由组件内部状态（`isCollaspe`）切换。
