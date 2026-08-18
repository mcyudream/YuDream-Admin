package online.yudream.base.application.platform.agent.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;

import java.util.List;

@Data
@Builder
public class AgentRunDTO {
    private String content;
    private String reasoning;
    private List<AiAgentToolResult> toolResults;
    private AiUsage usage;
}
