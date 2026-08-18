package online.yudream.base.application.platform.chat.assembler;

import online.yudream.base.application.platform.chat.dto.ChatMessageDTO;
import online.yudream.base.application.platform.chat.dto.ChatQuotaDTO;
import online.yudream.base.application.platform.chat.dto.ChatSessionDTO;
import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;

public final class ChatAssembler {

    private ChatAssembler() {
    }

    public static ChatSessionDTO session(ChatSession session) {
        if (session == null) {
            return null;
        }
        return new ChatSessionDTO(
                id(session.getId()),
                id(session.getUserId()),
                session.getTitle(),
                session.getScopeType(),
                session.getAgentCode(),
                session.getSpaceSlug(),
                session.getProviderCode(),
                session.getModelCode(),
                session.getMessageCount(),
                session.isPinned(),
                session.getLastMessageAt(),
                session.getCreateTime());
    }

    public static ChatMessageDTO message(ChatMessage message) {
        if (message == null) {
            return null;
        }
        return new ChatMessageDTO(
                id(message.getId()),
                id(message.getSessionId()),
                id(message.getUserId()),
                message.getRole(),
                message.getContent(),
                message.getReasoning(),
                message.getCitations(),
                message.getTools(),
                message.getActivities(),
                message.getAttachments(),
                message.getUsage(),
                message.getStatus(),
                message.getErrorMessage(),
                message.getCreateTime());
    }

    public static ChatQuotaDTO quota(UserChatQuota quota) {
        if (quota == null) {
            return null;
        }
        return new ChatQuotaDTO(
                id(quota.getUserId()),
                quota.getUsageDate(),
                quota.getUsedTokens(),
                quota.getLimitTokens(),
                quota.remaining());
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }
}
