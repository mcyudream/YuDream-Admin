package online.yudream.base.domain.platform.mcp.service;

import java.util.Map;

/**
 * MCP 客户端网关：按 Streamable HTTP 协议连接远端 MCP 服务端。
 * 每次操作都是完整的一次「initialize → notifications/initialized → 业务请求」序列，
 * 不持有长连接；会话标识只在单次序列内传递。
 */
public interface McpClientGateway {

    /** 连接并拉取工具清单；失败时以 error 返回而不抛异常，便于管理端展示。 */
    McpProbeResult probe(McpEndpoint endpoint);

    /** 调用远端工具。 */
    McpToolCallResult callTool(McpEndpoint endpoint, String toolName, Map<String, Object> arguments);

    /** 连接参数（与聚合解耦，便于测试替换）。 */
    record McpEndpoint(
            String code,
            String endpointUrl,
            Map<String, String> headers,
            int timeoutMillis,
            boolean allowPrivateNetwork
    ) {
        public McpEndpoint {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            timeoutMillis = timeoutMillis <= 0 ? 15000 : timeoutMillis;
        }
    }

    record McpProbeResult(
            boolean success,
            String protocolVersion,
            String serverName,
            String serverVersion,
            java.util.List<online.yudream.base.domain.platform.mcp.valobj.McpToolDefinition> tools,
            String message
    ) {
        public McpProbeResult {
            tools = tools == null ? java.util.List.of() : java.util.List.copyOf(tools);
        }
    }

    record McpToolCallResult(boolean success, boolean isError, String content, Map<String, Object> structured, String error) {
    }
}
