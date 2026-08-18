package online.yudream.base.application.platform.chat.dto;

import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

import java.time.LocalDateTime;

public record ChatSessionDTO(
        String id,
        String userId,
        String title,
        ChatScopeType scopeType,
        String agentCode,
        String spaceSlug,
        String providerCode,
        String modelCode,
        int messageCount,
        boolean pinned,
        LocalDateTime lastMessageAt,
        LocalDateTime createTime
) {
}
