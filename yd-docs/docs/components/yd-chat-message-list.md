<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingAiDemos from '../.vitepress/theme/components/demos/InteractiveRemainingAiDemos.vue'

const srcBasic = `<script setup>
import { YdChatMessageList } from '@yudream/components'
import { ref } from 'vue'

const messages = ref([
  { id: '1827364512001', role: 'user', content: '知识库里关于部署的文档有哪些？' },
  {
    id: '1827364512002',
    role: 'assistant',
    reasoning: '用户想找部署相关文档，应先检索知识库…',
    activities: [
      { activityType: 'wiki-retrieval', status: 'complete', title: '检索知识库', hits: [] },
    ],
    content: '找到 2 篇相关文档：\\n\\n1. [部署指南](wiki://deploy)\\n2. [Nginx 配置](wiki://nginx)',
    citations: [{ title: '部署指南', path: '/ops/deploy' }],
    actions: [{ label: '打开部署指南', action: 'open', value: '/ops/deploy' }],
  },
])

function onContentClick(event) {
  // 拦截 wiki:// 链接跳转
}
<\/script>

<template>
  <YdChatMessageList
    :messages="messages"
    @content-click="onContentClick"
    @copy-message="msg => console.log('copy', msg)"
    @regenerate-message="msg => console.log('regenerate', msg)"
  />
</template>`

const srcScroll = `<script setup>
import { YdChatMessageList } from '@yudream/components'
import { ref } from 'vue'

const listRef = ref()

// 发送新消息后可手动强制回到底部（组件在新消息时也会自动滚动）
function afterSend() {
  listRef.value?.scrollToBottom(true)
}
<\/script>

<template>
  <YdChatMessageList ref="listRef" :messages="messages" />
</template>`

const props = [
  ['<code>messages</code>', '<code>YdChatMessage[]</code>', '—（必填）', '消息数组，通常来自 <code>useYdChatStream().messages</code>'],
  ['<code>transformContent</code>', '<code>(content: string) => string</code>', '取全局配置', '渲染前的内容转换（如 wikilink → markdown 链接）'],
  ['<code>thinkingText</code>', '<code>string</code>', '取全局配置，再回退 <code>\'正在检索并思考…\'</code>', '等待首字节的提示文案'],
  ['<code>reasoningTitle</code>', '<code>string</code>', '取全局配置，再回退 <code>\'深度思考\'</code>', '深度思考折叠块标题'],
  ['<code>compact</code>', '<code>boolean</code>', '<code>false</code>', '紧凑模式，适用于约 420px 宽的公开助手窗口；透传给 <code>YdChatProcess</code>/<code>YdChatGraph</code>'],
  ['<code>imageUrlResolver</code>', '<code>(url: string) => string</code>', '—', '引用图片地址解析（如开发环境补代理前缀），透传给引用列表'],
]

const emits = [
  ['<code>contentClick</code>', '<code>(event: MouseEvent)</code>', '点击 assistant 正文气泡（可拦截 <code>wiki://</code> 等自定义链接）'],
  ['<code>citationClick</code>', '<code>(citation: YdChatCitation)</code>', '点击某条引用'],
  ['<code>retrievalClick</code>', '<code>(hit: YdChatRetrievalHit)</code>', '点击处理过程中的检索命中项'],
  ['<code>graphNodeClick</code>', '<code>(node: YdChatGraphNode)</code>', '点击关联图谱中的节点（<code>query</code>/<code>source</code> 角色除外）'],
  ['<code>actionClick</code>', '<code>(action: YdChatAction)</code>', '点击消息下方的建议动作按钮'],
  ['<code>copyMessage</code>', '<code>(message: YdChatMessage)</code>', '点击「复制」'],
  ['<code>regenerateMessage</code>', '<code>(message: YdChatMessage)</code>', '点击「重新生成」'],
]

const slots = [
  ['<code>empty</code>', '列表为空时的内容（欢迎语、推荐问题等）'],
  ['<code>message-extra</code>', '<code>{ message, index }</code>', '每条助手消息体末尾的附加内容；业务可将澄清选项等交互放在对应 assistant 消息下，而不是固定在输入区'],
]
</script>

# YdChatMessageList 消息列表

AI 对话页的消息流主体：按顺序渲染用户 / 助手消息，内置深度思考折叠块、处理过程时间线、工具调用状态、Markdown 正文与流式光标、引用列表、复制 / 重新生成操作、建议动作按钮，以及「回到底部」悬浮钮和智能跟随滚动。数据通常直接绑定 `useYdChatStream` 返回的 `messages`。

源码：`yudream-frontend/packages/components/src/ai/YdChatMessageList.vue`

## 基础用法

<Demo title="消息列表" description="真实消息列表；展开推理、点击引用和操作均只处理本地 mock 数据" :source="srcBasic">
  <ClientOnly><InteractiveRemainingAiDemos demo="message-list" /></ClientOnly>
</Demo>

## 空态与滚动控制

<Demo title="ref 方法" description="真实列表的本地消息与滚动容器" :source="srcScroll">
  <ClientOnly><InteractiveRemainingAiDemos demo="message-list" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatMessageList Props" :data="props" />

### Emits

<ApiTable title="YdChatMessageList Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

<ApiTable title="YdChatMessageList Slots" :data="slots" :columns="['插槽', '作用域参数', '说明']" />

### Expose

| 方法 | 签名 | 说明 |
| --- | --- | --- |
| `scrollToBottom` | `(force?: boolean) => void` | 滚动到底部；`force = true` 强制滚动，否则仅当接近底部（80px 内）时才滚 |

## 单条消息的渲染结构

```mermaid
flowchart TD
  A[message] --> B{role === 'user'?}
  B -- 是 --> C[右侧气泡<br/>attachments 在上方]
  B -- 否 --> D[左侧头像 + 消息体]
  D --> E{reasoning?}
  E -- 是 --> F[深度思考折叠块<br/>默认收起，逐条独立展开]
  F --> G
  E -- 否 --> G{activities?}
  G -- 是 --> H[YdChatProcess 处理过程]
  H --> I
  G -- 否 --> I{tools?}
  I -- 是 --> J[工具状态胶囊行<br/>executing 旋转/complete 绿/error 红]
  J --> K
  I -- 否 --> K{pending 且全空?}
  K -- 是 --> L[三点加载占位 + thinkingText]
  K -- 否 --> M[MdPreview 正文<br/>error 时红色样式<br/>pending 显示光标]
  M --> N{citations 非空且非 error?}
  N -- 是 --> O[YdCitationList]
  O --> P
  N -- 否 --> P{有正文且非 pending 非 error?}
  P -- 是 --> Q[复制 / 重新生成]
  Q --> R{actions?}
  R -- 是 --> S[建议动作按钮组]
  S --> T[message-extra 插槽]
  R -- 否 --> T
```

## 滚动行为

组件维护「用户是否上翻离开底部」的状态：

- `messages.length` 变化（新消息）→ **强制**滚到底。
- 最后一条消息的内容量变化（流式增量，统计正文 / 推理 / 工具 / 活动 / 命中 / 图谱节点边数量之和）→ 仅当用户本就在底部（距底 < 80px）时跟随滚动。
- 用户上翻后显示 sticky 吸附在容器底部的「回到底部」悬浮钮，点击强制回底。

## 注意事项

- **`message.id` 一律使用 string**。后端消息 id 是雪花 Long，JSON 中序列化为字符串；禁止 `Number(id)` 转换。列表渲染 `key` 取 `message.id ?? index`，缺 id 时退化为索引 key，建议始终传 id。
- 列表内的深度思考是**组件内联实现**（非 `YdChatReasoning`），展开状态按消息索引记录在组件内部，默认全部收起。
- 工具名映射：`wiki.search` / `web.fetch` / `web_fetch` 统一显示为「检索知识库」，其余显示原始 `toolName`。
- 正文中的 `wiki://` 链接有专属虚线下划线样式，点击不会自动导航——需要业务在 `@content-click` 中自行拦截并路由。

> 相关组件：`YdBubble`（单条消息版本）、`YdChatProcess`、`YdCitationList`、`YdAttachmentList`。真实使用参考 `yudream-frontend/apps/core-arco-design-vue/src/views/platform/chat/index.vue`、`yudream-frontend/apps/component-showcase/src/sections/AiSection.vue`。
