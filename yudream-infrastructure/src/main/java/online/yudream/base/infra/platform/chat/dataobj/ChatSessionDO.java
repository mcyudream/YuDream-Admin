package online.yudream.base.infra.platform.chat.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformChatSession")
public class ChatSessionDO extends BaseDO {
    @Indexed
    private Long userId;
    private String title;
    private ChatScopeType scopeType;
    private String agentCode;
    private String spaceSlug;
    private String providerCode;
    private String modelCode;
    private int messageCount;
    private boolean pinned;
    @Indexed
    private LocalDateTime lastMessageAt;
}
