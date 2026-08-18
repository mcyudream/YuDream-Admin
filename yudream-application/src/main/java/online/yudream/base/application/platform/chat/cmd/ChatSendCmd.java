package online.yudream.base.application.platform.chat.cmd;

import lombok.Data;
import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.application.platform.chat.dto.ChatContextRefDTO;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

import java.util.List;

@Data
public class ChatSendCmd {
    private Long sessionId;
    private ChatScopeType scopeType;
    private String agentCode;
    private String spaceSlug;
    private String providerCode;
    private String modelCode;
    private List<ChatContextRefDTO> contextRefs = List.of();
    private String question;
    private List<ChatAttachmentDTO> attachments = List.of();
    private List<AiChatMessage> history = List.of();
    private List<String> permissionCodes = List.of();
}
