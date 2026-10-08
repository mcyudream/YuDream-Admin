# 形态 A 重构：AI 控制台（对齐 coding harness 主控制台）

> 参考 ZCode(TUI)/deepseek-harness(Web) 提炼的 harness 主控制台共性：
> **会话即工作区实例**（每个会话绑定 agent + 工作区）、**工具过程全可见**
> （turn/step 事件流：思考、工具调用卡片实时展开）、**HITL 审批**、
> **上下文注入**（agent.inject / @引用）、**一切能力可插拔**。

## 1. 现状差距（/platform/chat → AI 控制台）

| harness 特性 | 现状 | 重构 |
|---|---|---|
| 会话绑定工作区 | 会话只有 scopeType，模型无工具、无工作区 | 会话选择「工作区类型」→ 决定 agentCode + targetKey + 工具面 |
| 工具过程可见 | 后端有 TOOL_CALL 事件，前端渲染弱 | AG-UI 全事件控制台渲染（工具卡片实时展开） |
| HITL 审批 | 无 | RequireUserConfirm → 确认卡片（框架事件已就绪） |
| 上下文注入 | 无 | @工作区文件引用 → 注入首条提示 |
| plan/todo | 无 | 迭代项（AgentScope enableTaskList） |

## 2. 会话模型（复用 ChatSession 聚合，新增 CONSOLE scope）

```
ChatScopeType 新增 CONSOLE
ChatSession.agentCode 取 profile 编码：general | cms-builder | workflow-<appCode>
sessionId = console-<chatSessionId>   （Harness 会话与 ChatSession 1:1）
工具面按 profile：general=无文件工具 / cms-builder=文件+chrome / workflow=文件+应用工具
```

- 对话链路：`ConsoleChatDispatcher → HarnessAgentGateway.run`（与 CMS 工作台同链路，
  区别在 agentCode 与事件透传）；
- 额度/历史/消息落库/SETTLE 全复用现有 ChatAppService.send 流程（零新概念）；
- AG-UI 事件已由 ChatAguiWebAssembler 转换（TEXT/THINKING/TOOL_CALL/RUN_*）。

## 3. 前端控制台（AI 助手页重构）

- 新会话弹窗增加「工作区类型」选择（通用助手 / CMS 站点 / Agent 应用）；
- 消息流渲染强化：工具调用卡片（toolName + 参数摘要 + 结果折叠）——数据源
  已是 AG-UI TOOL_CALL_* 事件；
- HITL 卡片：RequireUserConfirm → 内联「批准/拒绝」按钮（配 stopOnReject 或
  ConfirmResult 回传）；
- 输入框 `@` 引用工作区文件（读文件列表 → 注入文件内容到提示）。

## 4. 实施顺序

1. 后端：ChatScopeType.CONSOLE + ConsoleChatDispatcher（Harness 直连）+ 工具面映射
2. 前端：助手页新会话类型 + 工具卡片渲染强化
3. HITL 确认卡片（RequireUserConfirm 事件透传 + ConfirmResult 回传链路）
4. @文件引用注入
5. plan/todo（enableTaskList）迭代
