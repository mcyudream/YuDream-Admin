<script setup lang="ts">
import type { YdChatAction, YdChatCitation, YdChatGraphNode, YdChatMessage, YdChatRetrievalHit, YdPromptItem, YdSuggestionItem, YdThoughtChainItem } from '@yudream/components'
import { useFaToast, YdBubble, YdChatLoading, YdChatMessageList, YdChatProcess, YdChatReasoning, YdChatSender, YdChatSessionList, YdCitationList, YdPrompts, YdSuggestion, YdThoughtChain, YdWelcome } from '@yudream/components'
import { onUnmounted, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useFaToast()

const demoCitations: YdChatCitation[] = [
  { title: '快速上手 · 安装与启动', path: '/guide/getting-started', nodeId: 'doc-1' },
  { title: '主题与全局样式配置', path: '/guide/theme', nodeId: 'doc-2' },
  { title: '常见问题 FAQ', path: '/guide/faq', nodeId: 'doc-3' },
]

const demoActions: YdChatAction[] = [
  { label: '打开快速上手', action: 'open', value: '/guide/getting-started' },
  { label: '查看主题配置', action: 'submit', value: '介绍一下主题配置' },
]

const demoActivities = [{
  messageId: 'demo-answer',
  activityType: 'wiki-retrieval',
  status: 'complete' as const,
  title: '检索知识库',
  query: '知识库主要内容',
  hits: [
    { title: '快速上手 · 安装与启动', kind: 'PAGE', score: 0.96, nodeId: 'doc-1', path: '/guide/getting-started', excerpt: '介绍安装、启动和目录约定。' },
    { title: '主题与全局样式配置', kind: 'PAGE', score: 0.91, nodeId: 'doc-2', path: '/guide/theme', excerpt: '说明主题变量与全局样式配置。' },
    { title: '常见问题 FAQ', kind: 'PAGE', score: 0.84, nodeId: 'doc-3', path: '/guide/faq', excerpt: '收录部署、登录和权限相关问题。' },
  ],
}, {
  messageId: 'demo-answer',
  activityType: 'wiki-graph',
  status: 'complete' as const,
  title: '关联知识图谱',
  graph: {
    query: '知识库主要内容',
    nodes: [
      { id: 'doc-1', title: '快速上手', type: 'PAGE', role: 'focus', score: 0.96, path: '/guide/getting-started' },
      { id: 'doc-2', title: '主题配置', type: 'PAGE', score: 0.91, path: '/guide/theme' },
      { id: 'doc-3', title: '常见问题', type: 'PAGE', score: 0.84, path: '/guide/faq' },
    ],
    edges: [
      { source: 'doc-1', target: 'doc-2', weight: 0.89, signal: '配置' },
      { source: 'doc-1', target: 'doc-3', weight: 0.74, signal: '上手支持' },
    ],
  },
}]

const demoMarkdown = `本知识库主要包含 **三个部分**：

1. 快速上手：安装、启动与目录约定
2. 主题配置：通过 \`unocss.config\` 与 CSS 变量定制
3. 常见问题：部署、登录与权限相关 FAQ

\`\`\`bash
pnpm install
pnpm dev
\`\`\`

> 提示：更多细节可查看下方引用来源。`

const bubbleCode = `<YdBubble
  :message="message"
  show-actions
  feedback
  @regenerate="onRegenerate"
  @citation-click="onCitationClick"
/>`

const aguiCode = `const { messages, streaming, send, stop } = useYdChatStream({
  endpoint: () => '/api/platform/wiki/spaces/{spaceId}/chat/agui',
  protocol: 'agui',
  transport: 'sse', // 或 'websocket'
})`

const providerCode = `// 应用根部一次性提供全局默认
provideYdChatConfig({
  placeholder: '有问题尽管问我',
  thinkingText: '余梦正在思考…',
  reasoningTitle: '深度思考',
  disclaimer: '内容由余梦 AI 生成，请核对重要信息',
})`

const streamCode = `import { ydRequest, readYdStream } from '@yudream/components/ai'

// XRequest：统一 JSON 请求（注入 token / 超时 / 取消）
const data = await ydRequest('/api/platform/chat/quota/me', undefined, {
  getToken: () => localStorage.getItem('token') ?? undefined,
})

// XStream：逐事件读取 SSE
const response = await fetch(endpoint, requestInit)
await readYdStream(response, ({ event, data }) => {
  // event: RUN_STARTED / TEXT_MESSAGE_CHUNK / RUN_FINISHED ...
})`

// 静态消息示例：用户提问 / 深度思考 + 工具状态 + Markdown 回答 + 引用 + 建议动作 / 流式输出中 / 错误气泡
const staticMessages: YdChatMessage[] = [
  { role: 'user', content: '这个知识库主要讲什么？' },
  {
    role: 'assistant',
    reasoning: '用户想了解知识库的整体结构，我需要先检索目录页，再按主题归纳回答。',
    activities: demoActivities,
    tools: [
      { toolName: 'wiki.search', status: 'complete', message: '检索到 3 个相关页面' },
    ],
    content: demoMarkdown,
    citations: demoCitations,
    actions: demoActions,
  },
  { role: 'assistant', content: '正在流式输出这段回答，右侧带有闪烁光标…', pending: true },
  { role: 'assistant', content: '问答失败，请稍后重试', error: true },
]

const bubbleUser: YdChatMessage = {
  role: 'user',
  content: '帮我总结一下 YuDreamAdmin 组件库',
  attachments: [{ fileName: '需求文档.pdf', contentType: 'application/pdf', kind: 'DOCUMENT', size: 235_520 }],
}
const bubbleAssistant: YdChatMessage = {
  role: 'assistant',
  reasoning: '用户想要了解组件库的整体结构，先介绍分层再列举 AI 对话元件。',
  content: '**YuDreamAdmin（余梦）组件**分为基础元件与 AI 对话元件两大部分，AI 侧覆盖气泡、会话、输入区、附件、思维链与 AG-UI 流式协议适配。',
  citations: demoCitations.slice(0, 2),
}
const bubbleStreaming: YdChatMessage = { role: 'assistant', content: '正在输出这一段回答', pending: true }
const bubbleThinking: YdChatMessage = { role: 'assistant', content: '', pending: true }

// 本地模拟流式问答（不连后端）：逐字输出一段预置回答
const mockAnswer = demoMarkdown
const chatMessages = ref<YdChatMessage[]>([
  { role: 'user', content: '帮我梳理一下内容目录' },
  { role: 'assistant', content: mockAnswer, citations: demoCitations.slice(0, 2) },
])
const streaming = ref(false)
const suggestions = ['这个知识库主要讲什么？', '帮我梳理一下内容目录', '最近有哪些新摄入的内容？']

let timer: ReturnType<typeof setInterval> | null = null

function mockSend(text: string, _attachments?: unknown) {
  if (streaming.value) {
    return
  }
  chatMessages.value.push({ role: 'user', content: text })
  const answer: YdChatMessage = { role: 'assistant', content: '', pending: true, citations: [], tools: [] }
  chatMessages.value.push(answer)
  streaming.value = true

  let index = 0
  timer = setInterval(() => {
    index += 4
    answer.content = mockAnswer.slice(0, index)
    if (index >= mockAnswer.length) {
      answer.pending = false
      answer.citations = demoCitations.slice(0, 2)
      streaming.value = false
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    }
  }, 60)
}

function mockStop() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  const last = chatMessages.value[chatMessages.value.length - 1]
  if (last?.pending) {
    last.pending = false
    last.content = last.content || '（已停止生成）'
  }
  streaming.value = false
}

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})

const prompts: YdPromptItem[] = [
  { key: 'write', label: '写一份项目周报', icon: 'i-ri:quill-pen-line', description: '结构化输出本周进展与计划' },
  { key: 'kb', label: '梳理知识库内容', icon: 'i-ri:book-2-line', description: '总结重点文档与关联' },
  { key: 'data', label: '分析数据给建议', icon: 'i-ri:line-chart-line', description: '输出可执行建议清单' },
  { key: 'code', label: '审查这段代码', icon: 'i-ri:code-s-slash-line', description: '发现问题并给出修改建议' },
]

const suggestionItems: YdSuggestionItem[] = [
  { key: 'translate', label: '/翻译', icon: 'i-ri:translate-2', description: '把内容翻译成指定语言', value: '请把以下内容翻译成英文：' },
  { key: 'summary', label: '/总结', icon: 'i-ri:file-list-3-line', description: '提炼要点输出摘要', value: '请总结以下内容：' },
  { key: 'polish', label: '/润色', icon: 'i-ri:magic-line', description: '优化表达与结构', value: '请润色以下内容：' },
]
const suggestionText = ref('/')

const chainItems: YdThoughtChainItem[] = [
  { key: 1, title: '理解问题', description: '解析用户意图，确定需要检索知识库', status: 'success' },
  { key: 2, title: '检索知识库', description: '命中 3 个相关页面', status: 'success' },
  { key: 3, title: '生成回答', description: '综合引用来源输出 Markdown 回答', status: 'running' },
  { key: 4, title: '校验引用', status: 'pending' },
]

const demoSessions = [
  { id: 's1', title: '知识库使用咨询', pinned: true, scopeType: 'WIKI', messageCount: 12 },
  { id: 's2', title: '周报撰写助手', scopeType: 'GENERAL', messageCount: 6 },
  { id: 's3', title: '代码审查 Agent', scopeType: 'AGENT', messageCount: 3 },
]

function onCitationClick(citation: YdChatCitation) {
  toast.info(`点击引用：${citation.title}`)
}

function onRetrievalClick(hit: YdChatRetrievalHit) {
  toast.info(`点击检索命中：${hit.title}`)
}

function onGraphNodeClick(node: YdChatGraphNode) {
  toast.info(`点击图谱节点：${node.title}`)
}

function onActionClick(action: YdChatAction) {
  toast.info(`触发动作：${action.label}`)
}
</script>

<template>
  <DemoCard title="YdBubble 对话气泡" description="单条消息气泡：用户/助手、附件、深度思考、Markdown、引用、操作栏与流式光标" :code="bubbleCode">
    <div class="ai-demo ai-demo--bubble">
      <YdBubble :message="bubbleUser" />
      <YdBubble
        :message="bubbleAssistant"
        show-actions
        feedback
        @citation-click="onCitationClick"
        @copy="toast.success('已复制回答')"
        @regenerate="toast.info('触发重新生成')"
        @feedback="(_, value) => toast.info(`反馈：${value ?? '取消'}`)"
      />
      <YdBubble :message="bubbleStreaming" />
      <YdBubble :message="bubbleThinking" thinking-text="余梦正在思考…" />
    </div>
  </DemoCard>

  <DemoCard title="YdChatMessageList 消息列表" description="完整会话视图：深度思考、工具状态、检索/图谱过程、Markdown 渲染、引用来源、建议动作、流式光标、错误气泡">
    <div class="ai-demo ai-demo--static">
      <YdChatMessageList
        :messages="staticMessages"
        @citation-click="onCitationClick"
        @retrieval-click="onRetrievalClick"
        @graph-node-click="onGraphNodeClick"
        @action-click="onActionClick"
      >
        <template #empty>
          暂无消息
        </template>
      </YdChatMessageList>
    </div>
  </DemoCard>

  <DemoCard title="YdChatSessionList 管理对话" description="会话搜索、置顶分组、新建/重命名/删除，按作用域（通用/知识库/Agent）展示图标">
    <div class="ai-demo ai-demo--sessions">
      <YdChatSessionList
        :sessions="demoSessions"
        active-id="s1"
        @select="toast.info(`切换会话 ${$event}`)"
        @create="toast.info('新建会话')"
        @rename="toast.info(`重命名：${$event.title}`)"
        @pin="toast.info(`置顶：${$event.title}`)"
        @remove="toast.info(`删除：${$event.title}`)"
      />
    </div>
  </DemoCard>

  <DemoCard title="YdWelcome 欢迎 + YdPrompts 提示集" description="空态欢迎页与预设提示词（对齐 antd-x Welcome / Prompts），引导用户开始对话">
    <div class="ai-demo ai-demo--welcome">
      <YdWelcome
        title="你好，我是余梦 AI 助手"
        description="支持通用问答、知识库检索与 Agent 应用调用，可上传图片与文档附件。"
        :suggestions="prompts.map(item => item.label)"
        @select="toast.info(`选择：${$event}`)"
      />
    </div>
    <YdPrompts title="试试这些" :items="prompts" @select="toast.info(`提示集：${$event.label}`)" />
  </DemoCard>

  <DemoCard title="YdChatSender 输入框 + 本地模拟流式" description="多行输入、建议问题、拖拽/粘贴附件、发送/停止；发送后本地逐字输出，不请求任何接口">
    <div class="ai-demo ai-demo--chat">
      <YdChatMessageList
        :messages="chatMessages"
        @citation-click="onCitationClick"
      >
        <template #empty>
          <div class="grid justify-items-center gap-2">
            <FaIcon name="i-ri:sparkling-2-line" class="text-2xl text-primary" />
            <span>向示例知识库提问吧</span>
          </div>
        </template>
      </YdChatMessageList>
      <YdChatSender
        :loading="streaming"
        :suggestions="suggestions"
        @send="mockSend"
        @stop="mockStop"
        @suggestion-click="mockSend"
      />
    </div>
  </DemoCard>

  <DemoCard title="YdSuggestion 快捷指令" description="输入 / 唤起快捷指令（对齐 antd-x Suggestion），支持方向键与 Enter 选择">
    <div class="grid max-w-md gap-3">
      <FaInput v-model="suggestionText" placeholder="输入 / 唤起快捷指令" />
      <YdSuggestion :text="suggestionText" :items="suggestionItems" @select="toast.info(`选中指令：${$event.label}`)" />
    </div>
  </DemoCard>

  <DemoCard title="YdThoughtChain 思维链 + YdChatReasoning 深度思考" description="推理步骤时间线（对齐 antd-x ThoughtChain）与可折叠的推理内容块（对齐 tdesign ChatReasoning）">
    <div class="grid max-w-xl gap-4">
      <YdThoughtChain :items="chainItems" title="回答生成过程" />
      <YdChatReasoning content="用户想知道知识库结构，我先检索目录页，再按主题归纳输出。" pending />
    </div>
  </DemoCard>

  <DemoCard title="YdChatLoading 加载态 + YdChatProcess 执行过程" description="等待首字节的三点动画，以及检索/图谱等过程时间线">
    <div class="grid max-w-xl gap-4">
      <div class="flex items-center gap-6 border border-[var(--color-border-2)] rounded-lg p-4">
        <YdChatLoading text="正在检索并思考…" />
        <YdChatLoading text="" />
      </div>
      <YdChatProcess :activities="demoActivities" @retrieval-select="onRetrievalClick" @graph-node-select="onGraphNodeClick" />
    </div>
  </DemoCard>

  <DemoCard title="YdCitationList 引用来源" description="独立的引用来源，带序号，可折叠">
    <div class="max-w-xl border border-[var(--color-border-2)] rounded-lg p-4">
      <p class="m-0 text-sm text-[var(--color-text-2)]">
        这是一段回答正文，下方是引用来源：
      </p>
      <YdCitationList :citations="demoCitations" @select="onCitationClick" />
    </div>
  </DemoCard>

  <DemoCard title="YdChatProvider 全局配置" description="provideYdChatConfig 在应用根部统一提供占位文案、思考提示、免责声明等默认值（对齐 antd-x XProvider / tdesign ChatProvider）" :code="providerCode">
    <div class="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
      <div class="rounded-lg border border-[var(--color-border-2)] p-3">
        <p class="m-0 mb-1 flex items-center gap-1 font-medium">
          <FaIcon name="i-ri:settings-4-line" class="text-primary" /> 统一文案
        </p>
        <p class="m-0 text-xs text-[var(--color-text-3)]">placeholder / thinkingText / disclaimer 一次配置全站生效。</p>
      </div>
      <div class="rounded-lg border border-[var(--color-border-2)] p-3">
        <p class="m-0 mb-1 flex items-center gap-1 font-medium">
          <FaIcon name="i-ri:exchange-line" class="text-primary" /> 内容转换
        </p>
        <p class="m-0 text-xs text-[var(--color-text-3)]">transformContent 全局处理 wikilink 等自定义协议。</p>
      </div>
    </div>
  </DemoCard>

  <DemoCard title="useYdChatStream / useYdChat / ydRequest / readYdStream" description="AG-UI 协议适配 + 聊天数据管理 + 请求与流原语（对齐 antd-x useXAgent / useXChat / XRequest / XStream）" :code="`${aguiCode}\n\n${streamCode}`">
    <div class="grid gap-3 text-sm">
      <div class="flex flex-wrap gap-2">
        <FaTag>RUN_STARTED</FaTag>
        <FaTag>TEXT_MESSAGE_CHUNK</FaTag>
        <FaTag>THINKING_TEXT_MESSAGE_*</FaTag>
        <FaTag>TOOL_CALL_START</FaTag>
        <FaTag>TOOL_CALL_RESULT</FaTag>
        <FaTag>ACTIVITY_SNAPSHOT</FaTag>
        <FaTag>RUN_FINISHED</FaTag>
        <FaTag>RUN_ERROR</FaTag>
      </div>
      <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <div class="rounded-lg border border-[var(--color-border-2)] p-3">
          <p class="m-0 mb-1 flex items-center gap-1 font-medium">
            <FaIcon name="i-ri:rss-line" class="text-primary" /> SSE
          </p>
          <p class="m-0 text-xs text-[var(--color-text-3)]">fetch + ReadableStream 增量解析，适合单向流式问答。</p>
        </div>
        <div class="rounded-lg border border-[var(--color-border-2)] p-3">
          <p class="m-0 mb-1 flex items-center gap-1 font-medium">
            <FaIcon name="i-ri:exchange-line" class="text-primary" /> WebSocket
          </p>
          <p class="m-0 text-xs text-[var(--color-text-3)]">一问一连接，支持双向交互与结构化 AG-UI 事件。</p>
        </div>
      </div>
    </div>
  </DemoCard>
</template>

<style scoped>
.ai-demo {
  display: flex;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-large);
  background: var(--color-bg-2);
  flex-direction: column;
}

.ai-demo--bubble {
  display: grid;
  gap: 18px;
  padding: 20px;
}

.ai-demo--static {
  height: 520px;
}

.ai-demo--chat {
  height: 560px;
}

.ai-demo--sessions {
  display: block;
  height: 340px;
}

.ai-demo--welcome {
  padding: 12px 0;
}
</style>
