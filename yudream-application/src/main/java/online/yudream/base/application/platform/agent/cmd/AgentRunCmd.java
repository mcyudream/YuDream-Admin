package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;

import java.util.List;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;

@Data
public class AgentRunCmd {
    private Long applicationId;
    private String input;
    private String providerCode;
    private String modelCode;
    private String imageDataUrl;
    private List<AgentAttachmentCmd> attachments = List.of();
    private String runtimeSystemPrompt;
    private List<AiChatMessage> history = List.of();
}
