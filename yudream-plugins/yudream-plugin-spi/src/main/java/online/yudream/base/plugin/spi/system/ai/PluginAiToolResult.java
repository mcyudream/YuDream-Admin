package online.yudream.base.plugin.spi.system.ai;

import java.util.Map;

public record PluginAiToolResult(String action, String message, Map<String, Object> payload) {
    public PluginAiToolResult {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
