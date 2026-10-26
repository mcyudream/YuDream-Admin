package online.yudream.base.domain.platform.mcpserver.valobj;

/**
 * 工具执行结果：error=true 表示工具执行失败（MCP isError=true），
 * 协议级错误（工具不存在/未授权）不走此类型，由应用层抛业务异常。
 */
public record McpToolOutcome(boolean error, String text) {
}
