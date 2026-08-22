<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveMediaChatWindow from '../.vitepress/theme/components/demos/InteractiveMediaChatWindow.vue'

const srcBasic = `<script setup>
import { YdChatWindow } from '@yudream/components'
import { ref } from 'vue'

const visible = ref(true)
<\/script>

<template>
  <YdChatWindow
    v-if="visible"
    title="AI 助手"
    :width="480"
    :height="640"
    @close="visible = false"
    @expand="$router.push('/assistant')"
  >
    <!-- 内部放完整的对话区：消息列表 + 输入框 -->
    <YdChatMessageList :messages="messages" />
    <YdChatSender @send="send" />
  </YdChatWindow>
</template>`

const srcExpand = `<script setup>
import { YdChatWindow } from '@yudream/components'

// 挂在全局布局上的悬浮助手：点最大化/全屏改为路由跳转到全屏页
<\/script>

<template>
  <YdChatWindow expand-only @expand="router.push('/ai/fullscreen')" @close="visible = false">
    <ChatBody />
  </YdChatWindow>
</template>`

const props = [
  ['<code>title</code>', '<code>string</code>', "<code>'AI 助手'</code>", '标题栏文字，前置一个聊天图标'],
  ['<code>width</code>', '<code>number</code>', '<code>480</code>', '初始宽度（px）'],
  ['<code>height</code>', '<code>number</code>', '<code>640</code>', '初始高度（px）'],
  ['<code>minWidth</code>', '<code>number</code>', '<code>360</code>', '拖拽缩小的最小宽度'],
  ['<code>minHeight</code>', '<code>number</code>', '<code>420</code>', '拖拽缩小的最小高度'],
  ['<code>resizable</code>', '<code>boolean</code>', '<code>true</code>', '是否显示右下角缩放手柄（最大化 / 全屏时隐藏）'],
  ['<code>maximizable</code>', '<code>boolean</code>', '<code>true</code>', '标题栏显示「最大化」按钮（<code>expand-only</code> 时行为改变，见下文）'],
  ['<code>fullscreenable</code>', '<code>boolean</code>', '<code>true</code>', '标题栏显示「全屏」按钮，使用浏览器 Fullscreen API'],
  ['<code>expandOnly</code>', '<code>boolean</code>', '<code>false</code>', '最大化 / 全屏按钮不再就地放大，而是对外抛出 <code>expand</code>（由父级跳转全屏页）'],
]

const emits = [
  ['<code>close</code>', '<code>()</code>', '点击标题栏关闭按钮；组件不自我销毁，由父级控制 v-if'],
  ['<code>expand</code>', '<code>()</code>', '<code>expand-only</code> 模式下点击最大化或全屏按钮时触发'],
]

const slots = [
  ['<code>default</code>', '窗口主体内容；容器是纵向 flex，通常放 <code>YdChatMessageList</code> + <code>YdChatSender</code>'],
]
</script>

# YdChatWindow 聊天窗口

可拖拽、可缩放、可最大化的浮动 AI 对话窗壳：`position: fixed` 悬浮在页面上方，标题栏支持按住拖动、最大化与浏览器全屏，右下角提供缩放手柄。组件只负责「窗口」本身——内部放什么（消息列表、输入框、会话列表）完全由默认插槽决定。

源码：`yudream-frontend/packages/components/src/ai/YdChatWindow.vue`

## 基础用法

<Demo title="浮动窗口" description="点击后打开真实窗口，可拖动、缩放、最大化 / 全屏和关闭；内容完全本地">
  <ClientOnly><InteractiveMediaChatWindow /></ClientOnly>
</Demo>

## expand-only 模式

<Demo title="窗口事件" description="真实窗口关闭、最大化和全屏均可直接交互；demo 仅显示 expand 事件，不跳转路由" :source="srcExpand">
  <ClientOnly><InteractiveMediaChatWindow /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatWindow Props" :data="props" />

### Emits

<ApiTable title="YdChatWindow Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

<ApiTable title="YdChatWindow Slots" :data="slots" :columns="['插槽', '说明']" />

## 交互模型

```mermaid
flowchart TD
  A[标题栏 pointerdown] --> B{最大化/全屏<br/>或目标是 button?}
  B -- 是 --> C[忽略]
  B -- 否 --> D[监听 window pointermove/up<br/>拖动 x/y，钳制 >= 0]
  E[右下角手柄 pointerdown] --> F[pointermove 改 width/height<br/>钳制到 minWidth/minHeight]
  G[点击最大化] --> H{expand-only?}
  H -- 是 --> I[emit expand]
  H -- 否 --> J[铺满视口 100%]
  K[点击全屏] --> L{expand-only?}
  L -- 是 --> I
  L -- 否 --> M[requestFullscreen / exitFullscreen<br/>并 watch fullscreenElement 同步状态]
  N[点击关闭] --> O[emit close<br/>父级 v-if 销毁]
```

- 拖动与缩放都在 `window` 上挂 `pointermove` / `pointerup` 监听，`onBeforeUnmount` 时兜底移除；标题栏内点击按钮不会触发拖动（`closest('button')` 判断）。
- 最大化与全屏互斥：已全屏时最大化按钮不响应；两者任一生效时样式统一切换为 `left/top 0 + 100%/100%`。
- 全屏使用浏览器 Fullscreen API（需 `document.fullscreenEnabled`），并通过 watch `document.fullscreenElement` 双向同步——用户按 Esc 退出时组件状态也能复位。

## 注意事项

- 窗口是 `position: fixed` 且 `z-index: 1000`，会悬浮在页面所有内容之上；不需要时务必用 `v-if` 卸载。
- 组件**没有内置遮罩与 teleport**，位置状态（x/y/宽高）也不受控——外部无法通过 prop 复位窗口位置；如需持久化位置请在业务侧自行处理。
- 默认插槽容器是纵向 flex 且 `min-height: 0`，直接把 `YdChatMessageList` 与 `YdChatSender` 依次放进去即可获得正确的滚动布局。
- `expand-only` 场景下最大化与全屏按钮都发同一个 `expand` 事件，按钮图标仍按各自状态渲染。

> 相关组件：`YdChatMessageList`、`YdChatSender`、`YdChatSessionList`。真实使用参考 `yudream-frontend/apps/core-arco-design-vue/src/views/platform/chat/index.vue`。
