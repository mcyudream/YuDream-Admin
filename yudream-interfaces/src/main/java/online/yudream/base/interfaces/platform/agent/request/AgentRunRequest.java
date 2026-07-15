package online.yudream.base.interfaces.platform.agent.request;

import lombok.Data;

import java.util.List;

@Data
public class AgentRunRequest {
    private String input;
    private String providerCode;
    private String modelCode;
    private String imageDataUrl;
    private List<AgentAttachmentRequest> attachments = List.of();
}
