<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingAiDemos from '../.vitepress/theme/components/demos/InteractiveRemainingAiDemos.vue'

const srcBasic = `<script setup>
import { YdChatActions } from '@yudream/components'
import { ref } from 'vue'

const feedbackValue = ref(null)
<\/script>

<template>
  <YdChatActions
    copy-text="要复制的回答全文…"
    regenerable
    feedback
    :feedback-value="feedbackValue"
    @copy="text => navigator.clipboard.writeText(text)"
    @regenerate="regenerate()"
    @feedback="value => (feedbackValue = value)"
  />
</template>`

const srcItems = `<script setup>
import { YdChatActions } from '@yudream/components'

// 自定义操作项：icon 为 iconify 名称，label 同时作为按钮 title 与图标模式外的文字
const items = [
  { key: 'translate', label: '翻译', icon: 'i-ri:translate-2' },
  { key: 'export', label: '导出为文档', icon: 'i-ri:download-2-line', disabled: true },
]
<\/script>

<template>
  <!-- icon-only 模式只显示图标，label 用作悬浮提示 -->
  <YdChatActions :items="items" icon-only @action="item => console.log(item.key)" />
</template>`

const props = [
  ['<code>copyText</code>', '<code>string</code>', "<code>''</code>", '复制的文本内容；为空时不渲染复制按钮'],
  ['<code>regenerable</code>', '<code>boolean</code>', '<code>false</code>', '是否显示「重新生成」'],
  ['<code>feedback</code>', '<code>boolean</code>', '<code>false</code>', '是否显示点赞 / 点踩两个反馈按钮'],
  ['<code>feedbackValue</code>', "<code>'like' | 'dislike' | null</code>", '<code>null</code>', '初始反馈状态（受控初值；组件内部维护后续切换）'],
  ['<code>items</code>', '<code>YdChatActionItem[]</code>', '<code>[]</code>', '自定义操作项，渲染在反馈按钮之后、默认插槽之前'],
  ['<code>iconOnly</code>', '<code>boolean</code>', '<code>false</code>', '紧凑纯图标模式，不显示文字；自定义项的 <code>label</code> 退化为 title 提示'],
]

const emits = [
  ['<code>copy</code>', '<code>(text: string)</code>', '点击复制；组件内部已尝试 <code>navigator.clipboard.writeText</code>，失败也照常抛出由业务兜底'],
  ['<code>regenerate</code>', '<code>()</code>', '点击「重新生成」'],
  ['<code>feedback</code>', "<code>(value: 'like' | 'dislike' | null)</code>", '反馈变化；再次点击同方向取消（回传 null）'],
  ['<code>action</code>', '<code>(item: YdChatActionItem)</code>', '点击自定义操作项，回传整个 item'],
]

const slots = [
  ['<code>default</code>', '追加在所有内置与自定义按钮之后的任意内容'],
]

const actionItem = [
  ['<code>key</code>', '<code>string</code>', '操作标识，随 <code>action</code> 事件原样回传（必填）'],
  ['<code>label</code>', '<code>string</code>', '显示文字 / 悬浮提示'],
  ['<code>icon</code>', '<code>string</code>', 'iconify 图标名（如 <code>i-ri:translate-2</code>）'],
  ['<code>disabled</code>', '<code>boolean</code>', '禁用该按钮'],
]
</script>

# YdChatActions 消息操作栏

AI 回答下方的轻量操作条：复制、重新生成、点赞 / 点踩反馈与自定义操作项。按钮是「图标 + 文字」的幽灵样式，支持 `iconOnly` 纯图标紧凑模式。组件自带剪贴板写入与「已复制」瞬时反馈，业务只需监听事件做持久化。

源码：`yudream-frontend/packages/components/src/ai/YdChatActions.vue`

## 基础用法

<Demo title="完整操作栏" description="真实组件；复制、重新生成、反馈和自定义操作均为本地交互" :source="srcBasic">
  <ClientOnly><InteractiveRemainingAiDemos demo="actions" /></ClientOnly>
</Demo>

## 自定义操作项

<Demo title="items + iconOnly" description="真实操作栏中的本地自定义操作" :source="srcItems">
  <ClientOnly><InteractiveRemainingAiDemos demo="actions" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="YdChatActions Props" :data="props" />

### Emits

<ApiTable title="YdChatActions Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

<ApiTable title="YdChatActions Slots" :data="slots" :columns="['插槽', '说明']" />

### YdChatActionItem

`items` 数组元素的类型，在组件 `<script setup>` 中以 `export interface YdChatActionItem` 声明：

<ApiTable title="YdChatActionItem 字段" :data="actionItem" :columns="['字段', '类型', '说明']" />

## 反馈状态机

```mermaid
stateDiagram-v2
  [*] --> null
  null --> like: 点赞
  null --> dislike: 点踩
  like --> null: 再点赞（取消）
  dislike --> null: 再点踩（取消）
  like --> dislike: 直接点踩
  dislike --> like: 直接点赞
```

- 组件内部持有 `feedbackState`，初始值来自 `feedbackValue` prop；之后切换完全在组件内完成，每次变化通过 `feedback` 事件抛出**最终值**。
- 复制成功后按钮文字变为「已复制」（图标变对勾），1600ms 后自动还原。

## 注意事项

- 复制使用 `navigator.clipboard?.writeText`；非 HTTPS 等场景下可能不可用——组件捕获异常后仍抛 `copy` 事件并显示「已复制」，需要严格兜底时请在事件里自行再写一次。
- 反馈按钮没有文字，只有图标与 `title`（「有帮助」/「没帮助」），不受 `iconOnly` 影响。
- 该组件通常不直接出现在页面里，而是被 `YdBubble` 在 `show-actions` 时内部调用（`copy-text` 取消息正文）；`YdChatMessageList` 则使用自己内联的操作行而非本组件。
- 注意与消息级建议动作区分：`message.actions`（`YdChatAction`）渲染的是气泡下方的胶囊按钮组，语义是「下一步做什么」；本组件是工具栏语义。

> 相关组件：`YdBubble`。真实使用参考 `yudream-frontend/apps/component-showcase/src/sections/AiSection.vue`。
