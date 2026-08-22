<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingFormIcon from '../.vitepress/theme/components/demos/InteractiveRemainingFormIcon.vue'
import InteractiveRemainingFormIconChat from '../.vitepress/theme/components/demos/InteractiveRemainingFormIconChat.vue'

const srcIcon = `<script setup>
import { FaIcon } from '@yudream/components'
<\/script>

<template>
  <!-- Iconify 名称 -->
  <FaIcon name="i-ri:puzzle-2-line" />
  <!-- SVG sprite -->
  <FaIcon name="./__spritemap#sprite-check" />
  <!-- 图片 URL / 路径 -->
  <FaIcon name="/logo.svg" />
</template>`

const props = [
  ['<code>name</code>', '<code>string</code>', '必填', '图标标识：iconify（<code>i-xxx:yyy</code> 或含 <code>:</code>）、svg sprite（<code>./__spritemap#sprite-x</code>）、图片 URL/路径，三类自动识别'],
  ['<code>transition</code>', '<code>boolean</code>', '<code>false</code>', '切换时过渡动画'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]

const srcChat = `<script setup>
import { ref } from 'vue'
import { YdBubble, YdChatWindow } from '@yudream/components/ai'

const visible = ref(false)
const message = { role: 'assistant', content: '本地 mock 消息，不发起网络请求。' }
<\/script>

<template>
  <button @click="visible = true">打开聊天窗口</button>
  <YdChatWindow v-if="visible" title="本地 AI 助手" @close="visible = false">
    <YdBubble :message="message" />
  </YdChatWindow>
</template>`
</script>

# FaIcon 图标

统一图标组件：自动识别 Iconify、SVG sprite、图片 URL 三类来源；尺寸跟随字号（`1em`）。

## 基础用法

<Demo title="三种来源" :source="srcIcon">
  <ClientOnly><InteractiveRemainingFormIcon /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaIcon Props" :data="props" />

---

# Yd* YuDream 原创组件（`@yudream/components/ai`）

YuDream 原创组件与 composable 集合，当前示例用于 AI 对话场景；组件归属不以 AI 或非 AI 区分。组件自带 scoped CSS 并消费中性语义变量。

## 典型组合

<Demo title="悬浮 AI 助手窗" description="本地 mock 消息驱动的真实窗口交互，不发起网络请求" :source="srcChat">
  <ClientOnly><InteractiveRemainingFormIconChat /></ClientOnly>
</Demo>

## 数据层 composable

| 函数 | 返回 | 说明 |
| --- | --- | --- |
| `useYdChatStream(options)` | `{ messages, streaming, toolHint, send(question, attachments?), stop(), clear() }` | 核心流式对话。协议 `'yd'`（SSE：delta/tool/citations/done/error）或 `'agui'`（AG-UI 事件，SSE/WebSocket 双传输，支持客户端工具 `onToolCallRequest`） |
| `useYdChat(options)` | 在 stream 之上增加 `input`、`attachments`、`sendInput(text?)`、`regenerate(message)`、`setMessages`、`addAttachment`、`removeAttachment`、`empty` | 面向表单的完整封装 |
| `ydRequest(url, body?, options?)` | `Promise<T>` | 底层请求原语：token 注入、120s 默认超时 |
| `readYdStream(response, onEvent)` | — | SSE 帧解析（`event:`/`data:`，多行 data） |
| `provideYdChatConfig(config)` / `useYdChatConfig()` | — | 全局配置：placeholder/suggestions/thinkingText/reasoningTitle/disclaimer/assistantAvatar/transformContent |

常用 options：`endpoint`（string \| () => string）、`protocol`/`transport`、`buildBody`/`buildMessage`/`getWebSocketToken`、`historyLimit=10`、`historyContent?`、`onTool`/`onDone`/`onError`。

消息模型 `YdChatMessage`：`role('user'\|'assistant')`、`content`、`reasoning?`（深度思考）、`citations?`（引用）、`tools?`（工具事件）、`activities?`、`actions?`、`attachments?`、`context?`、`error?`、`pending?`。

## 组件一览

| 组件 | 用途 | 关键 Props | 关键 Emits |
| --- | --- | --- | --- |
| `YdChatWindow` | 可拖拽/缩放/最大化/全屏的悬浮助手窗 | `title='AI 助手'`, `width=480`, `height=640`, `minWidth/minHeight`, `resizable/maximizable/fullscreenable=true`, `expandOnly` | `close`, `expand` |
| `YdChatSender` | 输入区（附件、@提及、免责声明、停止） | `loading/disabled/placeholder/suggestions/attachments(v-model:attachments)/accept/multiple/disclaimer/large=true/mentionItems/constrained` | `send(text,attachments)`, `stop`, `suggestionClick`, `attach(files)`, `removeAttachment`, `mentionSelect` |
| `YdChatMessageList` | 消息流渲染（markdown、深度思考折叠、引用、回到底部） | `messages`, `transformContent`, `thinkingText`, `reasoningTitle`, `compact`, `imageUrlResolver` | `contentClick/citationClick/retrievalClick/graphNodeClick/actionClick/copyMessage/regenerateMessage` |
| `YdBubble` | 单条消息气泡（user/assistant 双态） | `message`, `transformContent/thinkingText/avatar/showActions/feedback/caret/imageUrlResolver` | `contentClick/citationClick/copy/regenerate/feedback(message, 'like'\|'dislike'\|null)` |
| `YdWelcome` | 空态欢迎页 + 推荐提问网格 | `title/description/suggestions/icon` | `select(text)` |
| `YdPrompts` | 提示词 chips | `items: YdPromptItem[]`, `title/vertical/wrap` | `select(item)` |
| `YdThoughtChain` | 执行步骤链（成功/失败/运行中，可折叠） | `items: YdThoughtChainItem[]`, `collapsible=true`, `defaultExpanded=true`, `title='执行过程'` | slot `extra({item,index})` |
| `YdCitationList` | 引用来源列表 + 引用图片墙 | `citations`, `label='引用来源'`, `defaultOpen=true`, `imageUrlResolver` | `select(citation)` |
| `YdAttachmentList` | 附件 chips（图片缩略图/文件图标） | `attachments`, `removable=false` | `remove(attachment)` |

其余：`YdChatActions`（操作栏）、`YdChatLoading`、`YdChatReasoning`（深度思考块）、`YdChatProcess`（过程流）、`YdChatSessionList`（会话列表）、`YdChatGraph`（检索关系图）、`YdSuggestion`（@候选项浮层）。

::: warning 与后端 SSE 约定
后端 AI 流式接口使用结构化信封（`ai.message`/`ai.tool`/`ai.result`/`ai.error`/`ai.progress`）；工具状态经 `message.tools` / `message.activities` 渲染，不混入可见正文。插件注册自定义 Agent 工具见 [FrameworkServices · ai](/plugin/spi/v1/framework-services)。
:::
