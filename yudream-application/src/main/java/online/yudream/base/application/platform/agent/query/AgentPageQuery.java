package online.yudream.base.application.platform.agent.query;

import lombok.Data;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

@Data
public class AgentPageQuery {
    private String keyword;
    private AgentApplicationStatus status;
    private int page = 1;
    private int size = 10;
}
