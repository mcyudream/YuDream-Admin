package online.yudream.base.application.platform.agent.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.util.List;

@Data
@Builder
public class AgentRunDTO {
    private String content;
    private List<AiAgentToolResult> toolResults;
}
