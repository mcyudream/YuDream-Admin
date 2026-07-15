package online.yudream.base.interfaces.platform.agent.request;

import lombok.Data;

@Data
public class AgentAttachmentRequest {
    private String name;
    private String contentType;
    private Long size;
    private String dataUrl;
}
