package online.yudream.base.domain.platform.chat.valobj;

import java.util.Map;

public record ChatToolCall(
        String toolCallId,
        String toolName,
        String status,
        String message,
        Map<String, Object> payload
) {
}
