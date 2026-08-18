package online.yudream.base.application.platform.chat.support;

import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;

import java.util.List;
import java.util.function.Consumer;

public record ChatDispatchContext(
        String question,
        String providerCode,
        String modelCode,
        String agentCode,
        String spaceSlug,
        List<ChatAttachmentDTO> attachments,
        List<online.yudream.base.domain.platform.ai.valobj.AiChatMessage> history,
        List<String> permissionCodes,
        Consumer<String> onDelta,
        Consumer<String> onReasoningDelta,
        Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> onTool,
        Consumer<ChatActivity> onActivity
) {
    public ChatDispatchContext(
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
        this(question, providerCode, modelCode, agentCode, spaceSlug, attachments, history, permissionCodes,
                onDelta, ignored -> { }, onTool, onActivity);
    }
}
