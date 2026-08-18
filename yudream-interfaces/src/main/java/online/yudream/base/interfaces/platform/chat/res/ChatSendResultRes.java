package online.yudream.base.interfaces.platform.chat.res;

import lombok.Builder;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;

import java.util.List;

@Builder
public record ChatSendResultRes(
        String sessionId,
        String userMessageId,
        String assistantMessageId,
        String content,
        List<ChatCitation> citations,
        List<ChatToolCall> tools,
        List<ChatActivity> activities,
        AiUsage usage,
        long usedTokens,
        long limitTokens,
        long remainingTokens
) {
}
