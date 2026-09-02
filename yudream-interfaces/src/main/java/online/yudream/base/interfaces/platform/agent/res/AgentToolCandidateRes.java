package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentToolCandidateRes {
    private String code;
    private String name;
    private String description;
    private String permissionCode;
    private String source;
}
