package online.yudream.base.application.platform.agent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentToolCandidateDTO {
    private String code;
    private String name;
    private String description;
    private String permissionCode;
    private String source;
}
