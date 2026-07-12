package online.yudream.base.plugin.spi.system.ai;

import java.util.Map;

public record PluginAiToolCall(String toolName, Map<String, Object> arguments) {
    public PluginAiToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
