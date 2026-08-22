<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingBasicScrollArea from '../.vitepress/theme/components/demos/InteractiveRemainingBasicScrollArea.vue'

const srcBasic = `<script setup>
// 组件已由框架自动导入，无需手动 import
<\/script>

<template>
  <!-- 必须限制高度才能出现滚动 -->
  <FaScrollArea class="h-72 w-48 border rounded-md">
    <div v-for="item in 20" :key="item" class="p-4 text-sm">
      {{ item }}
    </div>
  </FaScrollArea>
</template>`

const srcHorizontal = `<template>
  <FaScrollArea horizontal class="overscroll-contain border rounded-md w-96">
    <div class="flex-center-start">
      <div v-for="item in 20" :key="item" class="shrink-0 h-16 w-16 text-sm">
        {{ item }}
      </div>
    </div>
  </FaScrollArea>
</template>`

const srcMask = `<template>
  <FaScrollArea mask class="h-72 w-48 border rounded-md">
    <div v-for="item in 20" :key="item" class="p-4 text-sm">
      {{ item }}
    </div>
  </FaScrollArea>
</template>`

const srcScrollTo = `<script setup>
import { ref } from 'vue'

const scrollRef = ref()

function toTop() {
  scrollRef.value?.scrollTo(0, 'smooth')
}
<\/script>

<template>
  <FaButton @click="toTop">回到顶部</FaButton>
  <FaScrollArea ref="scrollRef" class="h-72 border rounded-md">
    <!-- 长内容 -->
  </FaScrollArea>
</template>`

const props = [
  ['<code>horizontal</code>', '<code>boolean</code>', '<code>false</code>', '启用水平滚动；此时滚轮的纵向滚动会被转换为横向 <code>scrollBy</code>'],
  ['<code>scrollbar</code>', '<code>boolean</code>', '<code>true</code>', '是否显示滚动条；<code>false</code> 时滚动条以 <code>opacity-0 pointer-events-none</code> 隐藏，仍可滚动'],
  ['<code>mask</code>', '<code>boolean</code>', '<code>false</code>', '在内容可滚动的边缘显示渐变遮罩（基于 CSS Scroll Timeline 驱动）'],
  ['<code>class</code>', '<code>string</code>', '—', '外层容器 class；通常在这里设置 <code>h-72</code> 等尺寸约束'],
  ['<code>contentClass</code>', '<code>string</code>', '—', '内部滚动内容区（<code>ScrollAreaRoot</code>）class'],
]

const emits = [
  ['<code>onScroll</code>', '<code>(event: Event) => void</code>', '滚动时触发，来自内部 viewport 的 <code>scroll</code> 事件透传'],
]
</script>

# FaScrollArea 滚动区域

自定义滚动条的滚动容器，支持水平/垂直滚动、渐变遮罩与滚动事件监听。基于 reka-ui 的 `ScrollAreaRoot` / `ScrollAreaViewport` / `ScrollBar` 封装，并额外提供了 `scrollTo` 方法与 `mask` 遮罩能力。

## 使用场景

- 长列表、侧边栏、聊天消息的滚动容器
- 水平滚动的卡片列表
- 需要隐藏滚动条但保留滚动能力的区域

## 基础用法

**必须通过 `class` 设置 `height` / `max-height` 等尺寸约束**，内容超出后才会出现滚动。

<Demo title="垂直滚动" :source="srcBasic">
  <ClientOnly><InteractiveRemainingBasicScrollArea /></ClientOnly>
</Demo>

## 水平滚动

`horizontal` 启用后渲染横向 `ScrollBar`，并且滚轮的 `deltaY` 会被转换为 `scrollBy({ left })`，方便用鼠标滚轮横向浏览。内容需要超出容器宽度（子项 `shrink-0`）。

<Demo title="水平滚动" :source="srcHorizontal">
  <ClientOnly><InteractiveRemainingBasicScrollArea /></ClientOnly>
</Demo>

## 渐变遮罩

`mask` 开启后，在可滚动的边缘显示渐变遮罩，提示「下方/右侧还有内容」。内部实现基于 CSS `@property` + Scroll Timeline（`scroll-timeline-name: --scroll-area-mask-timeline`），遮罩随滚动位置自动淡出；`can-scroll` class 由 ResizeObserver 监测「内容是否超出视口」后写入。

<Demo title="渐变遮罩" :source="srcMask">
  <ClientOnly><InteractiveRemainingBasicScrollArea /></ClientOnly>
</Demo>

## 编程式滚动

组件通过 `defineExpose` 暴露 `scrollTo(scrollNumber, behavior)`；水平模式下滚动 `left`，垂直模式下滚动 `top`。同时也暴露了内部 `ref` 供高级场景取用 viewport 元素。

<Demo title="scrollTo" :source="srcScrollTo">
  <ClientOnly><InteractiveRemainingBasicScrollArea /></ClientOnly>
</Demo>

## 内部结构

```mermaid
flowchart TD
    A["外层 div (class, overflow-hidden)"] --> B["ScrollAreaRoot (contentClass, dir)"]
    B --> C["ScrollAreaViewport<br/>(scroll 事件 → onScroll)<br/>(wheel → horizontal 时转 scrollBy)"]
    B --> D["ScrollBar<br/>(horizontal 时渲染横向)"]
    B --> E[ScrollAreaCorner]
    F["ResizeObserver<br/>(viewport + 首个子元素)"] -.->|尺寸变化时| G["派发 scroll 事件<br/>刷新 can-scroll / mask 状态"]
```

- 组件会监听容器与内容尺寸变化，变化时主动向 viewport 派发 `scroll` 事件，保证遮罩与滚动条状态即时刷新。
- 文字方向跟随 `useTextDirection()` 自动设置 `ltr/rtl`。

## API

### Props

<ApiTable title="FaScrollArea Props" :data="props" />

### Emits

<ApiTable title="FaScrollArea Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 滚动内容 |

### Exposed

| 名称 | 类型 | 说明 |
| --- | --- | --- |
| `scrollTo` | `<code>(scrollNumber: number, behavior?: ScrollBehavior) => void</code>` | 滚动到指定位置；`behavior` 默认 `'auto'`，可传 `'smooth'` |
| `ref` | `<code>ShallowRef<ScrollAreaRootExpose \| null></code>` | 内部 `ScrollArea` 组件引用，可通过 `.el.viewportElement` 取到 viewport 元素 |

::: warning 注意事项
- 不设置高度约束就不会滚动——这是使用该组件最常见的踩坑点。
- `horizontal` 模式会接管 `wheel` 事件做纵向转横向滚动，页面级纵向滚动经过该区域时会被拦截。
- `mask` 依赖 CSS Scroll Timeline 特性，旧浏览器上遮罩可能不生效，但不影响滚动功能。
:::

::: info 源码位置
- 封装组件：`yudream-frontend/packages/components/src/basic/scroll-area/index.vue`
- 子组件：`yudream-frontend/packages/components/src/basic/scroll-area/scroll-area/`（`ScrollArea.vue` / `ScrollBar.vue`）
- 示例：`yudream-frontend/packages/components/src/basic/scroll-area/_examples/`
:::
