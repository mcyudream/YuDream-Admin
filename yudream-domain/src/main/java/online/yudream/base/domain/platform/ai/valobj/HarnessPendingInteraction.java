package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

/** 框架已持久的待办：kind 为 confirmation 或 external_execution，requestId 是真实工具调用标识。 */
public record HarnessPendingInteraction(String requestId, String kind, String toolName, Map<String, Object> arguments) {
    public HarnessPendingInteraction {
        arguments = arguments == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(arguments));
    }
}
