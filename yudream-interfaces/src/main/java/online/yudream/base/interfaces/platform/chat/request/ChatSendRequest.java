package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

import java.util.List;

@Data
public class ChatSendRequest {
    private Long sessionId;
    private ChatScopeType scopeType;
    private String agentCode;
    private String spaceSlug;
    private String providerCode;
    private String modelCode;
    @Valid
    @Size(max = 10, message = "上下文不能超过 10 个")
    private List<ChatContextRefRequest> contextRefs = List.of();
    @NotBlank(message = "问题不能为空")
    @Size(max = 20_000, message = "问题不能超过 20000 字符")
    private String question;
    @Valid
    @Size(max = 10, message = "附件不能超过 10 个")
    private List<ChatAttachmentRequest> attachments = List.of();
    @Valid
    @Size(max = 20, message = "历史消息不能超过 20 条")
    private List<ChatTurnRequest> history = List.of();
}
