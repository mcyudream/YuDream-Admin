<script setup lang="ts">
import type { YdAgentContextFile, YdAgentReference, YdAgentSendRequest, YdAgentSession, YdAgentToolCall, YdChatAttachment, YdChatMessage, YdPromptItem, YdThoughtChainItem } from '@yudream/components'
import { YdAgentWorkbench, YdBubble, YdButton, YdChatLoading, YdCheckbox, YdCode, YdFloatBall, YdIcon, YdInput, YdModal, YdPrompts, YdThoughtChain } from '@yudream/components'
import { computed, ref } from 'vue'
import { useAgentWorkbenchDemo } from './useAgentWorkbenchDemo'

const demo = useAgentWorkbenchDemo()
const { sessionId, workspaceId, modelId, mode } = demo
const galleryOpen = ref(false)
const protocolOpen = ref(false)
const floatBallOpen = ref(false)
const floatBallVisible = ref(false)
const floatBallBusy = ref(false)
const floatBallBadge = ref('3')
const persistentContract = `// 宿主统一 AI 网关：会话在 platformChatSession，运行在 platformAiRun
// 业务面板（CMS 画布 / 站点工作区）不再有专用流式端点，统一走
//   POST /api/platform/chat/sessions/{id}/runs   创建可恢复运行
//   GET  /api/platform/ai/runs/{id}/events       订阅 SSE（游标续传，断线只关订阅）
//   POST /api/platform/ai/runs/{id}/frontend-tools/{toolCallId}  回传前端工具真实结果
// 业务会话 = CONSOLE 范围 + agentCode（cms-canvas 画布 / cms-builder 站点工作区）
const assistant = useAiAssistantStore()
await assistant.send(request, pageContext, { scopeType: 'CONSOLE', agentCode: 'cms-canvas' })

// 画布工具在浏览器真实执行：面板挂载期注册执行器，卸载即注销
assistant.registerFrontendToolHandler('cms.canvas.update_text', request =>
  executeCanvasTool(editor, request.name, request.arguments, execOptions))

// message-extra: 宿主业务工具结果（需求澄清卡片等）
// composer-before: 等待输入、恢复连接与业务提示
// sender-actions: 业务模型或范围选择
// 普通助手工作区：每用户默认工作区（platform:chat:workspace:view/edit），
// 助手经 workspace.list / workspace.read 只读工具读取，写入只经界面端点`
const attachments = ref<YdChatAttachment[]>([])
const renameOpen = ref(false)
const renameId = ref('')
const renameTitle = ref('')
const sourceFile = ref<YdAgentContextFile>()
const sourceOpen = ref(false)
const referenceOpen = ref(false)
const selectedReference = ref<YdAgentReference>()
const toolOpen = ref(false)
const selectedTool = ref<YdAgentToolCall>()
const toolDetails = computed(() => JSON.stringify(selectedTool.value, null, 2))
const suggestions = ['修复任务与加载组件的深色对比度', '规划 Agent 工作台的组件分层', '解释 Harness、Skills 和 MCP 如何协作']
const sourceText = computed(() => {
  const path = sourceFile.value?.path
  if (path?.endsWith('AGENTS.md')) {
    return '# YD 工作区规范（演示片段）\n\n- 通用控件使用 Yd* 公开入口\n- 主题消费共享语义变量\n- 功能动画在三套外观下持续运行\n- 先验证，再提交独立模块'
  }
  if (path?.endsWith('appearance.css')) {
    return '/* 本地演示片段，并非读取宿主文件 */\n:root {\n  --yd-emphasis: oklch(var(--primary));\n}\n\n.dark {\n  --yd-emphasis: color-mix(in oklab, oklch(var(--primary)) 35%, oklch(var(--foreground)));\n}'
  }
  if (path?.endsWith('.vue')) {
    return '<!-- 本地演示片段，并非读取宿主文件 -->\n<template>\n  <div class="loading-state" role="status">\n    <span>正在执行任务…</span>\n  </div>\n</template>\n\n<style scoped>\n.loading-state {\n  color: var(--yd-emphasis);\n  background: oklch(var(--muted) / 65%);\n}\n</style>'
  }
  return '# YD 组件接入（演示片段）\n\n统一通过 YD 组件公开属性、插槽与事件组合页面。\n工作台只接收状态投影，真实运行、权限与持久化由宿主 Harness 管理。'
})

async function send(request: YdAgentSendRequest) {
  await demo.send(request)
  attachments.value = []
}
function attach(files: File[]) {
  attachments.value.push(...files.map(file => ({ fileName: file.name, contentType: file.type, size: file.size, kind: 'FILE' })))
}

function openRename(session: YdAgentSession) {
  renameId.value = session.id
  renameTitle.value = session.title
  renameOpen.value = true
}
function saveRename() {
  demo.renameSession(renameId.value, renameTitle.value)
}
function fileSelect(file: YdAgentContextFile) {
  sourceFile.value = file
  sourceOpen.value = true
}
function referenceSelect(reference: YdAgentReference) {
  selectedReference.value = reference
  referenceOpen.value = true
}
function toolSelect(tool: YdAgentToolCall) {
  selectedTool.value = tool
  toolOpen.value = true
}

const exampleMessage: YdChatMessage = {
  role: 'assistant',
  content: '工作台负责组织任务、上下文与审批。**气泡、输入器和过程元件**仍可以独立组合，用于知识库问答或轻量助手。',
  citations: [{ title: '组件主题约定 · 示例', path: '/demo/theme' }],
}
const chain: YdThoughtChainItem[] = [
  { key: 'context', title: '收集上下文', description: '只包含用户选中的文件', status: 'success' },
  { key: 'answer', title: '生成结果', description: '展示公开过程摘要', status: 'running' },
]
const prompts: YdPromptItem[] = [
  { key: 'ask', label: '了解主题变量', description: '从问题开始', icon: 'morph:messages-square' },
  { key: 'plan', label: '规划组件结构', description: '先形成可执行计划', icon: 'morph:list-checks' },
]
</script>

<template>
  <div class="agent-demo">
    <YdAgentWorkbench
      v-model:session-id="sessionId"
      v-model:workspace-id="workspaceId"
      v-model:model-id="modelId"
      v-model:mode="mode"
      :sessions="demo.sessions.value"
      :workspaces="demo.workspaces"
      :models="demo.models"
      :messages="demo.current.value.messages"
      :render-documents="demo.current.value.renderDocuments"
      :harness="demo.current.value.harness"
      :activities="demo.current.value.activities"
      :skills="demo.skills.value"
      :mcp-servers="demo.mcpServers"
      :files="demo.files.value"
      :changes="demo.current.value.changes"
      :approvals="demo.current.value.approvals"
      :suggestions="suggestions"
      :attachments="attachments"
      attachments-enabled
      demo
      @send="send"
      @attach="attach"
      @remove-attachment="attachment => attachments = attachments.filter(item => item !== attachment)"
      @stop="demo.stop"
      @session-create="demo.createSession"
      @session-rename="openRename"
      @session-pin="demo.pinSession"
      @session-remove="demo.removeSession"
      @file-select="fileSelect"
      @file-toggle="demo.fileToggle"
      @skill-toggle="demo.skillToggle"
      @approval-decision="demo.approvalDecision"
      @reference-select="referenceSelect"
      @tool-select="toolSelect"
      @tool-retry="demo.toolRetry"
    >
      <template #header-actions>
        <YdButton variant="ghost" size="sm" @click="protocolOpen = true">
          持久会话接入
        </YdButton>
        <YdCheckbox v-model="demo.failCheck.value" class="failure-toggle" :disabled="mode !== 'code'" title="影响下一轮代码模式的本地演示">
          模拟验证失败
        </YdCheckbox>
      </template>
    </YdAgentWorkbench>

    <section class="agent-gallery">
      <YdButton variant="ghost" class="gallery-toggle" :aria-expanded="galleryOpen" aria-controls="ai-building-blocks" @click="galleryOpen = !galleryOpen">
        <YdIcon name="morph:blocks" />
        <span>基础 AI 元件</span>
        <span class="gallery-description">独立组合气泡、提示集与过程</span>
        <YdIcon :name="galleryOpen ? 'morph:chevron-up' : 'morph:chevron-down'" />
      </YdButton>
      <div v-if="galleryOpen" id="ai-building-blocks" class="gallery-content">
        <div class="gallery-message">
          <YdBubble :message="exampleMessage" show-actions />
          <YdChatLoading text="正在准备回答…" />
        </div>
        <YdThoughtChain :items="chain" title="过程摘要" />
        <YdPrompts title="预设提示" :items="prompts" @select="mode = $event.key === 'plan' ? 'plan' : 'ask'" />
      </div>
    </section>

    <section class="agent-gallery">
      <YdButton variant="ghost" class="gallery-toggle" :aria-expanded="floatBallOpen" aria-controls="ai-float-ball" @click="floatBallOpen = !floatBallOpen">
        <YdIcon name="morph:circle" />
        <span>可拖拽圆球入口</span>
        <span class="gallery-description">图标 + 角标 + 吸附边缘的悬浮入口</span>
        <YdIcon :name="floatBallOpen ? 'morph:chevron-up' : 'morph:chevron-down'" />
      </YdButton>
      <div v-if="floatBallOpen" id="ai-float-ball" class="gallery-content">
        <p class="float-ball-note">
          开启后圆球固定在视口右下角：可拖拽，松手吸附最近的左右边缘，位置按 storage-key 记忆；
          拖拽超过 4px 不触发点击，busy 时图标旋转并带脉冲圈，badge 显示待处理数量。
        </p>
        <div class="float-ball-controls">
          <YdButton variant="outline" size="sm" @click="floatBallVisible = !floatBallVisible">
            {{ floatBallVisible ? '隐藏圆球' : '显示圆球' }}
          </YdButton>
          <YdCheckbox v-model="floatBallBusy">
            运行中（旋转 + 脉冲）
          </YdCheckbox>
          <YdInput v-model="floatBallBadge" class="float-ball-badge-input" aria-label="角标文本" placeholder="角标" :maxlength="3" />
        </div>
      </div>
    </section>

    <YdFloatBall
      v-if="floatBallVisible"
      :badge="floatBallBadge"
      :busy="floatBallBusy"
      label="AI 助手演示入口"
      storage-key="yd.showcase.floatBall"
      @click="floatBallVisible = false"
    />

    <YdModal v-model="renameOpen" title="重命名会话" description="名称仅保存在当前页面的本地演示状态。" show-cancel-button :confirm-button-disabled="!renameTitle.trim()" @confirm="saveRename">
      <YdInput v-model="renameTitle" aria-label="会话名称" placeholder="输入会话名称" :maxlength="60" @keydown.enter.prevent="renameTitle.trim() && (saveRename(), renameOpen = false)" />
    </YdModal>
    <YdModal v-model="protocolOpen" title="持久会话与宿主插槽" description="接入契约示例；此预览没有连接 CMS 后端或执行工具。" confirm-button-text="关闭" class="source-modal">
      <YdCode :code="persistentContract" language="typescript" :line-numbers="false" />
    </YdModal>
    <YdModal v-model="sourceOpen" :title="sourceFile?.path || '上下文文件'" description="本地示例片段，不会读取宿主文件。" confirm-button-text="关闭" class="source-modal">
      <YdCode :code="sourceText" :language="sourceFile?.path.endsWith('.vue') ? 'xml' : sourceFile?.path.endsWith('.css') ? 'css' : 'markdown'" line-numbers />
    </YdModal>
    <YdModal v-model="referenceOpen" :title="selectedReference?.title || '引用原文'" description="回答中的本地示例来源，行号仅用于展示引用定位。" confirm-button-text="关闭" class="source-modal">
      <p class="reference-path">
        {{ selectedReference?.path }}:{{ selectedReference?.startLine }}–{{ selectedReference?.endLine }}
      </p>
      <blockquote class="reference-excerpt">
        {{ selectedReference?.excerpt }}
      </blockquote>
    </YdModal>
    <YdModal v-model="toolOpen" :title="selectedTool?.label || selectedTool?.name || '工具详情'" description="本地模拟工具调用的状态、输入与输出。" confirm-button-text="关闭" class="source-modal">
      <YdCode :code="toolDetails" language="json" />
    </YdModal>
  </div>
</template>

<style scoped>
.agent-demo {
  display: grid;
  gap: var(--yd-workspace-gap);
  min-width: 0;
}

.agent-gallery {
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius);
}

.gallery-toggle {
  justify-content: flex-start;
  width: 100%;
  min-height: 50px;
  padding: 12px 18px;
  color: var(--color-text-1);
  text-align: left;
}

.gallery-toggle :deep(svg:last-child) {
  margin-left: auto;
}

.gallery-description {
  margin-left: 10px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-3);
}

.gallery-content {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--yd-workspace-gap);
  padding: var(--yd-surface-padding);
  border-top: 1px solid var(--color-border-2);
}

.gallery-message {
  display: grid;
  gap: 18px;
}

.gallery-content > :last-child {
  grid-column: 1 / -1;
}

.source-modal {
  width: min(860px, 95vw);
}

.failure-toggle {
  font-size: 12px;
}

.reference-path {
  margin: 0 0 14px;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
}

.reference-excerpt {
  padding: 14px 18px;
  margin: 0;
  line-height: 1.8;
  color: var(--color-text-1);
  background: var(--color-fill-1);
  border-inline-start: 3px solid var(--color-border-3);
  border-radius: var(--yd-control-radius);
}

.float-ball-note {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-3);
}

.float-ball-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.float-ball-badge-input {
  width: 96px;
}

@media (width <= 768px) {
  .gallery-content {
    grid-template-columns: 1fr;
  }

  .gallery-description {
    display: none;
  }
}
</style>
