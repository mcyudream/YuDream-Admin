package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

@Data
public class ChatSessionSaveRequest {
    @Size(max = 80, message = "会话标题不能超过 80 字符")
    private String title;
    private ChatScopeType scopeType;
    private String agentCode;
    private String spaceSlug;
    private String providerCode;
    private String modelCode;
    private Boolean pinned;
}
