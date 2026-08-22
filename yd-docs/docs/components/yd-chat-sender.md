<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingAiDemos from '../.vitepress/theme/components/demos/InteractiveRemainingAiDemos.vue'

const srcBasic = `<script setup>
import { YdChatSender } from '@yudream/components'
import { ref } from 'vue'

const attachments = ref([])
const loading = ref(false)

function handleSend(text, files) {
  console.log('发送', text, files)
  // 调用 useYdChatStream 的 send(text, files)，由流式组合式函数驱动消息列表
}

function handleStop() {
  // 中断当前 SSE / WebSocket 流
}
<\/script>

<template>
  <YdChatSender
    v-model:attachments="attachments"
    :loading="loading"
    :suggestions="['帮我写周报', '总结这篇文档']"
    @send="handleSend"
    @stop="handleStop"
    @suggestion-click="text => handleSend(text, [])"
  />
</template>`

const srcMention = `<script setup>
// @ 触发的上下文候选项：由页面提供数据并自行解析业务语义
const mentionItems = [
  { key: 'wiki', label: '知识库', value: 'wiki', icon: 'i-ri:book-2-line' },
  { key: 'agent', label: '代码审查 Agent', value: 'agent', icon: 'i-ri:robot-2-line' },
]

function onMentionSelect(item) {
  // 记录用户选择的上下文，发送时随请求体带给后端
  console.log('选择上下文', item.value)
}
<\/script>

<template>
  <YdChatSender :mention-items="mentionItems" @mention-select="onMentionSelect" @send="handleSend" />
</template>`

const props = [
  ['<code>loading</code>', '<code>boolean</code>', '<code>false</code>', '生成中：发送按钮切换为「停止生成」，输入与附件按钮禁用'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '整体禁用（建议 chips、输入框、附件按钮）'],
  ['<code>placeholder</code>', '<code>string</code>', '取全局配置，再回退 <code>\'尽管问，或让我帮你做点什么…\'</code>', '输入框占位文案'],
  ['<code>suggestions</code>', '<code>string[]</code>', '<code>[]</code>', '输入框上方的建议问题 chips，点击发 <code>suggestionClick</code>'],
  ['<code>attachments</code>', '<code>YdChatAttachment[]</code>', '<code>[]</code>', '当前附件列表，支持 <code>v-model:attachments</code>；组件内渲染在输入框上方并可删除'],
  ['<code>accept</code>', '<code>string</code>', '<code>\'image/*,.pdf,.txt,.md,.doc,.docx,.xls,.xlsx,.ppt,.pptx\'</code>', '文件选择器接受的类型'],
  ['<code>multiple</code>', '<code>boolean</code>', '<code>true</code>', '是否允许多选文件'],
  ['<code>disclaimer</code>', '<code>string</code>', '取全局配置，再回退 <code>\'内容由余梦 AI 生成，请核对重要信息\'</code>', '输入框底部的免责声明'],
  ['<code>large</code>', '<code>boolean</code>', '<code>true</code>', '大输入框模式（Kimi 风格圆角，rows=3）；<code>false</code> 时 rows=2'],
  ['<code>mentionItems</code>', '<code>YdSuggestionItem[]</code>', '<code>[]</code>', '<code>@</code> 触发的上下文候选项，由使用页面提供并自行解析业务语义'],
  ['<code>constrained</code>', '<code>boolean</code>', '<code>false</code>', '页面级助手使用受限内容宽度（composer 与 chips 限宽 920px 居中），嵌入式会话保持父容器宽度'],
]

const emits = [
  ['<code>send</code>', '<code>(text: string, attachments: YdChatAttachment[])</code>', '发送（Enter 或点击发送按钮）；文本已 <code>trim</code>，发送后输入框清空'],
  ['<code>stop</code>', '<code>()</code>', '点击停止按钮（<code>loading</code> 时显示）'],
  ['<code>suggestionClick</code>', '<code>(text: string)</code>', '点击建议问题 chip'],
  ['<code>attach</code>', '<code>(files: File[])</code>', '选择文件、拖拽或粘贴文件；上传由业务侧完成，成功后写入 <code>attachments</code>'],
  ['<code>removeAttachment</code>', '<code>(attachment: YdChatAttachment)</code>', '点击附件删除按钮'],
  ['<code>update:attachments</code>', '<code>(attachments: YdChatAttachment[])</code>', '<code>v-model:attachments</code>'],
  ['<code>mentionSelect</code>', '<code>(item: YdSuggestionItem)</code>', '选择 <code>@</code> 候选项；组件会自动把 <code>@xxx</code> 写回输入框'],
]

const slots = [
  ['<code>actions</code>', '输入框底部工具条（附件按钮与免责声明之间），可放上下文选择、模型切换等控件'],
]

const attachment = [
  ['<code>fileId</code>', '<code>string</code>', '后端文件 id（<strong>必须是 string</strong>，后端 Long 序列化为字符串）'],
  ['<code>fileName</code>', '<code>string</code>', '文件名（必填）'],
  ['<code>contentType</code>', '<code>string</code>', 'MIME 类型'],
  ['<code>size</code>', '<code>number</code>', '文件大小'],
  ['<code>kind</code>', '<code>\'IMAGE\' | \'DOCUMENT\' | \'FILE\'</code>', '附件类型，影响展示图标'],
  ['<code>url</code>', '<code>string</code>', '访问地址'],
  ['<code>extractedText</code>', '<code>string</code>', '抽取后的文本内容'],
  ['<code>dataUrl</code>', '<code>string</code>', '本地预览 dataURL'],
]
</script>

# YdChatSender 输入框

AI 对话页底部的输入区：Kimi 风格大圆角输入框，内置建议问题 chips、附件选择（按钮 / 拖拽 / 粘贴三种入口）、`@` 上下文提及、免责声明与「发送 / 停止生成」切换。组件只负责采集输入并发事件——文件上传、请求发送与流式接收全部由业务侧配合 `useYdChatStream` 完成。

源码：`yudream-frontend/packages/components/src/ai/YdChatSender.vue`

## 基础用法

<Demo title="输入框" description="真实组件；输入、建议问题和发送只使用本地 mock" :source="srcBasic">
  <ClientOnly><InteractiveRemainingAiDemos demo="sender" /></ClientOnly>
</Demo>

## @ 上下文提及

<Demo title="mentionItems" description="真实组件；输入 @ 打开并选择本地上下文候选" :source="srcMention">
  <ClientOnly><InteractiveRemainingAiDemos demo="sender-mention" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatSender Props" :data="props" />

### Emits

<ApiTable title="YdChatSender Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

<ApiTable title="YdChatSender Slots" :data="slots" :columns="['插槽', '说明']" />

### YdChatAttachment

`attachments` 数组元素的类型，声明在 `yudream-frontend/packages/components/src/ai/useYdChatStream.ts` 的 `export interface YdChatAttachment`：

<ApiTable title="YdChatAttachment 字段" :data="attachment" :columns="['字段', '类型', '说明']" />

## 交互细节

```mermaid
flowchart TD
  A[用户输入] --> B{按 Enter 且未按 Shift?}
  B -- 是 --> C{canSend?<br/>非 disabled 且非 loading<br/>且文本非空}
  C -- 是 --> D[emit send<br/>清空输入框]
  C -- 否 --> E[忽略]
  B -- 否 --> F[换行继续输入]
  G[拖拽 / 粘贴 / 点击＋] --> H[emit attach files]
  H --> I[业务上传后写入 attachments]
  I --> J[输入框上方展示可删除附件]
  D --> K[loading = true<br/>发送按钮切换为停止]
  K --> L[emit stop 中断流]
```

- **Enter 发送、Shift+Enter 换行**；`canSend` 为 `false` 时 Enter 不生效。
- 附件有三个入口：工具条 `＋` 按钮（受 `accept` / `multiple` 约束）、composer 区域拖拽（`drop`）、textarea 粘贴文件，三者统一走 `attach` 事件。
- `@` 提及由内置 `YdSuggestion` 实现：输入 `@` 后按候选过滤，键盘上下选择、Enter 确认；选中后组件把 `@{value} ` 写回输入框并抛出 `mentionSelect`，业务语义（如切换知识库作用域）完全由页面解析。
- `loading` 时建议 chips 与附件按钮禁用，发送按钮替换为「停止生成」。

## 注意事项

- 组件**不上传文件**：`attach` 只回传原始 `File[]`，上传到后端拿到 `fileId` 后由业务侧 push 进 `attachments`。
- **`fileId` 一律使用 string**。后端文件 id 是雪花 Long，JSON 中序列化为字符串；禁止 `Number(fileId)` 转换，否则超出 `Number.MAX_SAFE_INTEGER` 会精度丢失。
- `send` 事件不携带历史消息；历史裁剪与请求体组装由 `useYdChatStream` 的 `send(text, attachments)` 负责，页面只需在事件里转发。
- 免责声明在移动端（`max-width: 640px`）会被隐藏，工具条允许换行。

> 相关组件：`YdAttachmentList`、`YdSuggestion`。真实使用参考 `yudream-frontend/apps/core-arco-design-vue/src/views/platform/chat/index.vue`、`yudream-frontend/apps/component-showcase/src/sections/AiSection.vue`。
