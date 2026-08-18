package online.yudream.base.domain.platform.chat.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatSession extends BaseDomain {

    private Long userId;
    private String title;
    private ChatScopeType scopeType;
    private String agentCode;
    private String spaceSlug;
    private String providerCode;
    private String modelCode;
    private int messageCount;
    private boolean pinned;
    private LocalDateTime lastMessageAt;

    public static ChatSession create(Long userId, String title, ChatScopeType scopeType) {
        if (userId == null) {
            throw new BizException("当前用户不能为空");
        }
        if (scopeType == null) {
            scopeType = ChatScopeType.GENERAL;
        }
        String safeTitle = title == null || title.isBlank() ? "新的对话" : title.trim();
        return ChatSession.builder()
                .userId(userId)
                .title(safeTitle)
                .scopeType(scopeType)
                .messageCount(0)
                .pinned(false)
                .lastMessageAt(LocalDateTime.now())
                .build();
    }

    public void rename(String title) {
        if (title == null || title.isBlank()) {
            throw new BizException("会话标题不能为空");
        }
        this.title = title.trim();
    }

    public void pin(boolean pinned) {
        this.pinned = pinned;
    }

    public void recordMessage() {
        this.messageCount++;
        this.lastMessageAt = LocalDateTime.now();
    }

    public void belongsTo(Long userId) {
        if (userId == null || !userId.equals(this.userId)) {
            throw new BizException("无权访问该会话");
        }
    }
}
