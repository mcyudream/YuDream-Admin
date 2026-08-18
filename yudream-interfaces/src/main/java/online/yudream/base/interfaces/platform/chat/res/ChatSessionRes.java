package online.yudream.base.interfaces.platform.chat.res;

import lombok.Builder;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

import java.time.LocalDateTime;

@Builder
public record ChatSessionRes(
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
