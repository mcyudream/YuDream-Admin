package online.yudream.base.application.platform.agent.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

import java.util.List;

@Data
public class AgentApplicationSaveCmd {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String icon;
    private String systemPrompt;
    private String workflowJson;
    private List<String> toolCodes;
    private AgentApplicationStatus status;
}
