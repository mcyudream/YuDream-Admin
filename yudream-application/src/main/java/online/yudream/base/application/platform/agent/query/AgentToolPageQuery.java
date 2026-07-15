package online.yudream.base.application.platform.agent.query;

import lombok.Data;

@Data
public class AgentToolPageQuery {
    private String keyword;
    private int page = 1;
    private int size = 10;
}
