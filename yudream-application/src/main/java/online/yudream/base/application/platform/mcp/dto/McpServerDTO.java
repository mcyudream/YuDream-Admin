package online.yudream.base.application.platform.mcp.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** MCP 服务端视图：连接状态、工具快照与对应工具模块开关。 */
public record McpServerDTO(
        Long id,
        String code,
        String name,
        String description,
        String transport,
        String endpointUrl,
        Map<String, String> headers,
        boolean enabled,
        boolean preload,
        int timeoutMillis,
        boolean allowPrivateNetwork,
        String lastStatus,
        String lastError,
        LocalDateTime lastConnectedAt,
        String protocolVersion,
        String serverName,
        String serverVersion,
        List<Tool> tools,
        String moduleCode,
        boolean moduleEnabled,
        LocalDateTime updateTime
) {
    public McpServerDTO {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public record Tool(
            String name,
            String title,
            String description,
            String risk,
            boolean requiresConfirmation,
            String platformToolCode
    ) {
    }
}
