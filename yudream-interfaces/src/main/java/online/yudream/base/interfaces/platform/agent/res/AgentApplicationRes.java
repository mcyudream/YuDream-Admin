package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AgentApplicationRes {
    private String id;
    private String name;
    private String code;
    private String description;
    private String icon;
    private String systemPrompt;
    private String workflowJson;
    private List<String> toolCodes;
    private AgentApplicationStatus status;
    private String sourcePluginCode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
