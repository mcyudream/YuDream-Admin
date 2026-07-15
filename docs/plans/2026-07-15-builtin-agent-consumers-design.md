# 内置 Agent 与业务消费方迁移设计

## 目标

将 CMS Builder 和 AI 群聊机器人从直接选择、调用模型迁移为选择并运行 Agent 应用。平台初始化 `builtin-cms-builder` 与 `builtin-group-chatbot` 两个内置 Agent，业务配置只保存稳定的 Agent code。

## 边界

- Agent 应用仍由 `platform/agent` 上下文持久化、发布和执行。
- CMS 位于 core，可通过应用层 Agent 门面执行，并复用现有 AG-UI 文本、工具和进度事件。
- AI 群聊机器人位于独立插件仓库，只能通过 `yudream-plugin-spi` 的稳定 Agent DTO/端口查询和执行，不能依赖 core 领域或应用类。
- Agent 自己拥有模型、提示词和工具编排；CMS 与插件 UI 不再展示 Provider/模型选择。
- 业务配置使用 Agent code，不持久化 Snowflake ID。目录只暴露已发布应用；内置 Agent 未配置模型时保持草稿，不允许业务选择。

## 内置应用

- `builtin-cms-builder`：开始 -> CMS 大模型 -> 结束，预置 CMS 画布、校验、区块、站点样式和公开网页读取工具。
- `builtin-group-chatbot`：开始 -> 群聊大模型 -> 结束，使用群聊上下文输入；插件仍负责触发概率、消息历史、长期记忆和消息发送。

初始化器按 code 幂等创建。首次存在可用默认聊天模型时填充 Provider/模型并发布；已被管理员修改的应用不覆盖。没有可用模型时创建草稿，等待管理员进入 Agent 编排器配置并发布。

## 兼容与安全

- CMS 请求临时保留旧模型字段用于接口兼容，但新前端只发送 `agentCode`，应用服务必须优先且最终要求 Agent。
- 群聊策略读取旧 `providerCode/modelCode` 时映射到默认内置群聊 Agent；新保存只写 `agentCode`。
- Agent 运行继续执行应用状态、工具权限、能力开关和工作流发布校验。
