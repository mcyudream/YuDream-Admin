package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;

@Data
public class AgentRunCmd {
    private Long applicationId;
    private String input;
    private String providerCode;
    private String modelCode;
}
