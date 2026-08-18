package online.yudream.base.application.platform.chat.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

@Data
public class ChatSessionSaveCmd {
    private Long id;
    private String title;
    private ChatScopeType scopeType;
    private String agentCode;
    private String spaceSlug;
    private String providerCode;
    private String modelCode;
    private Boolean pinned;
}
