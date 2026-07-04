package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

public record AiAgentToolCall(
        String toolName,
        Map<String, Object> arguments
) {
}
