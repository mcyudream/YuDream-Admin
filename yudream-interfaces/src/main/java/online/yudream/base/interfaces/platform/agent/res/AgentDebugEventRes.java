package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.interfaces.platform.ai.res.AiToolCallRes;

@Data
@Builder
public class AgentDebugEventRes {
    private String type;
    private String threadId;
    private String runId;
    private Long timestamp;
    private String nodeId;
    private String nodeKind;
    private String nodeTitle;
    private String status;
    private String message;
    private String delta;
    private AiToolCallRes tool;
    private AgentRunRes result;
}
