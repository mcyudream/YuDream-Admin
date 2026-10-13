import type { YdAgentActivity, YdAgentApproval, YdAgentChange, YdAgentContextFile, YdAgentHarnessState, YdAgentMcpServer, YdAgentMode, YdAgentModel, YdAgentReference, YdAgentRenderDocument, YdAgentSendRequest, YdAgentSession, YdAgentSkill, YdAgentToolCall, YdAgentWorkspace, YdChatMessage } from '@yudream/components'
import { computed, onScopeDispose, reactive, ref } from 'vue'

interface DemoRecord {
  messages: YdChatMessage[]
  harness: YdAgentHarnessState
  activities: YdAgentActivity[]
  approvals: YdAgentApproval[]
  changes: YdAgentChange[]
  renderDocuments: Record<string, YdAgentRenderDocument>
  lastRequest?: YdAgentSendRequest
  mode: YdAgentMode
  workspaceId: string
  modelId: string
}

interface DemoRun {
  id: string
  sessionId: string
  record: DemoRecord
  controller: AbortController
  started: number
  answer: YdChatMessage
  tools: YdAgentToolCall[]
  referenceReady: boolean
  failCheck: boolean
}

const themeReference: YdAgentReference = {
  id: 'theme',
  title: '共享主题与动效约定 · 演示原文',
  path: 'packages/components/src/theme/appearance.css',
  startLine: 12,
  endLine: 17,
  excerpt: 'YD 表面与动效统一消费共享主题变量。三套外观保留功能动画；深色模式的强调色混合主题主色与语义前景，确保加载图形和状态可读。',
}

const changes: YdAgentChange[] = [{
  id: 'loading-style',
  path: 'packages/components/src/extended/YdLoading.vue',
  status: 'modified',
  additions: 2,
  deletions: 2,
  diff: `--- a/packages/components/src/extended/YdLoading.vue
+++ b/packages/components/src/extended/YdLoading.vue
@@ -42,2 +42,2 @@
-  color: oklch(var(--primary));
-  opacity: 0.45;
+  color: var(--yd-emphasis);
+  opacity: 0.9;`,
}, {
  id: 'task-style',
  path: 'packages/components/src/extended/YdMultiStepLoader.vue',
  status: 'modified',
  additions: 2,
  deletions: 2,
  diff: `--- a/packages/components/src/extended/YdMultiStepLoader.vue
+++ b/packages/components/src/extended/YdMultiStepLoader.vue
@@ -96,2 +96,2 @@
-  color: oklch(var(--primary));
-  background: oklch(var(--primary) / 8%);
+  color: var(--yd-emphasis);
+  background: oklch(var(--muted) / 65%);`,
}]

/** Preview state only: no requests, shell execution, filesystem writes or MCP connections. */
export function useAgentWorkbenchDemo(options: { stepMs?: number, chunkMs?: number } = {}) {
  const stepMs = options.stepMs ?? 520
  const chunkMs = options.chunkMs ?? 28
  const failCheck = ref(false)
  const selectedId = ref('theme-fix')
  const sessions = ref<YdAgentSession[]>([
    { id: 'theme-fix', title: '修复加载组件的深色主题', pinned: true, workspaceId: 'yd-worktree', status: 'idle' },
    { id: 'component-plan', title: '规划 Agent 组件分层', workspaceId: 'docs-local', status: 'completed' },
  ])
  const workspaces: YdAgentWorkspace[] = [
    { id: 'yd-worktree', name: 'YD 组件库', path: 'D:/demo/yudream-frontend', branch: 'codex/agent-workbench', kind: 'worktree', access: 'write', description: '演示可写工作树 · 所有变更只存在于本页' },
    { id: 'docs-local', name: '设计规范', path: 'D:/demo/design-guidelines', branch: 'main', kind: 'local', access: 'read', description: '演示只读目录 · 可提问和规划' },
  ]
  const models: YdAgentModel[] = [
    { id: 'demo-coder', label: 'Coding 模型 · 示例', providerCode: 'demo-provider', modelCode: 'demo-coding-model' },
    { id: 'demo-reasoner', label: '推理模型 · 示例', providerCode: 'demo-provider', modelCode: 'demo-reasoning-model' },
  ]
  const skills = ref<YdAgentSkill[]>([
    { code: 'yd-theme', name: 'YD 主题规范', description: '复用 FA 主题变量，检查三套外观与深浅模式。', source: 'workspace', enabled: true },
    { code: 'vue-components', name: 'Vue 组件约定', description: '检查受控数据、公开事件和组件组合。', source: 'workspace', enabled: true },
    { code: 'a11y-review', name: '交互与可访问性', description: '检查键盘操作、状态提示与可读性。', source: 'personal', enabled: false },
  ])
  const mcpServers: YdAgentMcpServer[] = [
    { code: 'theme-docs', name: '组件文档', status: 'connected', transport: '本地演示', description: '模拟只读文档检索，并未建立真实连接。', tools: ['docs.search', 'docs.read'] },
    { code: 'design-library', name: '设计素材库', status: 'disconnected', transport: '示例 HTTP', description: '未连接 · 当前任务不依赖此服务。', tools: ['assets.list'] },
  ]
  const files = ref<YdAgentContextFile[]>([
    { path: 'AGENTS.md', description: '工程约定', included: true },
    { path: 'packages/components/src/theme/appearance.css', description: '三套外观与动效变量', included: true },
    { path: 'packages/components/src/extended/YdLoading.vue', description: '加载图形', included: true },
    { path: 'packages/components/src/extended/YdMultiStepLoader.vue', description: '任务步骤与状态', included: true },
    { path: 'docs/yd-components.md', description: '组件接入说明', included: false },
  ])
  const records = reactive<Record<string, DemoRecord>>({
    'theme-fix': newRecord('code', 'yd-worktree'),
    'component-plan': {
      ...newRecord('plan', 'docs-local'),
      messages: [{ id: 'plan-intro', role: 'assistant', content: '这个会话用于规划组件边界。可以先整理 **Harness、Skills、MCP 和工作区** 的协作方式，再决定要实现的模块。\n\n计划模式仅输出方案，演示中不会产生文件变更。' }],
      renderDocuments: {
        'plan-intro': { version: 1, blocks: [
          { type: 'markdown', text: '工作台可以分为 **会话、运行、上下文与变更** 四个区域。主题与动效沿用共享规范 [[ref:theme]]。\n\nHarness 提供状态投影；技能描述约定；MCP 工具返回可检查的过程结果。以下是本地示例，不包含真实工具执行。' },
          { type: 'reference', reference: themeReference },
          { type: 'tool', tool: { id: 'intro-docs', name: 'docs.read', label: '读取主题规范', status: 'success', description: '本地示例', input: 'appearance.css', output: '已获得共享主题与动效约定。', durationMs: 280 } },
          { type: 'tool', tool: { id: 'intro-check', name: 'preview.check', label: '检查组件示例', status: 'queued', description: '计划中的步骤，尚未运行。' } },
        ] },
      },
    },
  })
  let activeRun: DemoRun | undefined
  let serial = 0

  function newRecord(mode: YdAgentMode, workspaceId: string): DemoRecord {
    return {
      messages: [],
      harness: { name: 'YuDream Harness', status: 'idle', phase: '准备就绪', contextTokens: 6420, contextLimit: 128000, checkpoint: '等待开始', memory: '当前会话 · 工作区规范' },
      activities: [],
      approvals: [],
      changes: [],
      renderDocuments: {},
      mode,
      workspaceId,
      modelId: 'demo-coder',
    }
  }

  const current = computed(() => records[selectedId.value])
  const sessionId = computed({ get: () => selectedId.value, set: (value: string | undefined) => {
    if (value && records[value] && value !== selectedId.value) {
      stop()
      selectedId.value = value
    }
  } })
  const workspaceId = computed({ get: () => current.value.workspaceId, set: (value: string | undefined) => {
    if (value && workspaces.some(workspace => workspace.id === value) && value !== current.value.workspaceId) {
      stop()
      current.value.workspaceId = value
      const session = sessions.value.find(item => item.id === selectedId.value)
      if (session) {
        session.workspaceId = value
      }
    }
  } })
  const modelId = computed({ get: () => current.value.modelId, set: (value: string | undefined) => {
    if (value && models.some(model => model.id === value) && value !== current.value.modelId) {
      stop()
      current.value.modelId = value
    }
  } })
  const mode = computed({ get: () => current.value.mode, set: (value: YdAgentMode) => {
    if (value !== current.value.mode) {
      stop()
      current.value.mode = value
    }
  } })

  function live(run: DemoRun) {
    return activeRun === run && !run.controller.signal.aborted
  }

  function sleep(run: DemoRun, ms: number) {
    return new Promise<boolean>((resolve) => {
      if (!live(run)) {
        resolve(false)
        return
      }
      const signal = run.controller.signal
      let timer: ReturnType<typeof setTimeout> | undefined
      const abort = () => {
        clearTimeout(timer)
        resolve(false)
      }
      timer = setTimeout(() => {
        signal.removeEventListener('abort', abort)
        resolve(live(run))
      }, ms)
      signal.addEventListener('abort', abort, { once: true })
    })
  }

  function syncSession(run: DemoRun) {
    const session = sessions.value.find(item => item.id === run.sessionId)
    if (session) {
      session.status = run.record.harness.status
      session.messageCount = run.record.messages.length
      session.updatedAt = new Date().toISOString()
    }
    run.record.harness.elapsedMs = Date.now() - run.started
    run.record.renderDocuments[run.answer.id!] = {
      version: 1,
      blocks: [
        { type: 'markdown', text: run.answer.content },
        ...(run.referenceReady ? [{ type: 'reference' as const, reference: themeReference }] : []),
        ...run.tools.map(tool => ({ type: 'tool' as const, tool })),
      ],
    }
  }

  async function stage(run: DemoRun, activity: Omit<YdAgentActivity, 'id' | 'status'>, tool?: YdAgentToolCall) {
    if (!live(run)) {
      return false
    }
    const item = reactive<YdAgentActivity>({ ...activity, id: `${run.id}-${run.record.activities.length}`, status: 'running' })
    run.record.activities.push(item)
    run.record.harness.phase = activity.title
    if (tool) {
      tool.status = 'running'
    }
    syncSession(run)
    if (!await sleep(run, stepMs)) {
      return false
    }
    item.status = 'complete'
    item.durationMs = stepMs
    if (tool) {
      tool.status = 'success'
      tool.output = activity.output
      tool.durationMs = stepMs
    }
    syncSession(run)
    return true
  }

  async function stream(run: DemoRun, content: string) {
    run.record.harness.status = 'running'
    run.record.harness.phase = '整理结果'
    for (let length = 0; length < content.length; length += 12) {
      if (!await sleep(run, chunkMs)) {
        return
      }
      run.answer.content = content.slice(0, length + 12)
      syncSession(run)
    }
    if (!live(run)) {
      return
    }
    run.answer.pending = false
    run.record.harness.status = 'completed'
    run.record.harness.phase = '本轮完成'
    run.record.harness.checkpoint = '本地演示结果已保存到当前会话'
    syncSession(run)
    activeRun = undefined
  }

  function stop(reason = '已停止本地演示。后续步骤不会继续执行。') {
    const run = activeRun
    if (!run) {
      return
    }
    activeRun = undefined
    run.controller.abort()
    run.answer.pending = false
    run.answer.content = `${run.answer.content}${run.answer.content ? '\n\n' : ''}${reason}`
    run.record.approvals = []
    run.tools.forEach((tool) => {
      if (tool.status === 'running' || tool.status === 'queued' || tool.status === 'awaiting-approval') {
        tool.status = 'cancelled'
        tool.description = reason
      }
    })
    run.record.activities.forEach((activity) => {
      if (activity.status === 'running' || activity.status === 'waiting') {
        activity.status = 'error'
        activity.detail = reason
      }
    })
    run.record.harness.status = 'cancelled'
    run.record.harness.phase = '已取消'
    syncSession(run)
  }

  async function send(request: YdAgentSendRequest) {
    const text = request.text.trim()
    if (!text || activeRun || (request.sessionId && request.sessionId !== selectedId.value)) {
      return
    }
    const record = current.value
    record.lastRequest = { ...request, contextPaths: [...request.contextPaths], skillCodes: [...request.skillCodes], attachments: [...request.attachments] }
    const runId = `demo-${++serial}`
    record.messages.push({ id: `${runId}-user`, role: 'user', content: text, attachments: request.attachments })
    record.messages.push({ id: `${runId}-answer`, role: 'assistant', content: '', pending: true })
    const answer = record.messages[record.messages.length - 1]
    record.activities = []
    record.approvals = []
    record.changes = []
    record.harness = { ...record.harness, runId, status: 'running', phase: '读取上下文', elapsedMs: 0, checkpoint: '本轮开始' }
    const tools = reactive<YdAgentToolCall[]>([
      { id: `${runId}-docs`, name: 'docs.read', label: '读取组件主题约定', status: 'queued', description: '只读 MCP 工具 · 本地模拟', input: 'packages/components/src/theme/appearance.css' },
      ...(request.mode === 'code'
        ? [
            { id: `${runId}-edit`, name: 'workspace.apply_patch', label: '生成组件补丁', status: 'queued' as const, description: '修改本页的 2 个示例快照', input: changes.map(item => item.path).join('\n') },
            { id: `${runId}-check`, name: 'preview.check', label: '验证主题与交互', status: 'queued' as const, description: '本地模拟验证，不执行真实命令', input: '三套外观、深浅模式与受控交互' },
          ]
        : []),
    ])
    const run: DemoRun = { id: runId, sessionId: selectedId.value, record, answer, controller: new AbortController(), started: Date.now(), tools, referenceReady: false, failCheck: failCheck.value }
    activeRun = run
    const workspace = workspaces.find(item => item.id === record.workspaceId)
    syncSession(run)
    if (!workspace || workspace.access === 'unavailable' || (request.mode === 'code' && workspace.access !== 'write')) {
      run.tools.forEach((tool) => {
        tool.status = 'cancelled'
        tool.description = '工作区访问限制，本轮未执行工具。'
      })
      await stream(run, workspace?.access === 'read'
        ? '当前工作区为 **只读**，代码模式需要可写工作区。\n\n可以选择“YD 组件库”工作树，或切换到 **提问 / 规划** 模式。本轮没有生成文件变更。'
        : '当前工作区 **不可用**，无法访问上下文或生成变更。请选择可用的演示工作区后重试。本轮没有执行工具。')
      return
    }
    if (!await stage(run, { kind: 'context', title: '读取工作区上下文', detail: `${request.contextPaths.length} 个已选文件 · ${workspace.name}`, output: request.contextPaths.join('\n') || '未选择文件，使用当前问题作为上下文。' })) {
      return
    }
    const enabledSkills = skills.value.filter(item => request.skillCodes.includes(item.code))
    if (enabledSkills.length && !await stage(run, { kind: 'skill', title: '应用技能约定', detail: enabledSkills.map(item => item.name).join('、'), output: '示例约定：消费共享主题变量；功能动效在三套外观下保持运行；使用 YD 公开入口。' })) {
      return
    }
    if (!await stage(run, { kind: 'mcp', title: '检索组件文档', detail: '组件文档 · docs.read · 只读模拟', input: '查询加载组件的主题与动效约定', output: '文档片段：默认主色为黑色，深色模式应消费可读的语义前景；减少动效只柔化节奏。' }, tools[0])) {
      return
    }
    run.referenceReady = true
    if (request.mode === 'ask') {
      await stream(run, '深色模式的问题来自 **主色与背景对比不足**。\n\n建议加载图形使用组件层的 `--yd-emphasis`，文字和表面分别消费语义前景、背景变量。三套外观共享同一主题引擎，功能动画持续表达加载状态 [[ref:theme]]。\n\n这一轮是提问模式，只分析已选上下文，没有文件变更。')
      return
    }
    if (request.mode === 'plan') {
      await stream(run, '建议按以下顺序推进：\n\n1. 对照三套外观，检查加载图形、步骤文字与进度条。\n2. 把组件强调色统一接入 `--yd-emphasis`，保留语义背景变量 [[ref:theme]]。\n3. 在深色与浅色模式验证可读性，确认减少动效不会冻结加载。\n4. 运行组件类型检查与预览构建，复核受控事件。\n\n这是规划结果，尚未产生代码变更。切换代码模式后可以体验修改审批与 diff。')
      return
    }
    run.answer.content = '已完成上下文和规范检查。准备修改 **2 个组件文件**，请在审批卡片确认本地演示变更。'
    run.record.harness.status = 'waiting'
    run.record.harness.phase = '等待修改审批'
    tools[1].status = 'awaiting-approval'
    run.record.activities.push({ id: `${run.id}-approval`, kind: 'edit', title: '修改加载与任务组件', status: 'waiting', detail: '等待批准 · 仅修改本页示例快照' })
    run.record.approvals = [{ id: `${run.id}-write`, title: '允许生成这 2 个文件的演示变更？', description: '调整加载图形和步骤状态的深色对比度。此操作仅生成本页 diff，不会读写宿主文件。', scope: changes.map(item => item.path).join('\n') }]
    syncSession(run)
  }

  async function approvalDecision(id: string, approved: boolean) {
    const run = activeRun
    if (!run || !live(run) || run.record.harness.status !== 'waiting' || !run.record.approvals.some(item => item.id === id)) {
      return
    }
    if (!approved) {
      stop('已拒绝修改审批。没有生成文件变更，也不会继续执行验证。')
      return
    }
    run.record.approvals = []
    run.record.harness.status = 'running'
    const approval = run.record.activities.find(item => item.status === 'waiting')
    if (approval) {
      approval.status = 'complete'
      approval.detail = '已批准本地演示变更'
    }
    run.answer.content = ''
    if (!await stage(run, { kind: 'edit', title: '生成组件变更', detail: '2 个文件 · +4 / −4 · 本地示例快照', output: '加载图形接入 --yd-emphasis；任务表面使用中性背景。' }, run.tools[1])) {
      return
    }
    run.record.changes = changes.map(item => ({ ...item }))
    if (!await stage(run, { kind: 'test', title: '验证主题与受控交互', detail: '模拟检查结果 · 并未执行真实构建命令', input: '演示：typecheck、主题切换、加载动画', output: '示例检查通过：三套外观、深浅模式、审批状态、取消流程。' }, run.tools[2])) {
      return
    }
    if (run.failCheck) {
      run.tools[2].status = 'error'
      run.tools[2].error = '演示错误：检测到步骤文字对比度不足。可重试本地演示。'
      run.tools[2].output = '模拟验证失败'
      run.tools[2].retry = true
      const activity = run.record.activities[run.record.activities.length - 1]
      activity.status = 'error'
      activity.output = run.tools[2].error
      run.answer.pending = false
      run.answer.content = '已生成示例补丁，但 **本地模拟验证未通过**。可以查看工具详情，或点击“重试”开启新的一轮演示。\n\n此错误仅用于演示交互，不代表真实组件检查失败。'
      run.record.harness.status = 'error'
      run.record.harness.phase = '演示验证失败'
      syncSession(run)
      activeRun = undefined
      return
    }
    if (!await stage(run, { kind: 'checkpoint', title: '记录演示检查点', detail: '保留当前会话的变更与结果', output: '检查点 demo/theme-contrast · 可从右侧变更查看 diff。' })) {
      return
    }
    await stream(run, '已生成 **2 个组件文件的演示变更**：\n\n- 加载图形改用可读的语义强调色。\n- 任务步骤改用中性表面与清晰文字。\n- 三套外观仍保留功能动画，减少动效仅柔化动作 [[ref:theme]]。\n\n右侧可检查 unified diff 与模拟验证记录。**所有结果仅存在于这个预览页面，没有执行构建、连接 MCP 或修改真实文件。**')
  }

  async function toolRetry(tool: YdAgentToolCall) {
    const request = current.value.lastRequest
    if (!tool.retry || activeRun || !request || !current.value.renderDocuments[current.value.messages.at(-1)?.id || '']?.blocks.some(block => block.type === 'tool' && block.tool.id === tool.id)) {
      return
    }
    failCheck.value = false
    await send({ ...request, text: '重试上轮深色主题修复演示（验证失败开关已关闭）', sessionId: selectedId.value, workspaceId: workspaceId.value, mode: mode.value, skillCodes: skills.value.filter(item => item.enabled).map(item => item.code), contextPaths: files.value.filter(item => item.included).map(item => item.path) })
  }

  function createSession() {
    stop()
    const id = `session-${++serial}`
    records[id] = newRecord(mode.value, workspaceId.value)
    sessions.value.unshift({ id, title: '新的组件任务', workspaceId: workspaceId.value, status: 'idle' })
    selectedId.value = id
  }

  function renameSession(id: string, title: string) {
    const session = sessions.value.find(item => item.id === id)
    if (session && title.trim()) {
      session.title = title.trim()
    }
  }

  function pinSession(session: YdAgentSession) {
    const item = sessions.value.find(candidate => candidate.id === session.id)
    if (item) {
      item.pinned = !item.pinned
    }
  }

  function removeSession(session: YdAgentSession) {
    if (!records[session.id]) {
      return
    }
    if (session.id === selectedId.value) {
      stop()
      const next = sessions.value.find(item => item.id !== session.id)
      if (next) {
        selectedId.value = next.id
      }
      else {
        createSession()
      }
    }
    sessions.value = sessions.value.filter(item => item.id !== session.id)
    delete records[session.id]
  }

  function fileToggle(path: string, included: boolean) {
    const file = files.value.find(item => item.path === path)
    if (file) {
      file.included = included
    }
  }

  function skillToggle(code: string, enabled: boolean) {
    const skill = skills.value.find(item => item.code === code)
    if (skill) {
      skill.enabled = enabled
    }
  }

  onScopeDispose(() => stop())

  return { sessions, sessionId, workspaces, workspaceId, models, modelId, mode, skills, mcpServers, files, current, failCheck, send, stop: () => stop(), approvalDecision, toolRetry, createSession, renameSession, pinSession, removeSession, fileToggle, skillToggle }
}
