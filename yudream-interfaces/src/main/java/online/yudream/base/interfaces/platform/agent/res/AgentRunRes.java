package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.interfaces.platform.ai.res.AiToolCallRes;

import java.util.List;

@Data
@Builder
public class AgentRunRes {
    private String content;
    private List<AiToolCallRes> toolResults;
}
