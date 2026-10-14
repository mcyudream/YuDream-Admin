# YD Agent 工作台组件

本地专用预览入口为 `http://127.0.0.1:5188/?category=ai`。这个入口在标签页内管理组件视图，其他预览切换分类不会打断当前 AI 演示。

YD Agent 工作台把会话、任务对话、执行轨迹、工作区上下文和变更审阅组合成可复用的 Coding Agent 界面。组件通过属性接收运行状态，通过事件向宿主提交操作意图；它不负责启动模型、连接 MCP、执行命令或写入仓库。

生产聊天页和全局助手复用此工作台，由根级 Pinia store 消费后端持久运行快照与增量事件。AgentScope 原生 AgentSession 管理后台执行与持久上下文，关闭窗口或切换页面只改变观察界面。`component-showcase` 中的任务、文件片段、MCP 状态、工具结果和审批仍为明确标记的本地演示数据；MCP、Git 与文件操作只展示宿主实际提供的能力。

## 公开入口与源码

组件及类型可从 `@yudream/components` 或 `@yudream/components/ai` 导入。基础 AI 元件继续保留，可用于知识库问答、轻量聊天或自定义布局。

```ts
import type {
  YdAgentHarnessState,
  YdAgentRenderDocument,
  YdAgentSendRequest,
  YdAgentWorkspace,
  YdChatMessage,
} from '@yudream/components'
import {
  YdAgentAnswer,
  YdAgentContextPanel,
  YdAgentWorkbench,
} from '@yudream/components'
```

| 入口 | 职责 | 源码 |
| --- | --- | --- |
| `YdAgentWorkbench` | 会话、对话、输入、轨迹、上下文侧栏、审批与变更抽屉 | [YdAgentWorkbench.vue](../yudream-frontend/packages/components/src/ai/YdAgentWorkbench.vue) |
| `YdAgentContextPanel` | 工作区信息、文件、Skills、MCP 与 Harness 状态投影 | [YdAgentContextPanel.vue](../yudream-frontend/packages/components/src/ai/YdAgentContextPanel.vue) |
| `YdAgentAnswer` | Markdown、引用和工具调用的顺序渲染 | [YdAgentAnswer.vue](../yudream-frontend/packages/components/src/ai/YdAgentAnswer.vue) |
| 工作台类型 | 会话、工作区、模型、Harness、能力与操作请求 | [agent-workbench.ts](../yudream-frontend/packages/components/src/ai/agent-workbench.ts) |
| 回答协议类型 | `version: 1` 文档、引用、工具调用与渲染辅助函数 | [agent-render.ts](../yudream-frontend/packages/components/src/ai/agent-render.ts) |
| 既有消息类型 | `YdChatMessage`、附件、引用与聊天流契约 | [useYdChatStream.ts](../yudream-frontend/packages/components/src/ai/useYdChatStream.ts) |

包根入口通过 [src/index.ts](../yudream-frontend/packages/components/src/index.ts) 导出 AI 模块；[ai/index.ts](../yudream-frontend/packages/components/src/ai/index.ts) 是 AI 子入口。插件使用宿主注入的 SDK 组件桥接，避免在远程产物中另外打包宿主组件库。

## YdAgentWorkbench

工作台保留轻量的本地界面状态，例如折叠侧栏、抽屉、每个会话的输入草稿和当前展开的变更。业务数据与运行结果均由宿主控制，事件不会自动修改传入对象或持久化会话。

### 属性

`harness` 必填，数组默认空数组，`renderDocuments` 默认空对象。

| 属性 | 类型 / 默认值 | 用途 |
| --- | --- | --- |
| `sessions`、`sessionId` | `YdAgentSession[]`、`string?` | 可选会话与当前会话 |
| `workspaces`、`workspaceId` | `YdAgentWorkspace[]`、`string?` | 可选工作区与当前工作区 |
| `models`、`modelId` | `YdAgentModel[]`、`string?` | 供应商与模型选择；请求包含所选模型的 `providerCode` / `modelCode` |
| `mode` | `'ask' \| 'plan' \| 'code'`，默认 `ask` | 问答、规划、编码 |
| `availableModes` | `YdAgentMode[]`，默认三种模式 | 宿主明确可用模式；普通聊天传 `['ask']`，不会凭 UI 取得编码权限 |
| `messages` | `YdChatMessage[]` | 消息与兼容的既有聊天内容 |
| `renderDocuments` | `Record<string, YdAgentRenderDocument>` | 以消息 `id` 关联回答渲染文档 |
| `harness` | `YdAgentHarnessState` | 真实运行状态、上下文用量、阶段和检查点 |
| `activities` | `YdAgentActivity[]` | 宿主提供的公开执行轨迹 |
| `skills` | `YdAgentSkill[]` | 可选技能及当前候选启用状态 |
| `mcpServers` | `YdAgentMcpServer[]` | 宿主报告的服务状态和工具清单 |
| `files` | `YdAgentContextFile[]` | 上下文候选文件和纳入状态 |
| `changes` | `YdAgentChange[]` | 宿主提供的文件变更、增删统计和 diff 文本 |
| `approvals` | `YdAgentApproval[]` | 待批准操作的标题、描述、命令与范围 |
| `suggestions` | `string[]` | 空会话的起始提示，点击后填入草稿 |
| `disabled` | `boolean`，默认 `false` | 禁止发送及变更上下文等操作 |
| `attachmentsEnabled` | `boolean`，默认 `false` | 启用附件选择入口；上传仍交给宿主 |
| `attachments` | `YdChatAttachment[]`，默认空 | 已上传附件的受控列表；发送携带此列表，移除仍由宿主处理 |
| `demo` | `boolean`，默认 `false` | 显示本地演示标识，不会自动产生示例状态 |

### 事件

| 事件 | 参数 | 宿主处理 |
| --- | --- | --- |
| `update:sessionId` | `id: string` | 切换会话 |
| `update:workspaceId` | `id: string` | 切换工作区并刷新上下文 |
| `update:modelId` | `id: string` | 选择模型 |
| `update:mode` | `mode: YdAgentMode` | 选择问答、规划或编码 |
| `send` | `request: YdAgentSendRequest` | 校验权限后提交任务 |
| `stop` | 无 | 取消当前运行并更新状态 |
| `attach` | `files: File[]` | 执行上传或解析流程 |
| `removeAttachment` | `attachment: YdChatAttachment` | 更新宿主附件列表，不自行删除服务端文件 |
| `sessionCreate` | 无 | 创建会话并提供新 ID |
| `sessionRename`、`sessionPin`、`sessionRemove` | `session: YdAgentSession` | 变更会话元数据或删除会话 |
| `fileSelect` | `file: YdAgentContextFile` | 打开或定位文件，按宿主权限读取内容 |
| `fileToggle` | `path: string, included: boolean` | 更新候选上下文文件 |
| `skillToggle` | `code: string, enabled: boolean` | 更新候选技能选择 |
| `approvalDecision` | `id: string, approved: boolean` | 核销审批并恢复或拒绝对应操作 |
| `referenceSelect` | `reference: YdAgentReference` | 展示引用原文或定位工作区文件 |
| `toolSelect` | `tool: YdAgentToolCall` | 查看工具详情；不是执行事件 |
| `toolRetry` | `tool: YdAgentToolCall` | 由宿主判断并重新执行工具 |
| `citationSelect` | `citation: YdChatCitation` | 处理既有消息引用 |

`YdAgentSendRequest` 包含 `text`、`attachments`、当前 `sessionId` / `workspaceId`、所选 `model`、`mode`、候选 `skillCodes` 与 `contextPaths`。所有 ID 保持字符串。发送请求不代表已取得工作区、工具或技能权限，宿主必须以当前用户和运行状态重新校验。

`harness.status` 为 `running` 或 `waiting` 时，工作台显示忙碌状态，禁止重复发送以及切换工作区、模式、模型和上下文。不可用工作区禁止发送；编码模式要求可写工作区。这些是界面约束，服务端仍需独立检查权限。

### 插槽和实例方法

| 名称 | 参数 / 用途 |
| --- | --- |
| `header-actions` | 顶部扩展操作 |
| `empty` | 空会话的扩展内容 |
| `message-actions` | `{ message: YdChatMessage }`，回答操作区 |
| `changes-footer` | `{ changes: YdAgentChange[] }`，变更抽屉底部 |
| `scrollToBottom(force?: boolean)` | 实例方法；默认尊重用户向上阅读的位置，传 `true` 可强制滚到底部 |

工作台按自身可用宽度收起上下文侧栏和会话列表，通过抽屉保留访问入口。视觉使用共享 FA 主题及 `--yd-*` 令牌，支持灵动、雅致、紧凑和深浅模式；运行中图标在各外观下持续动画，减少动态效果只柔化节奏。

## YdAgentContextPanel

该面板可独立用于编辑器侧栏或详情抽屉，根节点不额外添加卡片圆角。它展示本地、Worktree 或远程工作区，以及分支、读写权限、候选文件、Skills、MCP 服务和 Harness 状态。

| 属性 | 类型 / 默认值 |
| --- | --- |
| `workspace` | `YdAgentWorkspace?` |
| `files` | `YdAgentContextFile[]`，默认 `[]` |
| `skills` | `YdAgentSkill[]`，默认 `[]` |
| `mcpServers` | `YdAgentMcpServer[]`，默认 `[]` |
| `harness` | `YdAgentHarnessState`，必填 |
| `busy` | `boolean`，默认 `false` |

事件为 `fileSelect(file)`、`fileToggle(path, included)` 和 `skillToggle(code, enabled)`。`busy` 只锁定文件纳入与技能切换，仍可切换页签、打开文件和展开 MCP 工具清单。

MCP 状态使用 `connected`、`disconnected` 或 `error`；面板不尝试连接，也不按工具数量猜测连接状态。上下文进度只在宿主同时提供有效 `contextTokens` 和正数 `contextLimit` 时显示。`checkpoint`、`memory`、`runId` 与阶段文案均为宿主状态投影，面板不会创建检查点、压缩上下文或保存记忆。

## YdAgentAnswer 与 version 1 渲染协议

`YdAgentAnswer` 的必填属性为 `document: YdAgentRenderDocument`；可选属性 `retryDisabled` 默认为 `false`，用于宿主暂时禁止工具重试。它按 `blocks` 顺序渲染，不解析 SSE，也不建立消息存储。宿主将自己的流事件、结构化回答或工具日志映射为该文档后再传入。

```ts
const answer: YdAgentRenderDocument = {
  version: 1,
  blocks: [
    { type: 'markdown', text: '工作台只消费状态投影。相关约定见 [[ref:workspace-rule]]。' },
    {
      type: 'reference',
      reference: {
        id: 'workspace-rule',
        title: '工作区约定',
        path: 'AGENTS.md',
        startLine: 7,
        endLine: 12,
        excerpt: '任务执行、权限校验与持久化由宿主 Harness 管理。',
      },
    },
    {
      type: 'tool',
      tool: {
        id: 'check-1',
        name: 'typecheck',
        label: '检查组件类型',
        status: 'running',
        input: 'pnpm --filter @yudream/components run typecheck',
      },
    },
  ],
}
```

`version` 当前必须为字面量 `1`。宿主应在边界验证收到的数据，仅传入支持的块类型与状态；这里的版本号是 UI 文档协议版本，不是 npm 包版本或运行时协议版本。

| 块类型 | 字段 | 表现 |
| --- | --- | --- |
| `markdown` | `text: string` | Markdown 正文、列表、表格与代码；原生 HTML 文本不作为自定义执行界面 |
| `reference` | `reference: YdAgentReference` | 编号、来源、原文片段，以及可选文件路径和行号 |
| `tool` | `tool: YdAgentToolCall` | 调用名称、状态、时长，以及可展开的输入、输出和错误详情 |

### 引用与定位

`YdAgentReference` 的必填字段为字符串 `id`、`title`、`excerpt`；可选字段为 `url`、`path`、`startLine` 和 `endLine`。

正文中的 `[[ref:id]]` 引用同一文档内的 `reference` 块。组件按首次出现顺序编号，重复 ID 沿用同一来源，未知 ID 保留标记文本；代码块和行内代码中的标记保持原样。点击正文编号触发 `referenceSelect(reference)`，宿主可以展示完整原文或打开指定文件位置。

每个回答实例独立生成页面锚点，因此不同回答复用同一引用 ID 也能定位到各自的原文。事件中的 ID 保持宿主传入的原值。

引用卡片同时显示原文 `excerpt`。有效的 `startLine` / `endLine` 使用从 1 开始的行号；只有文件路径的来源通过事件交给宿主定位，不直接调用浏览器文件系统。`url` 仅允许明确的 HTTP(S) 外链，卡片使用新窗口和 `noopener noreferrer`；来源点击也通知 `referenceSelect`。不要把工作区路径拼成可执行 URL 或把来源 ID 转为数值。

### 工具状态与详情

`YdAgentToolCall` 必填字段为 `id`、`name`、`status`；可选字段为 `label`、`description`、`input`、`output`、`error`、`durationMs` 和 `retry`。

| `status` | 显示文案 | 含义 |
| --- | --- | --- |
| `queued` | 等待执行 | 调用已经排队 |
| `running` | 执行中 | 宿主报告调用正在运行 |
| `awaiting-approval` | 等待确认 | 调用正在等待批准 |
| `success` | 已完成 | 宿主报告调用成功 |
| `error` | 执行失败 | 调用失败，可展示错误详情 |
| `cancelled` | 已取消 | 调用已取消 |

点击工具标题会展开或收起详情，并触发 `toolSelect(tool)`。输入、输出和错误按文本展示，不执行其中的命令或 HTML。只有 `status === 'error'` 且 `retry === true` 时才显示重试按钮；点击触发 `toolRetry(tool)`，组件不会自行重试。

回答的 `awaiting-approval` 状态只表示工具进度。真正的待批准事项通过工作台 `approvals` 提供，用户选择触发 `approvalDecision(id, approved)`；宿主必须校验运行、操作范围和批准有效性，再恢复执行。

`YdAgentAnswer` 的事件为 `referenceSelect(reference)`、`toolSelect(tool)` 和 `toolRetry(tool)`。工作台原样向外转发，方便宿主统一处理。

## 既有 YdChatMessage 兼容

外部 JSON 文档可先通过根入口的 `parseYdAgentRenderDocument(value)` 校验：未知版本、未知块或非法字段返回 `undefined`，字符串 ID 保持原样。`YdAgentAnswer` 也会在渲染入口校验，无效内容显示格式提示。

`messages` 保持 `YdChatMessage[]`，没有强制迁移已有 `content`、`reasoning`、`citations`、`tools`、`activities`、`actions` 或 `attachments`。没有匹配渲染文档时，正文与旧工具事件映射为新渲染块，检索/图谱过程继续使用 `YdChatProcess`，推理、引用和附件由 `YdBubble` 承载；`pending` 继续表示生成中，`error` 继续表示错误气泡。兼容事件为 `citationSelect`、`retrievalSelect`、`graphNodeSelect` 和 `actionSelect`。

需要新回答协议时，为消息提供稳定的字符串 `id`，并在 `renderDocuments[message.id]` 放入 `version: 1` 文档。该文档替换消息的正文展示，消息的其他兼容信息仍由既有气泡承载。建议同时在 `message.content` 保留可复制和存储的纯文本或 Markdown 回答；工作台的“复制回答”使用这个字段。

工作台运行状态与工具状态是不同层级：`YdAgentRunStatus` 为 `idle`、`running`、`waiting`、`completed`、`error`、`cancelled`；`YdAgentActivity.status` 为 `pending`、`running`、`complete`、`error`、`waiting`。宿主应按自己的权威事件映射，不能仅因一个工具成功就将整项任务标记完成。

## 生产会话与全局助手

`/platform/chat` 和根级全局助手共享 `useAiAssistantStore`，使用统一 `/api/platform/ai/runs` 快照与 SSE 游标。刷新先读取服务端快照再续读，关闭浮层与路由卸载均不调用取消；停止按钮发送明确的服务端取消请求。正文、真实 reasoning、工具卡片和生命周期活动分别投影，缺少真流式实现时明确失败，禁止计时打字或阻塞后整段回放。

全局助手仅在登录且具备助手使用与会话查看权限时挂载，默认 `Ctrl/⌘+Shift+Y` 唤起，可选择 `Alt+Shift+A` 或关闭快捷键。路由、页面元数据、导航工具与高亮各自可关闭，默认关闭；偏好按账号保存在当前浏览器。开启对应工具后，宿主按可见菜单和路由权限校验导航，按页面显式注册的稳定操作标识聚焦和高亮，再回传真实结果。不能读取任意 DOM、表单、查询参数或令牌，不能接受任意脚本、选择器或代用户点击操作。

共享输入区提供 `sender-actions` 插槽以承载宿主的业务范围选择。附件上传与解析交给宿主，完成后通过 `attachments` 回显，通过 `removeAttachment` 更新；组件不另建文件 API。普通会话显式只开放 `availableModes=['ask']`。审批、MCP 和 Git 操作由真实宿主协议提供；组件示例不会被解释为已经接通生产服务。

`message-extra` 插槽接收 `{ message, index }`，承载宿主特定的工具结果与澄清问题；`composer-before` 位于输入区之前，承载真实等待、断线恢复和业务提示。`placeholder`、`attachmentAccept`、`imageUrlResolver` 分别控制输入提示、上传候选类型和引用图片地址；处理权限和上传解析仍属于宿主。过程活动即使存在 `renderDocuments` 也继续显示，检索与关系图不会被自定义回答块遮掉。

CMS 画布通过公开 `useYdPersistentChatStream` 与宿主的 `persistence` 端口接入根级浏览器运行注册表。`ownerKey` 必须是登录用户字符串标识，`contextKey` 必须稳定；`latest(contextKey)` / `snapshot(runId)` 返回权威运行快照，`cancel(runId)` 是显式取消，`toolResult(runId, request, reply)` 回传真实工具结果，`toolsReady` 防止编辑器未就绪时执行画布工具。首帧 `RUN_STARTED.runId` 与字符串 `cursor` 保存后，断线仅发送 `{ resumeRunId, after }`，不会再次提交问题。重复请求复用待回执 Promise 和已核销结果；卸载只移除页面工具绑定，重新进入页面后恢复原生挂起请求。

后端 `WAITING` 不是结束状态。普通导航与高亮在用户开启开关后自动执行，危险工具的原生 `confirmation` 显示本次批准/拒绝；外部执行只由对应业务页面处理，再以真实回执接续原 turn。退出登录调用 `clearYdPersistentChatOwner` 断开旧用户的浏览器观察，后台记录保留。公开 Wiki 浮窗仍仅登录后可提问，使用独立 `public-wiki:{slug}` 的用户运行记录；私有 Wiki、研究、Lint、摄入及图片描述都观察同一运行日志。

内部模型阶段的 `ai.generation` 根据 `callId` 分别展示真实正文与推理，不混入主回答。结构化 JSON 仅以已收到的完整字段或完整条目更新业务草稿，原始增量可折叠查看。额度将实际用量、运行预留与待核对用量分开展示；有额度配置权限者管理用户/系统每日预算，有查看/编辑权限者依据供应商真实 usage 核对扣留额度，前端不估算或伪造用量。

`/platform/ai/system-runs` 是独立的系统任务观察页面，只使用 `/api/platform/ai/system-runs` 端口和专有状态仓库；系统 owner `0` 不进入个人助手列表。`platform:ai:system:view` 控制查看，`edit` 控制取消，`accept` 控制本次原生批准/拒绝。页面卸载不取消，退出登录或失去查看权限时断开浏览器观察。

## 宿主接入边界

Harness 拥有模型调用循环、工具执行、权限、技能可见范围、上下文压缩、记忆与检查点。组件只展示结果和提交意图，不在前端手写底层 Agent 循环，不裁剪服务端对话历史，也不将 UI 的按钮状态当作授权。

`skillToggle` 和 `send.skillCodes` 表示用户提交的候选选择，宿主需要过滤不可用或无权限技能。`contextPaths` 也是候选路径，必须在宿主工作区范围和文件权限内解析。普通聊天型 Agent 仍应禁用宿主文件系统和 shell 工具；选择“编码”标签不会自动开放它们，只有宿主已授权的隔离 Coding 运行时才可提供相应能力。

`YdAgentWorkspace.access` 用于表达当前可用性，`YdAgentMcpServer.status` 用于表达真实连接状态。Git 分支和 diff 都由宿主提供；变更抽屉只展示文本，不自动 stage、commit、push、回滚或应用补丁。批准按钮也只发出本次决策事件，不绕过宿主权限或审批系统。

宿主应把中止请求接入自己的取消机制，把取消、错误和断连结果回写到 `harness`、工具文档及轨迹。组件不会猜测请求是否成功，也不会把本地动画结束当作真实运行完成。

## 界面参考

规划与执行分层的界面组织参考 Cursor 的 Plan Mode：先形成可审阅计划，再进入实现。本组件的问答、规划、编码是宿主可映射的 UI 模式，不是对 Cursor 行为或 API 的兼容承诺。[Cursor Plan Mode](https://cursor.com/docs/agent/plan-mode)

Skills 和 MCP 分开展示，便于区分任务指导与外部工具。Cursor 官方分别描述技能发现和 MCP 工具连接，本组件仅采用这类信息组织方式；启用候选 Skill 不等于取得执行权限。[Cursor Agent Skills](https://cursor.com/docs/skills)、[Cursor MCP](https://cursor.com/docs/mcp)

Harness 状态、会话和工作区分开建模，参考 Managed Agents 将 Agent、环境、会话及事件分开组织的思路。该参考用于解释 UI 边界，YuDream 后端仍遵守项目既有 AgentScope Harness 架构，没有引入 Claude Managed Agents 服务。[Claude Managed Agents overview](https://platform.claude.com/docs/en/managed-agents/overview)、[Anthropic Managed Agents architecture](https://www.anthropic.com/engineering/managed-agents)

工具调用保留可见状态、输入输出及批准入口，参考 MCP 官方工具规范关于工具可见性、调用指示与用户介入的建议。这里的 UI 渲染协议不替代 MCP 的 `tools/list` / `tools/call` 协议，工具校验和访问控制仍在宿主与服务端执行。[MCP Tools specification](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)
