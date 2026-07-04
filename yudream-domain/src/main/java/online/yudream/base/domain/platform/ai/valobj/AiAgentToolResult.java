package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

public record AiAgentToolResult(
        String toolName,
        String action,
        String permissionCode,
        String message,
        Map<String, Object> payload
) {
}
