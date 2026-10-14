package online.yudream.base.application.platform.chat.cmd;

import lombok.Data;
import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.application.platform.chat.dto.ChatContextRefDTO;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.ToolConfirmationDecision;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

import java.util.List;

@Data
public class ChatSendCmd {
    private boolean contextEnabled;
    private boolean frontendToolsEnabled;
    private online.yudream.base.application.platform.ai.dto.AiFrontendContextDTO frontendContext;
    /** 服务端每轮构造；不从 HTTP 接收工具实现。 */
    private List<online.yudream.base.domain.platform.ai.service.AiAgentTool> scopedTools;
    private String frontendContextPrompt = "";
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
    /** HITL 恢复：对上一轮暂挂工具调用的批准/拒绝决定。 */
    private List<ToolConfirmationDecision> confirmDecisions = List.of();
}
