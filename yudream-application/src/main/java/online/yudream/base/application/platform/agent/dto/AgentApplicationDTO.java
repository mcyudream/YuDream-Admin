package online.yudream.base.application.platform.agent.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AgentApplicationDTO {
    private Long id;
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
