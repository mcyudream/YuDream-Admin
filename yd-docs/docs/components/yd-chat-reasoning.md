<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveMediaChatReasoning from '../.vitepress/theme/components/demos/InteractiveMediaChatReasoning.vue'

const srcBasic = `<script setup>
import { YdChatReasoning } from '@yudream/components'
import { ref } from 'vue'

const reasoning = ref('用户想了解部署流程，我需要先检索知识库中的运维文档…')
const pending = ref(true) // 模型仍在输出推理时为 true
<\/script>

<template>
  <YdChatReasoning
    :content="reasoning"
    :pending="pending"
    default-expanded
  />
</template>`

const srcConfigured = `<script setup>
import { provideYdChatConfig } from '@yudream/components'

// 页面根部统一提供全局配置后，组件内可省略 title
provideYdChatConfig({
  reasoningTitle: '思考过程',
})
<\/script>

<template>
  <YdChatReasoning content="先拆解问题，再逐条核对文档…" />
</template>`

const props = [
  ['<code>content</code>', '<code>string</code>', '—（必填）', '推理过程文本；以 <code>pre-wrap</code> 纯文本渲染（不解析 Markdown）'],
  ['<code>title</code>', '<code>string</code>', '取全局配置，再回退 <code>\'深度思考\'</code>', '折叠块标题'],
  ['<code>defaultExpanded</code>', '<code>boolean</code>', '<code>false</code>', '初始是否展开；之后展开/收起完全由点击头部切换'],
  ['<code>pending</code>', '<code>boolean</code>', '<code>false</code>', '推理进行中：标题旁的脑图标播放脉冲动画'],
]

const emits = []

const slots = []
</script>

# YdChatReasoning 深度思考

推理模型「思维链」的折叠展示块：一个带脑图标的可点击标题行，点击展开 / 收起推理文本。推理仍在进行时图标有呼吸脉冲动画。组件极小、无任何数据逻辑，通常由 `YdBubble` 在 `message.reasoning` 非空时内部调用。

源码：`yudream-frontend/packages/components/src/ai/YdChatReasoning.vue`

## 基础用法

<Demo title="折叠与展开" description="真实组件；点击标题展开或收起，并可切换 pending 状态" :source="srcBasic">
  <ClientOnly><InteractiveMediaChatReasoning /></ClientOnly>
</Demo>

## 配合全局配置

<Demo title="标题与推理状态" description="可本地修改 title、content 和 pending，点击真实组件标题切换展开状态" :source="srcConfigured">
  <ClientOnly><InteractiveMediaChatReasoning /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatReasoning Props" :data="props" />

### Emits

无自定义事件，交互只是组件内部的 `expanded` 开关。

### Slots

无插槽。

## 注意事项

- 展开状态是**非受控**的：`defaultExpanded` 只决定初始值，父级无法从外部控制展开；需要受控展开请自行用 `v-show` 包一层或改造业务侧容器。
- 正文是纯文本 `pre-wrap` 渲染——模型的思维链通常是半结构化草稿，不走 Markdown 解析。
- 标题回退链：prop `title` → 全局配置 `reasoningTitle`（经 `useYdChatConfig` 注入，见 `yudream-frontend/packages/components/src/ai/chat-context.ts`）→ 内置 `'深度思考'`。
- `YdChatMessageList` 内部的深度思考是**内联实现**而非复用本组件（展开状态按消息索引独立管理）；本组件目前只在 `YdBubble` 中使用。两者样式一致，选型时注意区分。
- 推理内容来自 SSE 事件的 `reasoning` 增量字段，由 `useYdChatStream` 聚合到 `message.reasoning`；`pending` 直接绑定消息的 `pending` 即可。

> 相关组件：`YdBubble`、`YdChatMessageList`。真实使用参考 `yudream-frontend/apps/component-showcase/src/sections/AiSection.vue`。
