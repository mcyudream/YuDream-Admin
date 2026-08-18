package online.yudream.base.application.platform.chat.dto;

import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;

import java.util.List;

public record ChatSendResultDTO(
        String sessionId,
        String userMessageId,
        String assistantMessageId,
        String content,
        String reasoning,
        List<ChatCitation> citations,
        List<ChatToolCall> tools,
        List<ChatActivity> activities,
        AiUsage usage,
        long usedTokens,
        long limitTokens,
        long remainingTokens
) {
}
