package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentModelRes {
    private String providerCode;
    private String providerName;
    private String modelCode;
    private String modelName;
    private String kind;
    private boolean vision;
    private boolean configured;
    private boolean defaultModel;
}
