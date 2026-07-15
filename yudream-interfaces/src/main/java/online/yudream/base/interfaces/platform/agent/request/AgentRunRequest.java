package online.yudream.base.interfaces.platform.agent.request;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class AgentRunRequest {
    @Size(max = 20_000)
    private String input;
    private String providerCode;
    private String modelCode;
    @Size(max = 7_000_000)
    private String imageDataUrl;
    @Valid
    @Size(max = 5)
    private List<AgentAttachmentRequest> attachments = List.of();
}
