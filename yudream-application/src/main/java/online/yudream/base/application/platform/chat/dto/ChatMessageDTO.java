package online.yudream.base.application.platform.chat.dto;

import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageRole;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageStatus;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatAttachment;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageDTO(
        String id,
        String sessionId,
        String userId,
        ChatMessageRole role,
        String content,
        String reasoning,
        List<ChatCitation> citations,
        List<ChatToolCall> tools,
        List<ChatActivity> activities,
        List<ChatAttachment> attachments,
        AiUsage usage,
        ChatMessageStatus status,
        String errorMessage,
        LocalDateTime createTime
) {
}
