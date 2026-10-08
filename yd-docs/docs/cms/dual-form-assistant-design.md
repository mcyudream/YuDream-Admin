# 双形态 AI + 统一工作区 —— 设计定稿（对齐 coding harness 范式）

> 参考：ZCode / deepseek-harness(dsh) 等 coding harness 框架的共性——
> 一个常驻 agent 运行时 + 「一切围绕工作区」+ 两种交互形态（主控制台 + 随处唤起的
> 上下文感知浮层/内联）。
> 结论：**YuDream 已具备 80% 的地基**（AgentScope Harness 运行时、工作区 KV、
> workspace profile agent 家族、AG-UI 流组件），缺的是「路由/参数感知的浮层形态」
> 与「工作区注册中心」两个环节。

## 1. 两种形态的定义

### 形态 A：统一 AI 助手（已存在）
- 入口：`/platform/chat` AI 助手页 + 左侧「AI 助手」悬浮入口。
- 会话模型：GENERAL/WIKI/AGENT scope → 各自 Harness 会话（sessionId = chat-<id>）。
- 定位：完整对话界面、历史管理、附件与额度。

### 形态 B：浮层 + 路由感知 + 参数感知（待建）
- 入口：全局浮层（任意管理页按快捷键/按钮唤起），以及页面内嵌（如 CMS 工作台
  的 AI 抽屉——已是本形态的第一个实例）。
- **路由感知**：浮层根据当前路由自动选择 agent 与会话：
  | 路由 | agent | targetKey/sessionId |
  |---|---|---|
  | /platform/cms/workspace | cms-builder | cms-<targetKey>（现有） |
  | /platform/wiki/** | wiki-qa | wiki-<spaceSlug> |
  | /platform/theme-center/** | cms-builder | cms-home（主题首页） |
  | /platform/agents/**（工作流） | workflow | wf-<runKey> |
  | 其他 | general | chat-<用户会话> |
- **参数感知**：浮层打开时采集页面上下文参数（当前编辑文件、选中元素、知识库
  空间、主题 code），作为 RuntimeContext 参数/首条提示注入——与 CmsGrapesEditor
  现有 aiPayload（选中元素+纲要）同机制，泛化到全局。
- 会话与形态 A **共享同一 AgentState**：同 (agentCode, userId, sessionId) 下，
  浮层里聊到一半的改动在助手页继续，反之亦然（AgentScope 状态存储天然支持）。

## 2. 统一工作区（workspace 注册中心）

coding harness 的核心是「agent 围绕一个工作区工作」。泛化现有实现：

```
WorkspaceDescriptor {
  code            工作区编码（cms-site / workflow-<app> / 未来 plugin-<code>）
  agentCode       绑定的 Harness agent profile（cms-builder / workflow / general）
  targetKey       会话与文件寻址键（home / page-x / app-1）
  scopes          哪些路由/页面挂载浮层
  contextResolver 前端上下文采集器（route/选中/表单快照 → 注入参数）
}
```

- 后端：新增「工作区注册」只读端点（列表+解析），浮层按当前路由匹配 workspace；
- 前端：`useWorkspaceAssistant()` composable——路由变化时解析当前 workspace，
  为浮层提供 agentCode/sessionId/上下文参数；
- CMS 站点工作区是第一个注册项；工作流应用、Wiki 空间按同协议追加注册即可。

## 3. 实施分解

1. **后端 handoff 端点**：`POST /api/platform/ai/assistant/handoff`——入参
   {route, params, targetKey}，返回 {agentCode, sessionId, systemPrompt 附加,
   allowedTools}（复用 harness runtime 的解析逻辑，浮层无需感知映射表）。
2. **前端全局浮层**：`YdAssistantOverlay`（App 级挂载）——快捷键/角落按钮唤起，
   路由变化自动重绑会话；复用 useYdChatStream AG-UI SSE。
3. **工作区注册表**：前后端各一份 WorkspaceDescriptor 清单（CMS 先行）。
4. **浮层能力增强**（迭代）：页面选中元素注入（对齐 CmsGrapesEditor aiPayload）、
   工具确认 HITL 卡片、文件树联动高亮。
5. dsh 式「一切皆插件」远期项：PluginSiteContribution 已是雏形——插件未来可
   注册自己的 workspace 贡献（菜单项/插槽已通，工作区类型待扩展）。

## 4. 本轮已交付的相关件

- CMS 站点工作台重写为主题中心风格（表格+弹窗+srcdoc 预览+AI 抽屉）——
  抽屉即形态 B 在 CMS 场景的落地样板；
- AgentScope RuntimeContext 已支持按次 systemPrompt/模型/工具注入——
  浮层的参数感知复用 PerCall 三中间件，无新增运行时改动。
