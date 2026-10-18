# AI 基座与运行恢复

AI 框架版本以根 `pom.xml` 为准，当前使用 AgentScope Java `2.1.0-BETA1` 的原生 `AgentSession` 提交、日志、待办和恢复 API。模型连接复用、增量拆包、工具循环、结构化输出降级、压缩与记忆交给框架；宿主提供权限、业务工具、Mongo 存储、额度和 UI 投影。

## 统一运行

宿主聊天、Wiki 问答/研究/检查/图片描述/摄入、CMS 生成/画布/文件工作区、Agent 调试及插件调用进入 `AiRun`。后端保留任务、输入、真实正文、推理、工具、进度、内部模型生成和原生待办。每项事件包含 `event/action/module/traceId/timestamp/payload` 和字符串游标；快照固定读取到一个日志头，随后重放该头之后的事件，避免跨连接漏增量或重复内容。

`RUNNING` 执行、`WAITING` 等待确认、`COMPLETED/FAILED/CANCELLED` 结束。页面关闭、切换、解绑订阅和 SSE 超时仅释放观察。停止执行必须显式取消。恢复审批回复沿同一任务 ID 和游标继续原生 turn，不重新提交输入；响应落库之后重试只重放已保存的原回答。未授权恢复预检保持待办可见、可取消。

个人观察 API 为 `/api/platform/ai/runs`；所有读取、事件、停止和回复按当前用户归属校验。`/api/platform/ai/system-runs` 是独立 SYSTEM 后台任务管理出口，分别需要 `platform:ai:system:view/edit/accept`。SYSTEM 额度账户与原生匿名 actor 分开，管理员恢复不会认领其他用户会话。

业务工作流暂停时保存原图、节点游标、已激活/已执行集合、变量、输入输出和运行参数，接续原图后续节点，不重跑已执行业务工具。同一轮多次审批按原生运行 ID 幂等合并真实阶段内容和用量。应用重启后的中断运行通过原生日志检查恢复待办；无法续接的中断必须明确显示失败并保留已生成内容。

## 工具与工作区

每次运行持有独立 Harness Agent，模型连接可复用。状态、长期记忆、远程文件系统与 Session Log 使用共享 Mongo，Session Log 必须显式配置，不能因配置远程工作区而隐式落到本机日志。

普通聊天禁用主机文件系统和 shell；对用户自己工作区的读取走平台只读工具（见下）。CMS 文件工作区与明确授权的工作流可使用隔离工作区，编辑与发布分别进行。工具由技能库和运行范围提供，经过 schema 过滤、框架规则/模式、执行时权限复核及沙箱守卫。隐藏工具即使被模型伪造调用也不能执行。

### 用户工作区（每用户一个）

- 每个账号默认拥有一个工作区：助手会话与上传文件都在其中，不需要额外开通步骤。文件存在 Harness KV 命名空间 `agents/general/users/&lt;uid&gt;/workspace`（集合 `platformAgentWorkspace`，`{content, encoding}` 载荷，单文件 8 MB 上限）。
- HTTP 端点 `/api/platform/chat/workspace`：列表/读取需 `platform:chat:workspace:view`，上传/删除需 `platform:chat:workspace:edit`；列表限 2000 个文件。
- 模型侧只有只读工具 `workspace.list` / `workspace.read`（模块 `workspace`，常驻，权限码同 `platform:chat:workspace:view`），由 `ChatRunAppService` 按调用方权限快照装配到本次运行；写入与删除只经界面端点，模型侧不提供。单次读取超 60000 字符截断，二进制只返回大小与类型说明。
- 附件上传（`POST /api/platform/chat/attachments`）与工作区文件是两套存储：附件走 S3 + `sysFileObject`（随消息传给模型），工作区走 KV（模型按需读）；两者都受 `platform:chat:use` 与 AI 能力闸门。

## 工具模块与按需加载

系统工具按**模块**组织，模块是开关与可见性的唯一单位：宿主工具按工具名前缀（`web`/`cms`/`wiki`/`ui`…，`load_tools` 归 `meta`），插件工具归 `plugin:<pluginCode>`，MCP 工具归 `mcp:<serverCode>`，工作流自定义 Python 工具归 `custom`。管理端「系统工具」页按模块展示与开关，不再逐工具列卡。

- **总开关 + 模块开关**：`platformAiToolSettings`（单文档）保存全局总开关与逐模块开关，默认全开；生效值 = 部署配置 `yudream.platform.agent.system-tools-enabled` **且** 持久总开关。`meta`（工具目录）不可关闭。
- **闸门落在所有装配点**，缺一即越权：Harness 装配（`HarnessAgentRuntime.toolkit`，关闭模块的工具不注册）、工作流解析（`AgentToolExecutor.resolve`，`ensureToolAvailable`）、模型可见性（`PerCallToolFilterMiddleware` 同时校验加载状态）、管理端列表与发布校验（`AgentAppService` 的候选/授权并集）。工具页只展示开关，不绕过任何一层。
- **按需加载（默认）**：`yudream.platform.agent.tool-loading=on-demand` 时，只有**常驻模块**的工具直接进入模型视野，其余模块由元工具 `load_tools` 加载后才可见：不带参数返回模块目录，带 `module` 返回该模块工具清单并标记加载。加载只改变 schema 可见性，**不授予权限**（执行层仍按权限快照与模块开关 fail-closed 复核）。`load_tools` 只注册在 Harness 路径的全局工具集上；调用方显式授权的 scoped 工具集（CMS 画布、前端工具、插件触发会话）与审批/恢复续跑不做按需过滤，避免把已授权工具或待确认调用挡在门外。`eager` 模式恢复旧行为（全部可见、不注册元工具）。
- 按需模式在系统提示词末尾追加模块协议说明（`PerCallSystemPromptMiddleware`），模型不知道 `load_tools` 时会先看到模块目录提示，而不是臆造工具名。

### 管理端工具目录与工具测试

- 「系统工具」页按**表格页规范**落地：`YdTablePageLayout` + `YdDynamicSearchForm`（筛选来自 `AiToolCatalogPageQuery` 的 `@Searchable` 声明：关键词/来源/模块/状态）+ `YdResponsiveEnhancedTable`。系统、插件、MCP、Python 工具**合并成一张表**，用来源标签区分；行级操作统一进表格 `actions`（测试 / 编辑 / 删除，仅 Python 工具可编辑删除）。`GET /api/platform/agents/tools/catalog` 提供分页行（含 `toolId`、`moduleEnabled`、`risk`、`inputSchema`），开关状态与运行期同源。
- **开关一律在表格里**，不再另设开关弹窗：模块开关落在「模块」列（同一模块的行各自渲染一个开关，切任意一个即作用于整个模块，切完刷新列表让所有同模块行同步）；Python 工具自身的启停开关落在「状态」列（`PUT /api/platform/agents/tools/{id}/enabled/{enabled}`，只改启用位不回写整对象）。系统/插件工具的启停跟随模块开关、MCP 工具跟随服务启用，因此它们的「状态」列只读。总开关（系统工具总开关）是页面级口径，放在表格上方说明条里，与「加载只影响可见性、不授予权限」的说明同排；禁用原因（不可关闭 / 部署配置已关闭 / 随总开关停用）就近显示在开关旁，不靠弹窗解释。
- `POST /api/platform/agents/tools/test`（权限码 `platform:agent:tool:test`）按当前登录用户的权限快照**真实调用一次**工具：系统 → 插件 → MCP → Python 四条通道分别解析。与运行期的唯一差别是不要求工具被某个 Agent 应用授权（测试端点自身权限即授权来源）；模块开关、工具启用状态、工具权限码、Python 运行器可用性照旧全部复核，返回 `success/message/durationMillis/payload` 而非抛 500，失败原因直接给管理员看。写入类工具由前端在调用前二次确认。
- 工具输入 schema 的归一化只有一处（领域 `AiAgentToolSchemas`，infra `ToolSchemas` 复用同一实现）：运行期模型侧与目录行共用同一换算，声明「属性名 → 描述」简写的工具也能在测试弹窗里生成参数骨架。
- 删除 MCP 服务时同步清除其工具模块开关状态（`clearModuleState`），否则同编码重新登记会继承上一次的关闭状态。

## MCP 服务接入

MCP（Model Context Protocol）作为独立平台能力 `mcp`（类型 AI，依赖 `ai`，配置闸门 `yudream.platform.capabilities.mcp.enabled`，运行期仍需 `CapabilityAppService.ensureEnabled("mcp")`）接入外部工具服务端。

- **传输**：v1 只实现规范的 **Streamable HTTP**（POST JSON-RPC，响应 `application/json` 或 `text/event-stream`）；旧版 HTTP+SSE 与 stdio 未实现，管理端不提供选项。每次操作执行完整 `initialize → notifications/initialized → 业务请求` 序列，不持有长连接；`Mcp-Session-Id` 只在单次序列内回传，初始化失败时按协议版本回退重试一次。
- **工具桥接**：连接测试/刷新时缓存 `tools/list`（含游标分页）；缓存工具按 `mcp.<serverCode>.<toolName>` 桥接为 Agent 工具，模块 `mcp:<serverCode>`，统一权限码 `platform:mcp:server:use`（只为登记动态生成权限会污染权限树）。`annotations.readOnlyHint` 推导只读，`destructiveHint` 需要确认；未标注一律按写入处理。工具执行时按需建连，服务端离线只影响该次调用。
- **管理端**：`/api/platform/mcp/servers`（`platform:mcp:server:view/create/edit/delete/test`）维护名称、编码、地址、请求头、超时、启用、常驻与允许私网；请求头值回显为掩码 `••••••`，保存时保持掩码或留空即保留原凭据。创建时自动做一次连接测试，失败不阻塞保存但会在卡片上显示 `lastError`。
- **私网**：MCP 地址由管理员登记（非模型可控），因此按服务记录显式允许私网/回环，默认允许；仍必须是 http/https 且经 `OutboundUrlGuard` 校验。

QQ 沙箱中的内置工具也受捕获的原沙箱守卫约束。即使外部消息已绑定本站用户，模拟运行仍使用 SESSION 工作区和记忆隔离，避免后台压缩/记忆污染真实用户的 USER 工作区；用户身份、额度账户和原生日志寻址保持原值。

原生待办保留真实工具名、请求 ID、参数和种类，前端工具回复返回模型。前端断开期间保留外部请求，重新打开合适页面再处理；客户端必须验证页面准备状态与保存的回复，避免重复执行。

## 全局助手

登录并有助手使用及会话查看权限时可全站唤起。默认快捷键是 `Ctrl/Cmd+Shift+Y`，可选 `Alt+Shift+A` 或关闭。根级投影跨页面保留，注销和账号切换清理对应观察和浏览器状态。没有助手页面权限的业务用户仍可观察自己有权启动的任务。

路由感知、页面感知、页面跳转、操作高亮和引导播放分别默认关闭，偏好按账号保存在浏览器。仅发送受控路由、当前权限可见的已登记动作和当前页面的引导编码目录；表单、令牌、任意 DOM 和 URL 查询参数不进入助手上下文。跳转、高亮与引导执行受宿主再次核对，不提供任意脚本或自动点击；引导只做展示定位，不代替用户操作。

## 额度

每次实际模型调用（包括上下文压缩和长期记忆模型）统一预占、续租、结算与释放。额度按上海日期分账，用户与 SYSTEM 账户分别配置。并发领取通过持久仓储原子操作；恢复原待办不重复扣取已经结算的历史调用。

未启动的过期预占可释放。已启动且没有可靠 usage 的调用进入待核对额度，已知部分计入实际消费，其余保持保留，不能伪造零用量。管理员在额度界面查看与核对，提交最终实际用量不得小于已知用量，幂等只结算差额。修改每日上限不会覆盖并发产生的账本收据。

## 闸门与契约

AI/Agent/Wiki 等 provider 和端点受项目配置限制，每个用例继续检查持久能力状态。构造 provider 不连接外部服务，能力关闭时不启动恢复或业务调用。

插件新契约与迁移约定见 [SPI 2.35.0 持久 AI 运行](../plugin-system/spi-2.35.0-ai-runs.md)，工作台组件见 [YD Agent 工作台](../yd-agent-workbench.md)。独立插件仓必须等契约正式发布验签后升级，禁止依赖宿主内部类或本地工作区包。

## 细则速查

本速查是 AI 域的工程规则沉淀，与上文主体互补不重复。

### 模型与网关

- AI 配置**必须**是端点列表结构（`providers` 数组，每项是一个端点：用途 + 协议 + base URL、key、代理、模型清单）；前端传 `providerCode` + `modelCode`；禁止拍平成 `baseUrl/apiKey/model` 字典。供应商差异经 `AiProviderAdapter.generateOptions(...)` 产出 AgentScope `GenerateOptions`（`additionalBodyParams` 注入 thinking、toolChoice、reasoningEffort）；Kimi/DeepSeek 等兼容供应商走官方 compat formatter，禁用「结构化输出与工具共存」的原生路径。
- **端点用途与协议是运行时契约，不是展示标签**：`usage` 取 `chat`/`embedding`/`rerank`；聊天端点 `protocol` 取 `ANTH`（Anthropic Messages）/`CHAT`（OpenAI Chat Completions）/`RESPONSE`（OpenAI Responses），向量化与重排序端点协议由用途确定为 `EMBEDDING`/`RERANK`。协议决定模型实现（`OpenAIChatModel`/`AnthropicChatModel`/`OpenAIResponsesChatModel`）与默认路径，**禁止**只改标签不换实现，也禁止把不支持某协议的端点静默按聊天协议调用。
- **向量化与重排序是独立端点**：各自持有基址、路径、凭据与模型清单，不从聊天端点推导；同一供应商编码可跨用途复用（用途参与身份），同一用途下编码**必须**唯一。Wiki 知识库与插件语义记忆按 (usage, providerCode, modelCode) 解析，旧配置挂在聊天端点上的 `embeddingModels`/`rerankModels` 在解析时合成为同编码独立端点，前端保存时迁移为显式端点且不再写回旧字段。
- 端点路径默认值：`/chat/completions`、`/responses`、`/v1/messages`、`/embeddings`、`/rerank`；粘贴完整端点地址时 `endpointBaseUrl()` 剥离路径再交给 SDK，SDK 自行拼接版本段。
- 管理端「获取模型」走 `POST /api/platform/ai/endpoints/discover-models`（权限复用 `platform:capability:test`，要求 AI 能力已启用）：按协议请求 OpenAI 兼容 `/models` 或 Anthropic `/v1/models`，重排序等无列表接口的服务返回 `listed=false` 并提示手工登记；凭据只随本次请求使用，不落库、不写日志、不回显。出站目标只做输入合法性校验（http/https、主机存在、不携带用户凭据）——同一权限层已能通过聊天端点向相同地址发起生成，发现接口**不得**比实际调用更严格；需要限制内网时由部署侧 `yudream.security.outbound.allow-private-network` 统一收紧。
- AI 底层由 AgentScope Java（`io.agentscope:agentscope-harness`）承载，**禁止**手搓底层：模型实例构建/连接复用（`AgentScopeModels`）、流式拆包、工具调用循环、结构化输出与降级、重试一律交给框架。依赖版本从 `pom.xml` 读取并核实真实 API，不以网页描述代替已发布制品。
- Agent 路径（ReActAgent/HarnessAgent）的 system prompt **必须**经 `sysPrompt`/`onSystemPrompt` 注入，输入消息列表**禁止**携带 SYSTEM 消息；模型可见工具名一律 `ToolSchemas.safeName`。
- 每次调用定制走 `RuntimeContext` + 中间件：`PerCallModelMiddleware`（逐请求 provider/model）、`PerCallSystemPromptMiddleware`、`PerCallToolFilterMiddleware`（按权限快照 schema 级过滤工具）。
- 工具权限四层：schema 过滤（`PerCallToolFilterMiddleware`）→ 规则/模式（`PermissionContextState`）→ 执行层 fail-closed 复核（`AgentToolAdapter.callAsync`，快照来自 `RuntimeContext`/`AgentToolPermissions`）→ 沙箱守卫。插件 DESTRUCTIVE/需确认工具**必须**走权限系统 ASK（HITL）。安全仿真的工具闸门覆盖 system/plugin/runtime/Python 与模型 native callback 统一入口；`AiAgentTool` 未声明风险默认 WRITE，只有显式 READ 且过原权限/触发/allowedToolNames 校验的工具可在沙盒运行。
- 跨异步边界**必须**先捕获调用方权限快照：Agent SSE 调试、CMS AG-UI 兼容流、插件消息执行在过 CompletableFuture/Reactor 线程前把权限码存到边界命令并传到 `AgentRunCmd`；**禁止**在异步线程读 Sa-Token/API-key ThreadLocal。
- 兼容网关返回非标准 content type（如 `application/octet-stream`）的 JSON 时，在传输层处理，不做响应后处理。
- Agent 运行/调试创建 `AiGenerationRequest` 时必须传入已启用 AI 能力的 providers 配置；仅传 providerCode/modelCode 且 `config=Map.of()` 会让网关回落 Default AI 并误报 API Key 未配置。
- 框架工具调用完成后**必须**保留模型最终自然语言文本（最终 Msg 正文优先，空则回落首条工具消息）；`AiGenerationResult` 不能因 toolResults 非空清空 summary/content。

### SSE 与流式

- SSE **必须**端到端真流式（controller、应用回调、infra 模型调用都传 `TextBlockDeltaEvent`/`ThinkingBlockDeltaEvent` 增量），模型读超时匹配 SSE 超时；缺真流式实现**必须**明确失败，禁止阻塞后整段 `onDelta` 回放。
- 事件用可扩展信封（`ai.message`/`ai.tool`/`ai.progress`/`ai.result`/`ai.error`），data 含 `event/action/module/traceId/timestamp/payload`；正文在 `payload.content`，工具结果在 `payload.tool`，最终结果在 `payload.result`，错误在 `payload.message`；禁止把裸模型 JSON 当聊天内容。
- 接受请求后立即发 `ai.progress`，昂贵工具执行前也发进度（模型规划工具参数期间没有自然语言增量，前端不能显闲）；工具回调执行即发结果，不等最终总结。
- 发送结构化 `ai.error` 后正常 complete，**不得**再 `completeWithError`（否则 Spring MVC 在 `text/event-stream` 上走 JSON 异常处理）。

### Agent 应用与工作流

- Agent 应用是独立平台能力（显式 provider、双闸门、依赖 AI/Integration）；菜单与端点绑定 agent 而非仅 ai。执行流落库后由 `AgentTraceAppService` 统一查询/统计/导出/删除，日志菜单 `system:agent-trace`（`/api/platform/agent-traces`），不依赖开发模式。
- Agent 调试前端经 fetch ReadableStream 消费 POST SSE，按 nodeId 映射节点状态；只打日志或等阻塞返回实现不了实时画布反馈。
- 大模型节点持久化 `providerCode/modelCode` 与输入能力：llm/understand 只能引用已配置 chat 模型，Vision 模型才允许图片输入，禁止静默回落未配置的 Default AI；模型 `kind` 与端点用途共同决定归类——聊天生成只解析 chat 端点且 `kind=chat` 的模型；内置 Agent 启动时修复历史非法模型引用（embedding/rerank/已删除），保留合法 chat 选择。向量/重排清单由各自独立端点的用途推导，不在聊天端点内二次推导。
- 原生工具调用由具体聊天模型节点声明 `toolCodes`/`toolMode`，每次模型调用前解析为受作用域限制的 `AiAgentTool` 回调；应用级 toolCodes 仅是后端推导的授权并集；独立 tool 节点只作历史兼容迁移路径。
- Python 自定义工具契约是 `def run(params: dict) -> dict`：stdin/stdout 是内部 IPC，不进工具编排 UI 与 payload；持久化输出 JSON 示例并拒绝非 dict 结果。
- 工作流变量是动态 map：模板渲染把缺失字段当空值，可选模型字段用安全导航，可选意图元数据不能中止整个运行。
- 文档文本提取走领域 `DocumentTextExtractor` 网关，infra 用 Apache Tika 内存解析 Data URL/Base64，解码前后校验输入格式/MIME/最大字节，禁止把 Tika/临时文件逻辑放进应用服务。
- AG-UI 结构化卡片只在 AG-UI 流以 `ACTIVITY_SNAPSHOT`（activityType=agui-card）发出；普通 Agent/非流式响应保留原始结构化输出，不渲染 AG-UI 卡。
- 插件 Agent 应用以静态 plugin.yml agents + classpath 工作流资源声明，只投影进运行时注册表：启用注册、禁用/卸载立即销毁；**禁止**播种进宿主数据库，插件不可用时禁止回落历史持久副本。导入插件工作流创建标 `sourcePluginCode` 的本地草稿 overlay，仅源插件加载时优先；插件禁用/卸载后隐藏且不可运行，不播种/改动插件拥有的库记录。保留码遇无 sourcePluginCode 的历史持久记录时，导入复用该记录、标记来源插件、以运行时工作流替换为 DRAFT overlay 保存；普通本地撞码仍拒绝。
- 技能框架级统一管理：`platformAgentSkill`（`MongoAgentSkillRepository implements AgentSkillRepository`）是所有 Harness Agent 的技能源，启用技能随每轮推理进 `<available_skills>`；管理端 `/api/platform/agent-skills`（view/create/edit/delete 按动作词表拆分）。

### CMS AI

- CMS 生成网关请求显式标记结构化输出；普通插件/聊天请求回自然语言，绝不进 CMS 画布 JSON 校验。
- 画布结构性变更（add-html/set-html/replace-page）**必须**带 CSS；Agent 必须调 `cms.canvas.validate` 传最终 HTML/CSS/JS，valid=true 才可收尾；校验工具只读，不得当作画布变更应用。固定 `data-yb-chrome` Header/Footer 与 `site-builder-home` 包装器排除出 AI CSS 覆盖检查（由 `cms.chrome.style` validate 负责），首页主体与 add-html 的 class 覆盖保持严格。
- 画布预览 JS 必须可重复初始化并可释放：`addEventListener`/`setInterval`/`requestAnimationFrame`/Three.js `setAnimationLoop` 等长生命周期资源经 `window.__YU_CMS_REGISTER_CLEANUP__` 注册清理；预览更新防抖，普通 HTML/CSS 更新不重跑整段页面 JS。
- 工具状态、payload、cursor、业务上下文是结构化请求/运行时元数据：经 `message.tools`/`message.activities` 渲染，**禁止**序列化 `[工具上下文]` 之类的内部标记进 assistant 历史（模型会把它回声进用户可见回答）。
- 画布 CSS 编辑与 AI CSS 工具保留选择器与 at-rule 语义：整规则替换，按规范化选择器在同一 at-rule 路径内合并、后声明覆盖；禁止裸拼 CSS 串或子串判重；Grid/Flex 子项可能含长内容要 `min-width: 0`，弹性轨道优先 `minmax(0, 1fr)`。
- **画布与站点工作区的 AI 统一走 AI 助手网关**：不再有 CMS 专用生成/画布流式端点与 AG-UI WebSocket。会话是真实的聊天会话（`CONSOLE` 范围 + `agentCode`：`cms-canvas` 画布、`cms-builder` 站点工作区），启动只能用 `POST /api/platform/chat/sessions/{id}/runs`，订阅用 `/api/platform/ai/runs/{id}/events`，回复用 `/frontend-tools/{toolCallId}`。画布客户端工具（`cms.canvas.*` / `cms.ask.user`）由 `ChatRunAppService` 按 agentCode 绑定 `CmsCanvasClientBridge.external` 为本次运行的 scoped 工具，前端面板在挂载期注册执行器（`registerFrontendToolHandler`），卸载即注销；没有执行器的请求必须显式拒绝，不让模型空等。画布提示词由 `CmsCanvasAgentPrompt` 单一来源供应（网关与历史检查点共用）。
- 画布上下文（站点/页面/模板/选中元素/结构纲要/变量与深度思考开关）由前端合成到本轮消息文本；样图作为运行附件（image data URL）传入。
- 对话历史不再在浏览器端存：会话与消息由 `platformChatSession`/`platformChatMessage` 持久化，加载历史会话时从服务端拉取，画布面板只展示 `agentCode=cms-canvas` 的会话。
- 编辑器与公开站共用 Header/Footer chrome CSS 契约、排版基线与固定设备宽；公开基础样式不得压过持久化动态 CMS CSS；已登录账号控件继承 chrome 样式。
- 构建器默认在后端/应用边界开深思考，发送器 UI 不暴露独立开关（产品明确要求除外）。
- 通用工具（`web.fetch` 等）须声明为 `AiAgentTool`、带权限元数据，用于变更前的分析；只有 `cms.canvas.patch` 类变更工具直接改前端状态。

### 聊天与 Wiki

- 聊天接收 Wiki slug/all 时，**必须**在任何空间枚举/检索/目录读取前校验服务端捕获的 Wiki 查看权限快照；聊天目录兜底只物化已发布版本，禁止复用可能返回 markdownDraft 的管理目录接口。
- 插件富回复图片选择：答案模型用 `[[wiki-image:1]]` 索引占位选图，插件只解析被选占位为附件，消息适配器按附件标题交错文图段。
