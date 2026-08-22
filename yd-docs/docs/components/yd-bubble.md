<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveMediaBubble from '../.vitepress/theme/components/demos/InteractiveMediaBubble.vue'

const srcBasic = `<script setup>
import { YdBubble } from '@yudream/components'

const userMessage = { role: 'user', content: '帮我总结一下这篇文档的要点' }
const assistantMessage = {
  role: 'assistant',
  content: '好的，以下是文档要点：\\n\\n1. **双 token 认证**……',
}
<\/script>

<template>
  <div style="display: grid; gap: 16px;">
    <YdBubble :message="userMessage" />
    <YdBubble
      :message="assistantMessage"
      show-actions
      feedback
      @copy="msg => console.log('copy', msg)"
    />
  </div>
</template>`

const srcStreaming = `<script setup>
import { YdBubble } from '@yudream/components'

// pending = true 且尚无任何内容时，气泡显示「正在思考…」加载态；
// 首字节到达后切换为 Markdown 正文 + 闪烁光标
const streaming = {
  role: 'assistant',
  pending: true,
  reasoning: '用户想要文档摘要，我应该先浏览全文结构…',
  content: '',
}

const done = {
  role: 'assistant',
  content: '总结如下：…',
  error: false,
}
<\/script>

<template>
  <YdBubble :message="streaming" caret thinking-text="正在检索并思考…" />
</template>`

const props = [
  ['<code>message</code>', '<code>YdChatMessage</code>', '—（必填）', '消息对象；按 <code>role === \'user\'</code> 分为右侧用户气泡与左侧助手消息两种渲染分支'],
  ['<code>transformContent</code>', '<code>(content: string) => string</code>', '取全局配置', '渲染前的内容转换（如 wikilink → markdown 链接）；缺省回退 <code>provideYdChatConfig</code> 的 <code>transformContent</code>'],
  ['<code>thinkingText</code>', '<code>string</code>', '取全局配置，再回退 <code>\'正在思考…\'</code>', '等待首字节的提示文案'],
  ['<code>avatar</code>', '<code>string</code>', '取全局配置，再回退 <code>\'i-ri:sparkling-2-line\'</code>', '助手头像 iconify 名称；传空字符串隐藏头像'],
  ['<code>showActions</code>', '<code>boolean</code>', '<code>false</code>', '显示底部操作栏（复制 / 重新生成 / 反馈），内部由 <code>YdChatActions</code> 渲染'],
  ['<code>feedback</code>', '<code>boolean</code>', '<code>false</code>', '操作栏中启用点赞 / 点踩'],
  ['<code>caret</code>', '<code>boolean</code>', '<code>true</code>', '流式输出时在正文末尾显示闪烁光标'],
  ['<code>imageUrlResolver</code>', '<code>(url: string) => string</code>', '—', '引用图片地址解析（如开发环境补代理前缀），透传给 <code>YdCitationList</code>'],
]

const emits = [
  ['<code>contentClick</code>', '<code>(event: MouseEvent)</code>', '点击助手正文气泡（可拦截 <code>wiki://</code> 等自定义链接）'],
  ['<code>citationClick</code>', '<code>(citation: YdChatCitation)</code>', '点击某条引用'],
  ['<code>copy</code>', '<code>(message: YdChatMessage)</code>', '点击操作栏「复制」（需 <code>showActions</code>）'],
  ['<code>regenerate</code>', '<code>(message: YdChatMessage)</code>', '点击「重新生成」（需 <code>showActions</code>）'],
  ['<code>feedback</code>', "<code>(message: YdChatMessage, value: 'like' | 'dislike' | null)</code>", '点赞 / 点踩变化（需 <code>feedback</code>）'],
]

const slots = [
  ['<code>avatar</code>', '自定义助手头像内容，缺省渲染 <code>FaIcon</code> 图标'],
  ['<code>header</code>', '<code>{ message }</code>', '助手消息体顶部（推理块之前）的自定义区域'],
  ['<code>process</code>', '<code>{ message }</code>', '处理过程区域；业务可在此插入 <code>YdChatProcess</code> 或自定义时间线'],
  ['<code>content</code>', '<code>{ message, rendered }</code>', '替换默认 <code>MdPreview</code> 的正文渲染；<code>rendered</code> 是已应用 <code>transformContent</code> 后的内容'],
  ['<code>footer</code>', '<code>{ message }</code>', '助手消息体末尾（操作栏之后）的自定义区域'],
]
</script>

# YdBubble 消息气泡

AI 对话的单条消息气泡：`role === 'user'` 渲染为右侧主色实底气泡，其余渲染为左侧带头像、支持深度思考 / 处理过程 / 引用 / 操作栏的完整助手消息。正文使用 `md-editor-v3` 的 `MdPreview` 渲染 Markdown，天然适配流式追加。组件是纯展示组件，不含任何网络逻辑，配合 `useYdChatStream` 使用。

源码：`yudream-frontend/packages/components/src/ai/YdBubble.vue`

## 基础用法

<Demo title="用户 / 助手气泡" description="真实气泡、操作栏和本地流式状态；不访问任何 API" :source="srcBasic">
  <ClientOnly><InteractiveMediaBubble /></ClientOnly>
</Demo>

## 流式输出状态

<Demo title="等待首字节 → 流式正文" description="点击按钮以本地定时器驱动真实气泡的 pending、正文和光标状态" :source="srcStreaming">
  <ClientOnly><InteractiveMediaBubble /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdBubble Props" :data="props" />

### Emits

<ApiTable title="YdBubble Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

<ApiTable title="YdBubble Slots" :data="slots" :columns="['插槽', '作用域参数', '说明']" />

## 渲染分支

组件模板只有两个顶层分支，全部行为由此推导：

```mermaid
flowchart TD
  A[message] --> B{role === 'user'?}
  B -- 是 --> C[右侧主色气泡<br/>attachments 在气泡上方]
  B -- 否 --> D[左侧头像 + 消息体]
  D --> E[header 插槽]
  E --> F{message.reasoning?}
  F -- 是 --> G[YdChatReasoning 折叠块]
  G --> H
  F -- 否 --> H[process 插槽]
  H --> I{pending 且无任何内容?}
  I -- 是 --> J[加载态 YdChatLoading]
  I -- 否 --> K[MdPreview 正文 + 光标<br/>error 时红色样式]
  K --> L{citations 非空且非 error?}
  L -- 是 --> M[YdCitationList 引用列表]
  M --> N
  L -- 否 --> N{showActions 且有正文<br/>且非 pending 非 error?}
  N -- 是 --> O[YdChatActions 操作栏]
  O --> P[footer 插槽]
  N -- 否 --> P
```

## 注意事项

- **`message.id` 一律使用 string**。后端消息 id 是雪花 Long，JSON 中序列化为字符串；禁止 `Number(id)` 转换，否则超出 `Number.MAX_SAFE_INTEGER` 会精度丢失。本组件本身不消费 `id`，但列表 `key`、重新生成定位都依赖它。
- 加载态的触发条件是「五项全空」：`pending && !content && !reasoning && !tools?.length && !activities?.length`。只要模型吐出了推理或工具活动，就不再显示加载占位。
- 操作栏出现条件是 `showActions && !error && content && !pending`——错误消息和生成中的消息不会出现复制 / 重新生成。
- 全局配置通过 `provideYdChatConfig` 提供（`yudream-frontend/packages/components/src/ai/chat-context.ts`），组件内逐级回退：props → 全局配置 → 内置默认值。
- 用户气泡用 `white-space: pre-wrap` 直接输出纯文本（不走 Markdown）；助手正文才走 `MdPreview`。

> 相关组件：`YdChatMessageList`（内置了同构的消息布局）、`YdChatReasoning`、`YdChatProcess`、`YdChatActions`。真实使用参考 `yudream-frontend/apps/component-showcase/src/sections/AiSection.vue`。
