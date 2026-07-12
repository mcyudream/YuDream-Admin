package online.yudream.base.plugin.spi.system.ai;

import java.util.List;

public record PluginAiExecutionContext(
        Long userId,
        String platformUserId,
        String connectionId,
        String channelId,
        String messageId,
        String trigger,
        String traceId,
        List<String> permissions,
        List<String> allowedToolNames
) {
    public PluginAiExecutionContext {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        allowedToolNames = allowedToolNames == null ? List.of("*") : List.copyOf(allowedToolNames);
    }

    public PluginAiExecutionContext(Long userId, String platformUserId, String connectionId, String channelId,
                                    String messageId, String trigger, String traceId, List<String> permissions) {
        this(userId, platformUserId, connectionId, channelId, messageId, trigger, traceId, permissions, List.of("*"));
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isBlank() || permissions.contains("*") || permissions.contains(permission);
    }

    public boolean allowsTool(String name) { return allowedToolNames.contains("*") || allowedToolNames.contains(name); }
}
