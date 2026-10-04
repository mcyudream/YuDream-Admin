package online.yudream.base.domain.platform.ai.valobj;

import java.util.List;
import java.util.Map;

/**
 * 一次 Harness Agent 运行的入参。对话历史不在其中——上下文由框架按
 * (userId, sessionId) 从状态存储恢复；history 仅用于无状态一次性运行显式注入。
 * allowedToolNames 非空时为本次调用的工具白名单（平台安全名），空表示按权限快照全部可见。
 */
public record HarnessRunRequest(
        Long userId,
        String sessionId,
        String agentCode,
        String systemPrompt,
        String userPrompt,
        List<String> imageDataUrls,
        String providerCode,
        String modelCode,
        Map<String, String> config,
        boolean toolCallingEnabled,
        List<String> permissionCodes,
        List<AiChatMessage> history,
        List<String> allowedToolNames
) {
    public HarnessRunRequest {
        imageDataUrls = imageDataUrls == null ? List.of() : List.copyOf(imageDataUrls);
        permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
        history = history == null ? List.of() : List.copyOf(history);
        allowedToolNames = allowedToolNames == null ? List.of() : List.copyOf(allowedToolNames);
    }

    /**
     * 兼容既有调用方：不带工具白名单（按权限快照全部可见）。
     */
    public HarnessRunRequest(
            Long userId,
            String sessionId,
            String agentCode,
            String systemPrompt,
            String userPrompt,
            List<String> imageDataUrls,
            String providerCode,
            String modelCode,
            Map<String, String> config,
            boolean toolCallingEnabled,
            List<String> permissionCodes,
            List<AiChatMessage> history
    ) {
        this(userId, sessionId, agentCode, systemPrompt, userPrompt, imageDataUrls, providerCode, modelCode,
                config, toolCallingEnabled, permissionCodes, history, List.of());
    }

    public static HarnessRunRequest stateless(Long userId, String agentCode, String systemPrompt, String userPrompt) {
        return new HarnessRunRequest(userId, null, agentCode, systemPrompt, userPrompt,
                List.of(), null, null, Map.of(), false, List.of(), List.of());
    }
}
