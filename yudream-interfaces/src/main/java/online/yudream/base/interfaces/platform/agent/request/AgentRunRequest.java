package online.yudream.base.interfaces.platform.agent.request;

import lombok.Data;

@Data
public class AgentRunRequest {
    private String input;
    private String providerCode;
    private String modelCode;
}
