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
    /** 工作区模式：ask（默认）/ plan（只读规划）/ code（可写工作区） */
    private String mode;
    /** 按模式附加给模型的工作说明（服务端生成，不来自请求） */
    private String modePrompt = "";
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
