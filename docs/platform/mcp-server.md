# 平台 MCP 服务器（mcp-server）

宿主作为 **MCP（Model Context Protocol）服务端**，把插件注册的 AI 工具（SPI `context.registerAiTool`）按调用者权限暴露为 MCP 工具，供外部 AI 客户端（ZCode、Claude、Cursor 等）直连管理平台资源。

## 端点

| 端点 | 说明 |
|---|---|
| `POST /api/mcp` | **完整 MCP**：聚合全部模块（插件）的工具 |
| `POST /api/mcp/{module}` | **模块 MCP**：只含指定模块，模块名 = 插件 code（如 `mcpanel`），兼容 `plugin:<code>` 写法 |
| `GET` / `DELETE` | 405：本服务器为 Streamable HTTP 纯 JSON 响应，无 server→client 流 |

协议：JSON-RPC 2.0，支持 `initialize`（协议版本 2024-11-05 / 2025-03-26 / 2025-06-18，回显已支持的客户端版本，未知版本回落 2025-06-18）、`tools/list`、`tools/call`、`ping`；通过校验的通知回 202 且无响应体；`resources/list`、`prompts/list` 返回空清单。服务器不分配会话 ID。

请求格式按 [MCP 2025-06-18 基础协议](https://modelcontextprotocol.io/specification/2025-06-18/basic)校验：`jsonrpc` 必须为 `"2.0"`，`method` 为非空字符串，请求 `id` 为字符串或整数（不能为 `null`）；通知省略 `id`。每次 POST 只允许一个 JSON 对象，不接受批量或拼接的多条消息。

## 认证与权限门控

- 认证三选一（与其他平台端点一致）：
  - **API Key（推荐给 MCP 客户端）**：`Authorization: Bearer yda_...`（用户中心「API 密钥」自助签发，明文只显示一次）；
  - OAuth 访问令牌；登录会话（仅浏览器场景）。
- **可见性 = 可调用性**：`tools/list` 只返回调用者有权限的工具（工具的 `permissionCode` ⊆ 主体权限，`*` 全量）；`tools/call` 逐工具复检同一条件。无权限的模块在完整端点里自然不可见。
- 工具目录携带 `annotations.readOnlyHint`，供客户端区分只读与写操作；按暴露策略，所有工具的 `destructiveHint` 为 `false`。这些提示不替代服务端权限校验。
- **暴露策略（门控）**：只有满足以下全部条件的工具才会经 MCP 暴露——
  1. 工具 descriptor 的 `allowedTriggers` 声明了 `"MCP"`（工具显式报名 MCP 通道）；
  2. `risk` 非 `DESTRUCTIVE`；
  3. `requiresConfirmation = false`。
- 写操作（`risk=WRITE`）按调用者权限码放行（API Key 凭据的权限快照即显式授权），并在执行上下文里逐工具点名授权。
- 完整端点下撞名的工具只保留字典序首个模块的，撞名工具仍在各自模块端点可用。

## 能力开关（双闸门）

1. 项目闸门：内部配置 `yudream.platform.capabilities.mcp-server.enabled`（默认 true）——关闭时端点整体不存在；不提供部署侧环境变量门控。
2. 应用闸门：管理后台「平台能力」页启停 `MCP 服务器`（code=`mcp-server`）——握手、探活、目录、调用与通知均检查启用状态。关闭时请求返回 JSON-RPC 错误「能力未启用」；通知返回 HTTP 503，不会误报接受成功。

## 客户端接入示例

以 mcpanel 模块为例（ZCode / Claude Code 等 streamable HTTP 客户端）：

```json
{
  "mcpServers": {
    "yudream-mcpanel": {
      "type": "http",
      "url": "https://<站点域名>/api/mcp/mcpanel",
      "headers": { "Authorization": "Bearer yda_<你的 API Key>" }
    }
  }
}
```

完整端点把 `url` 换成 `https://<站点域名>/api/mcp`。

## 调用与错误反馈

`tools/call` 的 `params.name` 必须为非空字符串；`params.arguments` 必须为 JSON 对象。无参数时省略 `arguments` 或传 `{}`，不能传 `null`、数组或字符串。参数格式错误时直接拒绝，不执行工具。例如：

```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "method": "tools/call",
  "params": {
    "name": "<从 tools/list 获取的工具名>",
    "arguments": {}
  }
}
```

| 情况 | HTTP / MCP 反馈 | 处理方式 |
|---|---|---|
| JSON 解析失败或拼接多条消息 | 400 / `-32700` | 每次 POST 发送一条完整 JSON 消息 |
| JSON-RPC 版本、method 或 id 格式不正确 | 400 / `-32600` | 按上述请求格式修正；通知应省略 id |
| params、工具名或 arguments 类型错误 | 200 / `-32602`，含具体字段提示 | 修正参数后重试；非法通知返回 400，错误体不带 id |
| 未支持的方法 | 200 / `-32601` | 使用本页列出的已支持方法 |
| 能力关闭、工具不存在或无调用权限 | 200 / `-32000` | 检查能力开关、模块名与凭据授权 |
| 工具执行失败 | 200 / `result.isError=true` | 查看 `content` 中的工具错误说明 |
| 握手成功但目录为空 | `instructions` 提示没有可见工具 | 检查模块、插件 MCP 通道声明与凭据权限 |

请求 id 会按原类型回传。资源的 Long/Snowflake ID 应在 `arguments` 中使用字符串。

## 工具接入方（插件）约定

- 经 SPI `context.registerAiTool(...)` 注册 `PluginAiTool`；
- `descriptor.name` 自带插件前缀（如 `mcpanel_*`）防跨模块撞名；
- `allowedTriggers` 必须含 `"MCP"` 才会经 MCP 暴露（建议 `Set.of("MENTION", "MCP")` 同时兼容宿主聊天通道）；
- `inputSchema` 为标准 JSON Schema；
- 执行上下文由网关注入（`trigger="MCP"`，userId/permissions 为调用者身份），工具内部据此做 owner 推导与数据边界。
