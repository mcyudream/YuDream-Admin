package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;

/** 持久恢复仅保存触发寻址和原工具范围，不保存提供方凭据或旧角色权限。 */
public record AiInvocationContextSnapshot(String platformUserId, String connectionId, String channelId,
                                           String messageId, String trigger, String traceId,
                                           List<String> allowedToolNames, List<String> grantedWriteToolNames, String sandboxId) {
    public AiInvocationContextSnapshot {
        allowedToolNames = allowedToolNames == null ? List.of() : List.copyOf(allowedToolNames);
        grantedWriteToolNames = grantedWriteToolNames == null ? List.of() : List.copyOf(grantedWriteToolNames);
        if (allowedToolNames.contains("*")) throw new IllegalArgumentException("持久调用上下文必须保存点名的工具范围");
        if (grantedWriteToolNames.contains("*")) throw new IllegalArgumentException("写工具许可必须逐个点名");
    }
}
