<script setup lang="ts">
import {
  YdAttachmentList,
  YdChatActions,
  YdChatGraphView,
  YdChatMessageList,
  YdChatProcess,
  YdChatSender,
  YdChatSessionList,
  YdCitationList,
  YdPrompts,
  YdSuggestion,
  YdThoughtChain,
  YdWelcome,
} from '@yudream/components'
import { computed, ref } from 'vue'

const props = defineProps<{ demo: string }>()
const notice = ref('')
const sessions = ref([
  { id: 'demo-session-1', title: '知识库使用咨询', pinned: true, scopeType: 'WIKI' },
  { id: 'demo-session-2', title: '周报撰写助手', scopeType: 'GENERAL' },
  { id: 'demo-session-3', title: '代码审查 Agent', scopeType: 'AGENT' },
])
const activeId = ref('demo-session-1')
const attachments = ref([
  { fileId: 'demo-file-1', fileName: '架构图.svg', contentType: 'image/svg+xml', kind: 'IMAGE', dataUrl: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="68" height="68"%3E%3Crect width="100%25" height="100%25" fill="%23dbeafe"/%3E%3Cpath d="M12 50 30 24l12 16 8-10 8 20z" fill="%2360a5fa"/%3E%3C/svg%3E' },
  { fileId: 'demo-file-2', fileName: '需求文档-v2.pdf', contentType: 'application/pdf', kind: 'DOCUMENT' },
])
const graph = {
  query: '部署流程',
  nodes: [
    { id: 'query', title: '部署流程', role: 'query' },
    { id: 'compose', title: 'Docker Compose', type: 'concept', score: 0.9, role: 'focus' },
    { id: 'nginx', title: 'Nginx 配置', type: 'page', score: 0.6 },
  ],
  edges: [
    { source: 'query', target: 'compose', weight: 0.9 },
    { source: 'compose', target: 'nginx', weight: 0.6, signal: '引用' },
  ],
}
const activities = [
  { messageId: 'demo-message-2', activityType: 'wiki-retrieval', status: 'complete', title: '检索知识库', hits: [
    { title: '部署指南', kind: 'page', score: 0.92, excerpt: '生产环境使用 Docker Compose 部署…' },
    { title: 'Nginx 配置', kind: 'page', score: 0.87, excerpt: '反向代理与健康检查配置。' },
  ] },
  { messageId: 'demo-message-2', activityType: 'wiki-progress', status: 'running', phase: '整理要点', content: '正在汇总检索结果…' },
]
const messages = ref([
  { id: 'demo-message-1', role: 'user', content: '知识库里关于部署的文档有哪些？' },
  { id: 'demo-message-2', role: 'assistant', reasoning: '先检索知识库，再整理相关部署文档。', activities, content: '找到两篇相关文档：\n\n- [部署指南](wiki://deploy)\n- [Nginx 配置](wiki://nginx)', citations: [{ title: '部署指南', path: '/ops/deploy', excerpt: '生产环境使用 Docker Compose 部署。' }], actions: [{ label: '打开部署指南', action: 'open', value: '/ops/deploy' }] },
])
const suggestionText = ref('/')
const suggestionItems = [
  { key: 'translate', label: '/翻译', description: '翻译选中文本', icon: 'i-ri:translate-2', value: '请将以下内容翻译成英文：' },
  { key: 'summary', label: '/总结', description: '总结当前页面', icon: 'i-ri:file-list-3-line' },
  { key: 'polish', label: '/润色', description: '优化表达', icon: 'i-ri:quill-pen-line' },
]
const thoughtItems = computed(() => props.demo === 'thought-error'
  ? [
      { key: 'read', title: '读取页面配置', description: '本地 mock 成功', status: 'success' as const },
      { key: 'publish', title: '发布页面', description: '本地 mock：上游服务返回 500', status: 'error' as const },
      { key: 'notify', title: '通知订阅者', status: 'pending' as const },
    ]
  : [
      { key: 'retrieve', title: '检索知识库', description: '命中 3 篇文档', status: 'success' as const },
      { key: 'plan', title: '生成执行计划', status: 'running' as const },
      { key: 'render', title: '渲染结果', status: 'pending' as const },
    ])

function removeAttachment(attachment: { fileId?: string }) {
  attachments.value = attachments.value.filter(item => item.fileId !== attachment.fileId)
  notice.value = '已从本地 mock 列表移除附件'
}
function togglePin(session: { id: string }) {
  const target = sessions.value.find(item => item.id === session.id)
  if (target) target.pinned = !target.pinned
}
function removeSession(session: { id: string }) {
  sessions.value = sessions.value.filter(item => item.id !== session.id)
}
function createSession() {
  const id = `demo-session-${Date.now()}`
  sessions.value.push({ id, title: '本地新建会话', scopeType: 'GENERAL' })
  activeId.value = id
}
function send(text: string) {
  messages.value.push({ id: `demo-message-${Date.now()}`, role: 'user', content: text })
  notice.value = `已本地发送：${text}`
}
</script>

<template>
  <div class="interactive-remaining-ai">
    <YdAttachmentList v-if="demo === 'attachment'" :attachments="attachments" removable @remove="removeAttachment" />
    <YdChatActions v-else-if="demo === 'actions'" copy-text="这是本地 mock 的回答内容。" regenerable feedback :items="[{ key: 'translate', label: '翻译', icon: 'i-ri:translate-2' }]" @copy="notice = '已触发 copy 事件'" @regenerate="notice = '已触发 regenerate 事件'" @feedback="value => notice = `反馈：${value ?? '取消'}`" @action="item => notice = `操作：${item.label}`" />
    <YdChatGraphView v-else-if="demo === 'graph' || demo === 'graph-compact'" :graph="graph" :compact="demo === 'graph-compact'" @node-select="node => notice = `选中节点：${node.title}`" />
    <YdChatMessageList v-else-if="demo === 'message-list'" class="interactive-remaining-ai__messages" :messages="messages" @action-click="action => notice = `动作：${action.label}`" @citation-click="citation => notice = `引用：${citation.title}`" @copy-message="message => notice = `复制：${message.id}`" @regenerate-message="message => notice = `重新生成：${message.id}`" />
    <YdChatProcess v-else-if="demo === 'process'" :activities="activities" @retrieval-select="hit => notice = `命中：${hit.title}`" />
    <YdChatProcess v-else-if="demo === 'process-graph'" :activities="[{ messageId: 'graph', activityType: 'wiki-graph', status: 'complete', title: '分析关联图谱', graph }]" compact @graph-node-select="node => notice = `选中节点：${node.title}`" />
    <YdChatSender v-else-if="demo === 'sender' || demo === 'sender-mention'" :suggestions="['帮我写周报', '总结这篇文档']" :mention-items="demo === 'sender-mention' ? [{ key: 'wiki', label: '知识库', value: 'wiki', icon: 'i-ri:book-2-line' }, { key: 'agent', label: '代码审查 Agent', value: 'agent', icon: 'i-ri:robot-2-line' }] : []" @send="send" @suggestion-click="send" @mention-select="item => notice = `选择上下文：${item.label}`" />
    <YdChatSessionList v-else-if="demo === 'session-list'" :sessions="sessions" :active-id="activeId" @select="activeId = $event" @create="createSession" @rename="session => notice = `重命名：${session.title}`" @pin="togglePin" @remove="removeSession" />
    <YdCitationList v-else-if="demo === 'citation'" :citations="[{ title: '插件系统概述', path: '/docs/plugin-system/overview', excerpt: '插件是宿主应用的可热插拔扩展单元。' }, { title: 'SPI 端口设计', nodeId: 'demo-node-1', excerpt: '插件调用宿主能力只能走 SPI 端口。' }]" @select="citation => notice = `引用：${citation.title}`" />
    <YdPrompts v-else-if="demo === 'prompts'" title="你可以试试" :items="[{ key: 'weekly', label: '帮我写周报', description: '根据本周提交记录', icon: 'i-ri:file-list-3-line' }, { key: 'translate', label: '翻译一段文字', icon: 'i-ri:translate-2' }]" @select="item => notice = `选择：${item.label}`" />
    <div v-else-if="demo === 'suggestion'" class="interactive-remaining-ai__suggestion"><input v-model="suggestionText" aria-label="建议触发文本" placeholder="输入 / 过滤" /><YdSuggestion :text="suggestionText" :items="suggestionItems" @select="(item, query) => { suggestionText = item.value ?? item.label; notice = `选择 ${item.label}，过滤词：${query}` }" /></div>
    <YdThoughtChain v-else-if="demo === 'thought' || demo === 'thought-error'" :items="thoughtItems" />
    <YdWelcome v-else-if="demo === 'welcome'" title="下午好，Husky" description="这是完全本地的交互演示。" :suggestions="['帮我写一篇产品周报', '总结这份需求文档的要点', '把这段话翻译成英文']" @select="text => notice = `选择：${text}`" />
    <p v-if="notice" class="interactive-remaining-ai__notice">{{ notice }}</p>
  </div>
</template>

<style scoped>
.interactive-remaining-ai { display: grid; max-width: 680px; gap: 12px; }
.interactive-remaining-ai__messages { height: 420px; border: 1px solid var(--vp-c-divider); border-radius: 10px; }
.interactive-remaining-ai__suggestion { position: relative; display: grid; max-width: 420px; gap: 8px; }
.interactive-remaining-ai__suggestion input { padding: 8px 12px; border: 1px solid var(--vp-c-divider); border-radius: 8px; background: var(--vp-c-bg); color: var(--vp-c-text-1); }
.interactive-remaining-ai__notice { margin: 0; color: var(--vp-c-text-2); font-size: 12px; }
</style>
