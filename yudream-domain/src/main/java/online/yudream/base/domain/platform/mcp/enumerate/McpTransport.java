package online.yudream.base.domain.platform.mcp.enumerate;

/**
 * MCP 传输类型。v1 只支持规范定义的 Streamable HTTP（POST JSON-RPC，
 * 响应可以是 application/json 或 text/event-stream）；旧版 HTTP+SSE 与 stdio 未实现。
 */
public enum McpTransport {
    STREAMABLE_HTTP
}
