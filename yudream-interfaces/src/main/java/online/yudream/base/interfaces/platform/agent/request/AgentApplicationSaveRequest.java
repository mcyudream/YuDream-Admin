package online.yudream.base.interfaces.platform.agent.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

import java.util.List;

@Data
public class AgentApplicationSaveRequest {
    @NotBlank private String name;
    @NotBlank private String code;
    private String description;
    private String icon;
    private String systemPrompt;
    @NotBlank private String workflowJson;
    private List<String> toolCodes;
    private AgentApplicationStatus status;
}
