<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<template>
  <FaAvatar src="https://fantastic-admin.hurui.me/logo.svg" />
</template>`

const srcFallback = `<template>
  <FaAvatar src="" fallback="Hooray" />
</template>`

const srcCustom = `<template>
  <div class="flex space-x--2 *:data-[slot=avatar]:ring-2 *:data-[slot=avatar]:ring-background">
    <FaTooltip text="Fantastic-admin">
      <FaAvatar src="https://github.com/fantastic-admin.png" class="transition hover:(scale-110 z-1)" />
    </FaTooltip>
    <FaTooltip text="Hooray">
      <FaAvatar src="https://github.com/hooray.png" class="transition hover:(scale-110 z-1)" />
    </FaTooltip>
  </div>
</template>`

const props = [
  ['<code>src</code>', '<code>string</code>', '—', '头像图片地址；图片加载失败时自动显示 fallback'],
  ['<code>fallback</code>', '<code>string</code>', '—', '加载失败时的兜底文字，默认截取前 2 个字符展示'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class，可用于自定义尺寸（覆盖 <code>size-8</code>）等'],
]
</script>

# FaAvatar 头像

用于展示用户或实体的头像图片。基于 reka-ui 的 `AvatarRoot` / `AvatarImage` / `AvatarFallback` 封装，内置图片加载失败兜底。

> 源码位置：`yudream-frontend/packages/components/src/basic/avatar/index.vue`（子组件在同目录 `avatar/` 下）

## 基础用法

传入 `src` 展示图片头像，默认 32px 圆形。

<Demo title="基础头像" description="默认 size-8 圆形" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="avatar" /></ClientOnly>
</Demo>

## 加载失败兜底

`src` 为空或加载失败时，显示 `fallback` 文字（最多取前 2 个字符），背景为 muted 色。

<Demo title="Fallback 兜底" description="src 失效时展示 fallback 前 2 个字符" :source="srcFallback">
  <div class="demo-row">
    <span style="display: inline-flex; width: 32px; height: 32px; border-radius: 9999px; background: var(--vp-c-bg-soft); color: var(--vp-c-text-2); font-size: 13px; align-items: center; justify-content: center;">Ho</span>
  </div>
</Demo>

## 自定义样式与组合

通过 `class` 覆盖默认尺寸/交互样式；也可配合 `FaTooltip` 组成带悬浮提示的头像组。

<Demo title="头像组 + Tooltip" description="hover 放大并叠加白色描边" :source="srcCustom">
  <div class="demo-row">
    <span style="display: inline-flex; width: 32px; height: 32px; border-radius: 9999px; background: var(--vp-c-bg-soft); border: 2px solid var(--vp-c-bg); color: var(--vp-c-text-2); font-size: 13px; align-items: center; justify-content: center;">FA</span>
    <span style="display: inline-flex; width: 32px; height: 32px; border-radius: 9999px; background: var(--vp-c-bg-soft); border: 2px solid var(--vp-c-bg); color: var(--vp-c-text-2); font-size: 13px; align-items: center; justify-content: center; margin-left: -8px;">Ho</span>
    <span style="display: inline-flex; width: 32px; height: 32px; border-radius: 9999px; background: var(--vp-c-bg-soft); border: 2px solid var(--vp-c-bg); color: var(--vp-c-text-2); font-size: 13px; align-items: center; justify-content: center; margin-left: -8px;">Ad</span>
  </div>
</Demo>

## 示例讲解

以兜底用法为例：

```vue
<FaAvatar src="" fallback="Hooray" />
```

- 组件内部结构固定为 `Avatar > AvatarImage + AvatarFallback`，由 reka-ui 自动处理图片加载状态。
- 默认插槽可替换 fallback 内容（如自定义图标）；未提供插槽时渲染 `fallback.slice(0, 2)`。
- 根元素带 `data-slot="avatar"`，可用该选择器做批量样式（如上例的描边）。

## API

### Props

<ApiTable title="FaAvatar Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 自定义 fallback 内容（仅图片不可用时显示），缺省渲染 `fallback` 前 2 个字符 |

### Emits

无自定义 emits。
