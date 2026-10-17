# AI 经验：AgentScope、额度、恢复与 CMS 工作流

> **适用范围**：AgentScope Harness、统一 AI 运行、工具权限、CMS AI 与 SSE。
> **目标**：保留会导致运行中断、越权或 UI 假流式的已验证根因。

## 1. AgentScope 模型与请求

### Agent 执行报 API Key 未配置，却能在后台配置中看到 Provider

- **现象**：调试/运行 Agent 请求模型时回退 Default AI 并报 API Key 未配置。
- **根因**：构建 `AiGenerationRequest` 只传 `providerCode/modelCode`，`config=Map.of()` 没带启用 AI 能力的 providers 配置。
- **规避**：Agent 运行和调试都从已启用 AI capability 取完整 providers 配置后传给网关；聊天只选 `kind=chat` 模型。
- **来源**：`agent-ai-request-config`；`docs/platform/ai-foundation.md`。

### 模型调用工具后 UI 没有最终分析文本

- **现象**：工具结果成功，但回答只展示工具卡片或生命周期状态。
- **根因**：`AiGenerationResult` 因 `toolResults` 非空清空 summary/content，忽略 AgentScope 最终 Msg 正文。
- **规避**：最终 Msg 正文优先；为空时回落首条工具消息，不因工具列表非空就丢 summary。
- **来源**：`ai-tool-result-final-text`。

### Agent 原生工具调用越出节点配置范围

- **现象**：模型可看到/调用未在当前节点授权的工具，或独立 tool 节点成为默认新编排方式。
- **根因**：把应用级工具并集当作每次模型调用的允许范围。
- **规避**：每个聊天模型节点持久化 `toolCodes`/`toolMode`，调用前解析成作用域受限 `AiAgentTool` 回调；应用级列表只是后端从工作流推导的授权并集；独立 tool 节点仅兼容历史迁移。
- **来源**：`agent-model-native-tool-scope`。

### Agent 运行消息无法渲染可选字段

- **现象**：模型/工具输出缺少 intent 等可选字段时，整个工作流模板渲染失败。
- **根因**：模型与工具 JSON 是动态 map，模板直接取不存在的键。
- **规避**：缺失 map 字段视为空值；可选模型字段使用安全导航。
- **来源**：`workflow-map-missing-field`。

## 2. 持久运行与权限恢复

### 刷新页面后模型重复执行或事件出现缺口

- **现象**：浏览器刷新导致任务重跑、重复扣额度，或恢复的 SSE 少事件/重事件。
- **根因**：把浏览器观察连接当执行生命周期；快照游标与后续事件头不一致；恢复时重新提交输入。
- **规避**：后端 `AiRun` 是统一持久运行出口，按同一日志头读快照再从该头后续读事件；刷新先还原快照与 cursor，再续读，绝不重发输入；关闭/切页只解除观察，取消必须显式 API。
- **来源**：`ai-persistent-run-outlet`、本轮收尾验收；`docs/platform/ai-foundation.md`。

### 多副本恢复时 Agent Session Log 丢失

- **现象**：远程工作区已配，任务原生待办却在副本/重启后不可续接。
- **根因**：AgentScope 默认 Session Log 仍绑定本机 LOCAL filesystem，远程工作区配置不会自动迁移日志。
- **规避**：Session Log 显式绑定共享 Mongo RemoteFilesystem；AgentSession 按用户/Agent/会话隔离，执行后关闭；匿名长期记忆使用 SESSION，不与已认证 USER 记忆共享。
- **来源**：`ai-native-session-storage`、`HarnessAgentRuntime` 验收。

### CMS AI SSE 校验读到旧画布快照

- **现象**：工具已经通过 SSE 在浏览器实时应用变更，但请求结束时服务端校验认为画布缺内容或校验整个请求失败。
- **根因**：服务端结束时仍只有修改前快照；模型漏掉只读 `cms.canvas.validate` 不等于最终画布无效。
- **规避**：模型明确返回 `valid=false` 才阻断；最终实际画布校验由浏览器侧状态或显式模型校验承担，禁止用旧快照伪造最终状态。
- **来源**：`cms-sse-validation-ownership`。

### CMS AI SSE 校验读到旧画布快照

- **现象**：工具已经通过 SSE 在浏览器实时应用变更，但请求结束时服务端校验认为画布缺内容或校验整个请求失败。
- **根因**：服务端结束时仍只有修改前快照；模型漏掉只读 `cms.canvas.validate` 不等于最终画布无效。
- **规避**：模型明确返回 `valid=false` 才阻断；最终实际画布校验由浏览器侧状态或显式模型校验承担，禁止用旧快照伪造最终状态。
- **来源**：`cms-sse-validation-ownership`。

### CMS 固定 Header/Footer 被 AI CSS 覆盖校验误报

- **现象**：首页 CSS 最终校验把系统 Header/Footer 判成 AI 未覆盖结构而失败。
- **根因**：通用 class 覆盖校验把 `data-yb-chrome` 固定 Header/Footer 与 `site-builder-home` 系统包装器也当成 AI 输出主体。
- **规避**：固定结构排除出 AI CSS 覆盖检查，由 `cms.chrome.style` validate 专门校验；首页主体与 add-html class 覆盖仍严格校验。
- **来源**：`cms-locked-chrome-validation`。

### 管理员通过审批后恢复任务获得管理员权限

- **现象**：SYSTEM 或匿名任务的等待工具被管理员接受后，以管理员权限继续执行。
- **根因**：把 accept 人的当前权限当作原始任务权限。
- **规避**：恢复时刷新原调用者权限；管理员 accept 只核销决策，不借出 `*` 权限；持久化原 actor/connection/channel/trace 与点名工具范围，不存旧 RBAC/密钥；沙箱失效时拒绝恢复，禁止回落真实连接。
- **来源**：`ai-restored-actor-permissions`；`docs/platform/ai-foundation.md`。

### Agent workflow WAITING 恢复后重复执行业务副作用

- **现象**：审批/外部工具回复后，前面已完成的业务节点又运行一次。
- **根因**：只存最后一条消息或从头重跑图，没有保留图快照与节点检查点。
- **规避**：保存原图、节点 frame/cursor、激活与已执行集合、变量、输入输出和运行参数；WAITING 后接续原图，不重跑已提交副作用；不可验证的中断明确失败并留日志。插件跨进程作业保存 runId，最终用 `getRun.response` 业务结果，不把多节点 UI 正文拼接冒充结构化结果。
- **来源**：`ai-workflow-waiting-checkpoint`。

### 模型使用量未知时被当成零额度释放

- **现象**：模型调用已发出但上游未返回可靠 usage，额度被完全释放。
- **根因**：把未知用量当 0，或核对/并发上限更新覆盖了账本。
- **规避**：每次实际调用统一原子预占与续租；无可靠 usage 时保留待核对预算，已知部分计实际、其余保留；管理员核对幂等只补差额；上限更新不覆盖并发收据；用户与 SYSTEM0 按上海日期分账。
- **来源**：`ai-unknown-usage-ledger`；`docs/platform/ai-foundation.md`。

## 3. 工具流、SSE 与异步身份

### 前端看似流式，实际等待整个模型调用后才出现正文

- **现象**：请求期间无增量，完成后整段快速出现。
- **根因**：把阻塞 `generate/chat/runAgent` 的最终文本回放为 delta；某些框架工具规划阶段本来就不发自然语言 delta。
- **规避**：SSE 端到端真流式，缺真流式实现明确失败；额外用 `ai.progress` 表达 analysis/subscribed/tool-start/tool-complete；工具回调执行即发结构化结果；真实 reasoning、自然语言、工具与生命周期独立。
- **来源**：`ai-streaming-no-fallback`；`docs/platform/ai-foundation.md`。

### SSE 已发结构化错误后日志又出现异常处理失败

- **现象**：客户端已收到 `ai.error`，服务端随后又进入全局 JSON exception handler 并报内容类型错误。
- **根因**：发完 SSE 错误又调用 `completeWithError`，Spring MVC 尝试在 `text/event-stream` 响应上写 JSON。
- **规避**：发送结构化 `ai.error` 后正常 `complete()`；事件使用统一 envelope，携带 event/action/module/traceId/timestamp/payload。
- **来源**：`ai-sse` 规则；`docs/platform/ai-foundation.md`。

### 异步 Agent 工具失去原用户权限或错读管理员线程权限

- **现象**：同步能通过的工具到 CompletableFuture/Reactor worker 线程后权限为空，或意外借用执行审批管理员的 ThreadLocal 权限。
- **根因**：Sa-Token/API-key 上下文是线程本地变量，异步线程不可依赖它。
- **规避**：在可信请求入口捕获 caller permission snapshot，随边界命令传递至 `AgentRunCmd`，执行时用该快照做 fail-closed 复核；未绑定用户只能调不要求本站权限的工具。
- **来源**：`agent-async-permission-snapshot`；`docs/platform/ai-foundation.md`。

### 新增工具未声明风险，却在安全仿真中执行写操作

- **现象**：沙盒只挡了插件工具，宿主或 Python/native callback 写工具仍有副作用。
- **根因**：安全守卫只挂在单个插件 gateway，或未声明风险被默认视为 READ。
- **规避**：统一执行入口覆盖 system/plugin/runtime/Python/native callback；未声明风险默认 WRITE；只有显式 READ 且过权限、触发与 allowedToolNames 检查才可沙盒执行。
- **来源**：`agent-tool-risk-default-write`。

## 4. Spring 装配：额度网关构造循环（本轮修复）

### 启动报 BeanCurrentlyInCreationException，能力 Provider 链回到 CapabilityAppService

- **现象**：Spring 启动失败，依赖链为 `capabilityRuntimeBootstrap → capabilityAppService → aiCapabilityProvider → agentScopeGenerationGateway → chatQuotaAppService → capabilityAppService`。
- **根因**：`@Autowired(required=false)` setter 参数仍在 Bean 构造期即时解析，`ChatQuotaAppService` 又依赖 `CapabilityAppService`；可选注入不等于延迟注入。
- **规避**：仅在额度网关运行期调用的 setter 参数上使用 `@Lazy AiUsageQuotaGateway`，注入懒代理，打断构造期依赖环。`AgentScopeGenerationGateway` 与 `HarnessAgentRuntime` 两处保持一致；不要把非循环依赖无差别改为懒注入。
- **来源**：提交 `7273d855`（2026-10-14）；目标测试 48 例通过，详见记忆 `agentscope-migration-delivered`。单测未覆盖 Spring 全上下文 wiring，实际启动仍应在目标环境验收。

## 5. 工作区身份与 AgentScope API 验收（本轮收尾经验）

### Agent session 路径用了随机实例 UUID，导致找不到原生会话

- **现象**：同一逻辑 Agent 恢复时 AgentStateStore/Session Log 寻址不一致，恢复出来像新会话。
- **根因**：`AgentBase.getAgentId()` 是每次构造随机 UUID（实例身份），不是逻辑 Agent code；稳定寻址来自 `builder.name(agentCode)`（逻辑会话日志路径身份）。
- **规避**：区分实例 ID 与逻辑 name；持久路径使用稳定 agentCode/name；以实际 AgentScope 2.1.0-BETA1 发布 API 验证，不凭文档猜测。
- **来源**：AgentScope 收尾验收 2026-10-14；记忆 `agentscope-migration-delivered`。

### 验收测试破坏开发数据库

- **现象**：持久层集成验收通过前后，开发库集合被删除或无法还原。
- **根因**：测试 fixture 复用宿主连接/共享数据库名，并在 setup/teardown 执行 drop。
- **规避**：验收 fixture 使用独立随机数据库名；创建前确认 URI/DB 名确属临时库；drop 只对该随机库执行；测试后核对宿主库仍可连接。禁止把环境默认开发数据库直接传入破坏性 fixture。
- **来源**：AgentScope 收尾验收 2026-10-14（验收 fixture DB-drop 经验）。

## 6. Wiki 与工具记忆

### Wiki 聊天在检索前泄漏空间目录

- **现象**：用户无 Wiki 查看权限，但能通过聊天请求影响空间枚举或目录读取。
- **根因**：权限检查放在检索后，或复用管理目录接口返回草稿。
- **规避**：先校验服务端捕获的 Wiki view 权限快照，再做任何空间枚举/检索/目录读取；目录兜底只物化已发布版本。
- **来源**：`chat-wiki-permission-snapshot`。

### 技能工具可见但 Agent 没有可用技能清单

- **现象**：数据库有启用技能，Agent 推理仍不知道有哪些技能。
- **根因**：每个 Harness Agent 另接本地技能列表，而非使用框架 AgentSkillRepository。
- **规避**：`platformAgentSkill` 作为框架统一技能源，MongoAgentSkillRepository 实现接口，每轮经 `<available_skills>` 注入。
- **来源**：主仓 AgentScope 迁移规范；`docs/platform/ai-foundation.md`。

## 7. 系统工具模块与 MCP 接入（2026-10-18）

### 系统工具开关只改管理端列表，运行期仍能调用

- **现象**：管理端把某个模块关掉，模型/工作流仍能列出甚至调用该模块的工具。
- **根因**：工具装配有多处入口（Harness global toolkit、工作流 `AgentToolExecutor`、schema 过滤中间件、管理端候选与发布校验），只在一处过滤等于没有过滤。
- **规避**：开关判定用同一枚举/同一服务（`AiToolModuleAppService`），在四处全部落闸：Harness 装配时不注册、`resolve()` 时抛「工具模块已关闭」、中间件同时校验加载状态、候选/授权并集过滤；插件与 MCP 工具参与同一套模块编码（`plugin:<code>`／`mcp:<server>`），不另开开关。
- **来源**：系统工具模块化交付（`AiToolModuleAppService`、`AiToolModulePlan`）。

### 按需加载把已授权工具挡在门外（审批恢复被拒）

- **现象**：模型在上一轮规划了写入工具，用户确认后恢复运行，工具调用被「当前会话未授权 AI 工具」拒绝；或 CMS/插件会话里显式授权的工具突然不可见。
- **根因**：按需加载状态按「每次运行」重建，恢复/审批续跑与调用方 scoped 授权的工具集被当成普通全局工具集过滤。
- **规避**：只在全局工具集上做按需过滤；`AiAgentToolExecutionScope` 非空（调用方显式授权）与 `resume/respond/confirmDecisions` 续跑时 `onDemand=false`（`AiToolModulePlan.newLoadState(false)`），全部已注册工具直接可见；`load_tools` 元工具自身始终可见。
- **来源**：系统工具模块化交付（`HarnessAgentRuntime.execute` 的 `resuming`/`scoped` 判定）。

### MCP 工具编码在管理端与运行期不一致

- **现象**：从管理端复制的工具编码存进工作流后运行期解析不到（工具不存在）。
- **根因**：模块编码用冒号（`mcp:code`）、工具名用点（`mcp.code.tool`），两处各写一遍字符串拼接就会漂移。
- **规避**：编码只在 `AiToolModules.mcpToolName(serverCode, toolName)` 一处生成，管理端 DTO、桥接工具名、前端模块索引都调它；前端不得自行拼 `mcp:` 前缀当工具编码。
- **来源**：编排页模块选择器联调（`AiToolModules.mcpToolName`；前端已在 editor/index 注释中标注）。

### MCP 探测挂住：SSE 响应体不会自己结束

- **现象**：调用 MCP 连接测试长时间不返回，服务端日志显示已完成响应。
- **根因**：Streamable HTTP 允许用 `text/event-stream` 返回单次响应，部分实现不主动关闭流；JDK HttpClient 的 `send()` 只对响应头生效，`readAllBytes()` 会一直等。
- **规避**：响应体读取放到独立线程并按 `timeoutMillis` 强超时（`Future.get` + 超时后关闭流），再按 SSE 帧解析出与请求 id 匹配的 JSON-RPC 消息；不要用 `BodyHandlers.ofString()` 直接等体结束。
- **来源**：`HttpMcpClientGateway`；测试 `HttpMcpClientGatewayTest`（含 SSE 体、分页游标、协议版本回退、会话头回传）。

### 机器审核判定不能只有布尔：要分清「判定不通过」与「没审成」

- **现象**：评论交给 AI 审核时，把「模型说不行」与「模型没答上来（能力未启用、模型未配、上游 5xx/超时）」混成一个结果，人工复核时无从下手；更糟的是解析失败被当成通过，等于用坏输出替人做决定。
- **根因**：只设计了布尔语义（通过/不通过），没有给「审核不可用」留位置；解析失败也没和模型判定分开处理。上游聚合网关常见 `No available channel for model xxx` 这类 500，看起来像「模型拒绝」其实根本没审。
- **规避**：三态落库（approved / rejected / unavailable）并各自带理由摘要与模型标识，判定理由要能被治理端读到；只有明确 `approved: true` 才自动公开，其余一律回落人工；模型输出解析失败、缺字段、类型不对都必须抛异常（不是判定），由应用层记成 unavailable 并保住读者内容。判定端口在领域层，SQL/HTTP 细节留在基础设施实现。
- **来源**：`ai-comment-review-three-state`（Wiki 评论 AI 审核：领域端口 + 治理端三态标签）。
