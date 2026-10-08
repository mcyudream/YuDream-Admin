package online.yudream.base.application.platform.chat.support;

import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;

import java.util.List;
import java.util.function.Consumer;

/**
 * 聊天调度上下文。sessionId 为平台会话 ID 字符串（有状态 Harness 运行的寻址键）；
 * 一次性运行（streamOnce）为 null，此时 history 由调用方显式提供。
 */
public record ChatDispatchContext(
        Long userId,
        String sessionId,
        String question,
        String providerCode,
        String modelCode,
        String agentCode,
        String spaceSlug,
        List<ChatAttachmentDTO> attachments,
        List<online.yudream.base.domain.platform.ai.valobj.AiChatMessage> history,
        List<String> permissionCodes,
        List<online.yudream.base.domain.platform.ai.valobj.ToolConfirmationDecision> confirmDecisions,
        Consumer<String> onDelta,
        Consumer<String> onReasoningDelta,
        Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> onTool,
        Consumer<ChatActivity> onActivity
) {
    public ChatDispatchContext(
            Long userId,
            String sessionId,
            String question,
            String providerCode,
            String modelCode,
            String agentCode,
            String spaceSlug,
            List<ChatAttachmentDTO> attachments,
            List<online.yudream.base.domain.platform.ai.valobj.AiChatMessage> history,
            List<String> permissionCodes,
            Consumer<String> onDelta,
            Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> onTool,
            Consumer<ChatActivity> onActivity
    ) {
        this(userId, sessionId, question, providerCode, modelCode, agentCode, spaceSlug, attachments, history,
                permissionCodes, List.of(), onDelta, ignored -> { }, onTool, onActivity);
    }
}
