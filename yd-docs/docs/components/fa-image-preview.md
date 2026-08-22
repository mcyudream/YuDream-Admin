<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveMediaImagePreview from '../.vitepress/theme/components/demos/InteractiveMediaImagePreview.vue'

const srcBasic = `<script setup>
const image = 'data:image/svg+xml,...' // 浏览器内存中的本地 data URL
<\/script>

<template>
  <FaImagePreview :src="image" />
</template>`

const srcSize = `<template>
  <div class="flex flex-wrap gap-4 items-end">
    <FaImagePreview :src="image" class="size-25" />
    <FaImagePreview :src="image" class="size-40" />
    <FaImagePreview :src="image" class="h-40 w-60" />
  </div>
</template>`

const srcError = `<template>
  <!-- 损坏的本地 data URL，不请求远端资源 -->
  <FaImagePreview src="data:image/svg+xml,%3Csvg">
    <template #error>图片加载失败</template>
  </FaImagePreview>
</template>`

const srcFunc = `<script setup>
import { useFaImagePreview } from '@yudream/components'

const images = ['data:image/svg+xml,...', 'data:image/svg+xml,...']
const { open } = useFaImagePreview()

function openSingle() {
  open(images[0])
}

function openMulti() {
  open(images)
}

function openMultiWithIndex() {
  open(images, 1)
}
<\/script>

<template>
  <FaButton @click="openSingle">预览单张</FaButton>
  <FaButton @click="openMulti">预览多张</FaButton>
  <FaButton @click="openMultiWithIndex">预览多张（初始第 2 张）</FaButton>
</template>`

const props = [
  ['<code>src</code>', '<code>string</code>', '—（必填）', '图片地址'],
  ['<code>class</code>', '<code>string</code>', '—', '透传到 <code>&lt;img&gt;</code>，用于覆盖尺寸（默认 <code>size-50</code>）'],
]

const previewProps = [
  ['<code>src</code>', '<code>string | string[]</code>', '—（必填）', '单张地址或多张地址数组'],
  ['<code>index</code>', '<code>number</code>', '<code>0</code>', '多图时初始展示的下标（从 0 开始）'],
  ['<code>v-model</code>', '<code>boolean</code>', '—', '控制预览弹层开关（函数式调用时内部固定为 true）'],
]
</script>

# FaImagePreview 图片预览

图片展示 + 点击放大预览。组件内嵌缩略图，点击后在全屏弹层（Dialog）中查看大图，支持缩放、旋转、拖拽和多图切换。同时提供 `useFaImagePreview` 组合式函数，可在任意触发场景（表格、按钮、链接）命令式打开预览。

## 基础用法

<Demo title="内嵌预览" description="本地图片加载成功后点击打开真实全屏预览" :source="srcBasic">
  <ClientOnly><InteractiveMediaImagePreview /></ClientOnly>
</Demo>

缩略图交互细节：

- 加载中显示 loading 图标（可用 `loading` 插槽自定义），图片本身隐藏。
- 加载失败显示破图图标（可用 `error` 插槽自定义），且**点击不会再打开预览**。
- 悬停时图片有 `scale-110` 放大过渡效果。

## 尺寸

<Demo title="自定义尺寸" description="真实组件的 class 尺寸覆盖" :source="srcSize">
  <ClientOnly><InteractiveMediaImagePreview /></ClientOnly>
</Demo>

## 加载失败

<Demo title="error 插槽" description="真实组件加载本地损坏 data URL 后展示错误插槽" :source="srcError">
  <ClientOnly><InteractiveMediaImagePreview /></ClientOnly>
</Demo>

## 函数式调用

通过 `useFaImagePreview` 的 `open(src, index?)` 命令式打开预览弹层，不需要在模板中放置组件。`open` 内部通过 `createApp` 动态挂载预览组件。

<Demo title="useFaImagePreview" description="open 接受单张地址、地址数组，第二个参数指定初始下标" :source="srcFunc">
  <div style="display: flex; gap: 8px; flex-wrap: wrap;">
    <span style="border: 1px solid var(--vp-c-border); border-radius: 6px; padding: 4px 12px; font-size: 14px;">预览单张</span>
    <span style="border: 1px solid var(--vp-c-border); border-radius: 6px; padding: 4px 12px; font-size: 14px;">预览多张</span>
    <span style="border: 1px solid var(--vp-c-border); border-radius: 6px; padding: 4px 12px; font-size: 14px;">预览多张（初始第 2 张）</span>
  </div>
</Demo>

## 预览弹层能力

预览弹层由内部组件 `preview.vue` 实现，无论内嵌点击还是函数式调用，能力一致：

| 能力 | 说明 |
| --- | --- |
| 缩放 | 工具栏按钮或**鼠标滚轮**，步进 0.25，范围 0.5 ~ 3 倍 |
| 原始尺寸 | 一键恢复 scale = 1 |
| 旋转 | 左/右旋转，每次 ±90° |
| 拖拽 | 按住图片拖动平移，拖动时无过渡动画，松手恢复 |
| 多图切换 | 底部显示 `当前序号 / 总数`，左右箭头循环切换（首尾相接） |
| 状态重置 | 切换图片或关闭弹层后，缩放/旋转/位移全部复位 |

## API

### FaImagePreview Props

<ApiTable title="FaImagePreview Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `loading` | 自定义加载中占位（默认为旋转 loading 图标） |
| `error` | 自定义加载失败占位（默认为破图图标） |

### Emits

| 事件 | 参数 | 说明 |
| --- | --- | --- |
| `load` | 无 | 图片加载成功 |
| `error` | 无 | 图片加载失败 |

### useFaImagePreview

```ts
function useFaImagePreview(): {
  open: (src: string | string[], index?: number) => void
}
```

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `src` | `string \| string[]` | —（必填） | 单张地址或多张地址数组 |
| `index` | `number` | `0` | 多图时初始展示的下标 |

`open` 打开的预览弹层（`preview.vue`）自身也接受以下 props，函数式调用时由 `open` 代为传入：

<ApiTable title="Preview Props" :data="previewProps" />

## 注意事项

- `src` 变化会重置加载状态并重新加载；加载失败状态下点击不会打开预览。
- 组件自动导入，模板中无需手动 import；函数式调用需显式 `import { useFaImagePreview } from '@yudream/components'`（包内导出别名，源码中函数名为 `useImagePreview`）。
- 滚轮缩放绑在整个弹层内容区，会 `preventDefault` 阻止页面滚动。
- 关闭弹层后动态挂载的容器元素保留在 `document.body` 下，频繁调用时每次都会新建一个挂载点，属于正常实现行为。

## 源码引用

- `yudream-frontend/packages/components/src/basic/image-preview/index.vue`（内嵌组件）
- `yudream-frontend/packages/components/src/basic/image-preview/preview.vue`（预览弹层）
- `yudream-frontend/packages/components/src/basic/image-preview/index.ts`（`useImagePreview` 函数式调用）
- `yudream-frontend/packages/components/src/index.ts`（导出别名 `useFaImagePreview`）
