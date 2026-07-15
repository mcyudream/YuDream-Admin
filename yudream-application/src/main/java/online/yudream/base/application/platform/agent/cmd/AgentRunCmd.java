package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;

import java.util.List;

@Data
public class AgentRunCmd {
    private Long applicationId;
    private String input;
    private String providerCode;
    private String modelCode;
    private String imageDataUrl;
    private List<AgentAttachmentCmd> attachments = List.of();
    private String runtimeSystemPrompt;
    private boolean runtimeToolCallingEnabled;
    private List<String> permissionCodes = List.of();
    private boolean permissionContextExplicit;
    private List<AiChatMessage> history = List.of();
}
