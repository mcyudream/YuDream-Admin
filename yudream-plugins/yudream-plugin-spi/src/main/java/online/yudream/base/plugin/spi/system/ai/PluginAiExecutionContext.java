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
        List<String> allowedToolNames,
        List<String> grantedWriteToolNames
) {
    public PluginAiExecutionContext {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        allowedToolNames = allowedToolNames == null ? List.of("*") : List.copyOf(allowedToolNames);
        grantedWriteToolNames = grantedWriteToolNames == null ? List.of() : List.copyOf(grantedWriteToolNames);
    }

    public PluginAiExecutionContext(Long userId, String platformUserId, String connectionId, String channelId,
                                    String messageId, String trigger, String traceId, List<String> permissions,
                                    List<String> allowedToolNames) {
        this(userId, platformUserId, connectionId, channelId, messageId, trigger, traceId, permissions,
                allowedToolNames, List.of());
    }

    public PluginAiExecutionContext(Long userId, String platformUserId, String connectionId, String channelId,
                                    String messageId, String trigger, String traceId, List<String> permissions) {
        this(userId, platformUserId, connectionId, channelId, messageId, trigger, traceId, permissions, List.of("*"));
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isBlank() || permissions.contains("*") || permissions.contains(permission);
    }

    public boolean allowsTool(String name) { return allowedToolNames.contains("*") || allowedToolNames.contains(name); }

    /** 插件显式授权本次调用可执行的写工具（逐个点名），宿主仅对白名单内的 WRITE 工具放行。 */
    public boolean grantsWriteTool(String name) {
        return name != null && grantedWriteToolNames.contains(name);
    }
}
