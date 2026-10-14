# SPI 2.35.0：持久 AI 运行

本版本配套 SDK 1.12.0、components 1.7.0。版本须完成 Nexus 发布与验签后，独立插件仓才能升级依赖。旧构造函数及一次性服务方法保留；新增字段默认 `COMPLETED`，工具审批会显式暂停。

## 服务契约

`PluginAiService` 新增：

```java
PluginAiRunHandle startChat(PluginAiChatRequest request);
PluginAiRunHandle startAgent(String agentCode, PluginAiChatRequest request);
PluginAiRunSnapshot getRun(String runId, PluginAiExecutionContext executionContext);
```

`PluginAiRunHandle` 包含字符串 `runId` 和 `CompletionStage<PluginAiChatResponse> result`。开始用例立即返回任务凭据，前端响应返回 `runId`，不要等待模型全文再返回 HTTP。阶段结果含 `content/toolResults/runId/status`；`status=WAITING` 表示原生工具审批或外部执行待办，不能将部分文本作为最终业务成功。一次性 `chat/runAgent/chatStream/runAgentStream` 在这种情况下抛 `PluginAiInteractionRequiredException`，其 `runId()` 指向保留的任务。

后台作业必须持久保存任务 ID，恢复后经 `getRun` 查询状态及输出，不能依赖原 JVM 的 future。`PluginAiRunSnapshot.response` 是业务阶段结果；完成后消费它的 `content`，不能将 UI 全程的多节点正文拼接值误当最终结构化业务输出。`cursor`、任务 ID 和会话 ID 都是字符串。系统任务的查询还核对原外部用户、连接、频道和 trace，无法凭 SYSTEM 用户 ID 读取其他后台运行。

`PluginAiChatRequest` 新增可选字符串 `sessionId`。指定稳定会话时，Harness 负责历史、压缩和记忆，每轮只提交新输入；宿主按外部用户、连接、频道进一步隔离原生会话。未指定会话的一次性请求可显式传入历史。工具调用始终通过原生 Harness，确认操作保留原生待办。无工具、无会话的生成使用框架流式生成网关。

## 前端接入

使用宿主注入的 SDK `ai.runs`：

```ts
const detach = await sdk.ai.runs.watch(runId, (snapshot) => {
  // 将真实正文、推理、工具、进度与待确认项映射到 YdAgentWorkbench。
})
// 页面卸载仅执行 detach()；用户主动停止才调用 cancel(runId)。
```

公开方法为 `list/get/subscribe/watch/cancel/resume/respond`。`subscribe` 使用字符串事件游标，AbortSignal 仅断开观察；`watch` 复用宿主根级投影，解绑观察者不终止服务端或宿主观察。刷新页面后读取快照，再从快照游标追增量，不能重复提交用户消息。已记录的前端回复不得重新执行副作用。

正文只来自模型流式增量，推理只来自模型推理字段；工具、活动、内部结构化生成分别展示。禁止对一次性结果逐字回放。组件级状态不能作为持久任务的权威状态。

## 页面动作

`sdk.ai.registerAction({ code, label, permission }, element)` 注册当前路由中真实操作元素，返回清理函数。宿主给动作加插件命名空间，核对登录、路由、当前权限、元素挂载和可见状态。模型只能请求已登记页面的跳转与高亮，不能提交任意选择器、脚本、点击或表单值。插件卸载会清理登记。

页面感知、路由感知、导航与高亮在助手设置中分别控制，默认关闭。启用时仅传经过筛选的路由和操作说明，不发送表单、DOM 文本、查询参数或令牌。

插件传入的本地操作码最多 120 字符；宿主加上 `plugin:{pluginCode}:` 命名空间后的完整标识最多 180 字符。注册、页面上下文校验与高亮回执使用同一完整标识，不能截短后匹配其他操作。路由名称最多 180 字符。

页面感知只向模型提供已授权的操作说明，不隐式启用定位工具。导航和高亮开关分别进入后端工具目录；导航完成后，前端回传新页面的真实操作目录，同一 turn 可以继续高亮。高亮始终复核当前路由、真实元素与当前权限，不能点击或提交。

## 权限与沙箱

模型可见工具范围和实际执行范围一致。插件工具同时受原允许名单、触发类型、真实权限和逐项写授权约束；高风险或要求确认的工具走原生 ASK。调用者填写 `permissions=["*"]` 不会获得宿主权限，权限由本站用户真实角色解析。

恢复保存原触发寻址与工具名单，重新获取当前权限，不持久存储密钥或旧权限。QQ 沙箱只保存原沙箱引用；缺失、过期、关闭或身份变化时拒绝恢复，不能回落真实连接。
