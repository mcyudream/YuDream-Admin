package online.yudream.base.domain.platform.mcp.valobj;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 服务端 tools/list 返回的工具定义快照（缓存于服务记录，供列表与本机桥接使用）。
 * risk 由 annotations.readOnlyHint 推导：只有显式声明只读的工具才是 READ。
 */
public record McpToolDefinition(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        String risk,
        boolean requiresConfirmation
) {
    public McpToolDefinition {
        name = name == null ? "" : name.trim();
        title = title == null || title.isBlank() ? name : title;
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? Map.of() : new LinkedHashMap<>(inputSchema);
        risk = "READ".equalsIgnoreCase(risk) ? "READ" : "WRITE";
    }
}
